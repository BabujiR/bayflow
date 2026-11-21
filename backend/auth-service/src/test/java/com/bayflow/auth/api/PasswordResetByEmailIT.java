package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.HourlyLimiter;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.mail.MailDispatcher;
import com.bayflow.auth.mail.MailGateway;
import com.bayflow.auth.mail.OutgoingMail;
import com.bayflow.security.Tenants;

/** "Forgot password?" by e-mail: the link, the reset, and what must not leak or linger. */
@Import(PasswordResetByEmailIT.CapturedMail.class)
class PasswordResetByEmailIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final String NEW_PASSWORD = "Battery-Staple-2";
    private static final String COOKIE = "bayflow_rt";
    private static final Pattern LINK =
            Pattern.compile("https://[a-z0-9-]+\\.example\\.test/reset-password\\?token=([A-Za-z0-9_-]{43})");

    /** Records e-mails instead of sending them; can pretend e-mail is not set up. */
    @TestConfiguration
    static class CapturedMail {
        @Bean
        @Primary
        CapturingGateway capturingGateway() {
            return new CapturingGateway();
        }
    }

    static class CapturingGateway implements MailGateway {
        final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
        volatile boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void send(OutgoingMail mail) {
            sent.add(mail);
        }

        List<OutgoingMail> to(String address) {
            return sent.stream().filter(m -> m.to().equals(address)).toList();
        }
    }

    @Autowired
    private CapturingGateway mail;

    @Autowired
    private MailDispatcher dispatcher;

    @Autowired
    private HourlyLimiter limiter;

    @BeforeEach
    void setUp() {
        mail.available = true;
        limiter.reset();
    }

    @Test
    void aForgottenPasswordIsResetThroughTheEmailedLink() throws Exception {
        String name = unique("forgot");
        String email = account(name, Role.CUSTOMER);
        String oldSession = refreshCookie(login(name, PASSWORD));

        forgot(email).andExpect(status().isAccepted());
        dispatcher.dispatchDue();

        List<OutgoingMail> sent = mail.to(email);
        assertThat(sent).hasSize(1);
        OutgoingMail resetMail = sent.get(0);
        assertThat(resetMail.subject()).startsWith("Reset your").endsWith("password");
        assertThat(resetMail.text()).contains(name).contains("60 minutes");
        String token = tokenIn(resetMail.text());
        assertThat(resetMail.html()).contains(token);

        reset(token, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(name))
                .andExpect(header().exists("Set-Cookie"));

        // The link worked once; the old password and the old sessions are gone.
        reset(token, "Another-Pass-3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:reset-link-invalid"));
        login(name, PASSWORD).andExpect(status().isUnauthorized());
        login(name, NEW_PASSWORD).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").header("X-Bayflow-Client", "web")
                        .cookie(new Cookie(COOKIE, oldSession)))
                .andExpect(status().isUnauthorized());

        // A confirmation follows, and no sent e-mail keeps its link in the database.
        dispatcher.dispatchDue();
        assertThat(mail.to(email)).extracting(OutgoingMail::subject).anyMatch(s -> s.endsWith("password was changed"));
        assertThat(ownerJdbc().queryForObject(
                "select count(*) from bayflow.auth_mail_outbox where recipient = ? and text_body <> ''",
                Integer.class, email)).isZero();
    }

    @Test
    void theAnswerIsTheSameForAnAddressWithoutAnAccount() throws Exception {
        String nobody = unique("nobody") + "@example.com";
        forgot(nobody).andExpect(status().isAccepted());
        dispatcher.dispatchDue();
        assertThat(mail.to(nobody)).isEmpty();
    }

    @Test
    void aDisabledAccountGetsNoLink() throws Exception {
        String name = unique("disabled");
        String email = account(name, Role.CUSTOMER);
        ownerJdbc().update("update bayflow.auth_users set enabled = false where username = ?", name);

        forgot(email).andExpect(status().isAccepted());
        dispatcher.dispatchDue();
        assertThat(mail.to(email)).isEmpty();
    }

    @Test
    void anExpiredLinkAndAnOlderLinkDoNotWork() throws Exception {
        String name = unique("expired");
        String email = account(name, Role.CUSTOMER);

        forgot(email).andExpect(status().isAccepted());
        forgot(email).andExpect(status().isAccepted());
        dispatcher.dispatchDue();
        List<OutgoingMail> sent = mail.to(email);
        assertThat(sent).hasSize(2);
        String older = tokenIn(sent.get(0).text());
        String newer = tokenIn(sent.get(1).text());

        // Asking again replaces the earlier link.
        reset(older, NEW_PASSWORD).andExpect(status().isBadRequest());

        ownerJdbc().update("update bayflow.auth_password_resets set expires_at = now() - interval '1 minute'"
                + " where user_id = (select id from bayflow.auth_users where username = ?)", name);
        reset(newer, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:reset-link-invalid"));
        login(name, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anAddressGetsAtMostThreeLinksAnHour() throws Exception {
        String name = unique("flood");
        String email = account(name, Role.CUSTOMER);
        for (int i = 0; i < 5; i++) {
            forgot(email).andExpect(status().isAccepted());
        }
        dispatcher.dispatchDue();
        assertThat(mail.to(email)).hasSize(3);
    }

    @Test
    void withoutEmailTheFeatureSaysSo() throws Exception {
        mail.available = false;
        forgot(unique("x") + "@example.com")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:password-reset-unavailable"));
    }

    @Test
    void theNewPasswordFollowsTheUsualRules() throws Exception {
        reset("not-a-token", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:validation-failed"));
        reset("not-a-real-token", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:reset-link-invalid"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private String account(String username, Role role) {
        String email = username + "@example.com";
        newUser(Tenants.DEFAULT_TENANT_ID, username, email, PASSWORD, Set.of(role));
        return email;
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private static String refreshCookie(ResultActions result) {
        Cookie cookie = result.andReturn().getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    private static String tokenIn(String text) {
        Matcher matcher = LINK.matcher(text);
        assertThat(matcher.find()).as("a reset link in: %s", text).isTrue();
        return matcher.group(1);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
