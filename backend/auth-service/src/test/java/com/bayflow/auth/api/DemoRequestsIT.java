package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.HourlyLimiter;
import com.bayflow.auth.mail.MailDispatcher;

/** "Request a demo" on the public site: stored, the team is e-mailed, and the form is limited per IP. */
@Import(PasswordResetByEmailIT.CapturedMail.class)
@TestPropertySource(properties = {"bayflow.auth.demo.notify-email=sales@example.test",
        "bayflow.auth.demo.max-per-hour-per-ip=3"})
class DemoRequestsIT extends IntegrationTestBase {

    @Autowired
    private PasswordResetByEmailIT.CapturingGateway mail;

    @Autowired
    private MailDispatcher dispatcher;

    @Autowired
    private HourlyLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter.reset();
        mail.available = true;
    }

    @Test
    void aRequestIsStoredAndTheTeamIsEmailed() throws Exception {
        String shop = "Lotus Auto " + UUID.randomUUID().toString().substring(0, 6);
        String email = uniqueEmail();
        request(body(email, shop, "4-10")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.id").doesNotExist());

        assertThat(ownerJdbc().queryForObject(
                "select locations from bayflow.auth_demo_requests where shop_name = ?", String.class, shop))
                .isEqualTo("4-10");
        // The form no longer asks for the shop type.
        assertThat(ownerJdbc().queryForObject(
                "select shop_type from bayflow.auth_demo_requests where shop_name = ?", String.class, shop))
                .isNull();
        dispatcher.dispatchDue();
        assertThat(mail.to("sales@example.test")).anySatisfy(sent -> {
            assertThat(sent.subject()).isEqualTo("Demo request: " + shop + " (4-10 locations)");
            assertThat(sent.replyTo()).isEqualTo(email);
            assertThat(sent.text()).contains("Ana Silva").contains("+1 (555) 010-0199")
                    .doesNotContain("Type:");
        });
        // Nothing goes to the address typed in.
        assertThat(mail.to(email)).isEmpty();
    }

    @Test
    void askingAgainWhileTheRequestIsOpenJoinsIt() throws Exception {
        String email = uniqueEmail();
        String shop = "Repeat Motors " + UUID.randomUUID().toString().substring(0, 6);
        request(body(email.toUpperCase(), shop, "1")).andExpect(status().isCreated());
        request(body(email, shop + " Group", "4-10").replace("Two bays, looking to switch.", "Now three shops"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(true));

        var jdbc = ownerJdbc();
        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_demo_requests where email = ?",
                Integer.class, email)).isEqualTo(1);
        var row = jdbc.queryForMap(
                "select shop_name, locations, repeat_count, last_requested_at, notes from bayflow.auth_demo_requests"
                        + " where email = ?", email);
        // The first details stay; the new ones are in the notes.
        assertThat(row.get("shop_name")).isEqualTo(shop);
        assertThat(row.get("locations")).isEqualTo("1");
        assertThat(row.get("repeat_count")).isEqualTo(1);
        assertThat(row.get("last_requested_at")).isNotNull();
        assertThat((String) row.get("notes")).startsWith("Asked again ")
                .contains(shop + " Group").contains("4-10 locations").contains("\"Now three shops\"");
        dispatcher.dispatchDue();
        assertThat(mail.to("sales@example.test")).anySatisfy(sent -> assertThat(sent.subject())
                .isEqualTo("Demo request (asked again): " + shop + " Group (4-10 locations)"));

        // Once the request is closed, asking again starts a new one.
        jdbc.update("update bayflow.auth_demo_requests set status = 'DECLINED' where email = ?", email);
        request(body(email, shop, "1")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_demo_requests where email = ?",
                Integer.class, email)).isEqualTo(2);
    }

    @Test
    void requiredFieldsAndKnownChoicesOnly() throws Exception {
        request("{\"firstName\":\"Ana\"}").andExpect(status().isBadRequest());
        request(body("Shop", "lots")).andExpect(status().isBadRequest());
        request(body("Shop", "1").replace("+1 (555) 010-0199", "call me")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/tenants/demo-requests").contentType(MediaType.APPLICATION_JSON)
                .content(body("Shop", "1"))).andExpect(status().isForbidden());
    }

    @Test
    void tooManyFromOneAddressAreRefused() throws Exception {
        for (int i = 0; i < 3; i++) {
            request(body("Shop " + i, "1")).andExpect(status().isCreated());
        }
        request(body("Shop 4", "1")).andExpect(status().isTooManyRequests());
    }

    private ResultActions request(String json) throws Exception {
        return mvc.perform(post("/api/v1/tenants/demo-requests").header("X-Bayflow-Client", "web")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** From a new address each time (asking again from the same one joins the open request). */
    private static String body(String shop, String locations) {
        return body(uniqueEmail(), shop, locations);
    }

    private static String body(String email, String shop, String locations) {
        return """
                {"firstName":"Ana","lastName":"Silva","email":"%s","phone":"+1 (555) 010-0199",
                 "shopName":"%s","locations":"%s","city":"Detroit, MI",
                 "message":"Two bays, looking to switch."}""".formatted(email, shop, locations);
    }

    private static String uniqueEmail() {
        return "ana-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
