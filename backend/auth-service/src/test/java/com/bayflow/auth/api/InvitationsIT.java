package com.bayflow.auth.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.HourlyLimiter;
import com.bayflow.auth.application.SignupRateLimiter;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Onboarding step 2: workshops join only through setup invites sent from the console. */
class InvitationsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Autowired
    private HourlyLimiter limiter;

    @Autowired
    private SignupRateLimiter signupLimiter;

    private final JdbcTemplate jdbc = ownerJdbc();

    private String team;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        limiter.reset();
        signupLimiter.reset();
        team = "team-" + suffix();
        newUser(Platform.TENANT_ID, team, team + "@example.com", PASSWORD, Set.of(Role.PLATFORM_ADMIN));
        token = JsonPath.read(body(mvc.perform(post("/api/v1/auth/login")
                        .header("X-Bayflow-Client", Platform.CONSOLE_CLIENT)
                        .header("X-Forwarded-Host", "localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(team, PASSWORD)))
                .andExpect(status().isOk())), "$.accessToken");
    }

    @Test
    void anInvitedOwnerSetsUpTheirWorkshopOnce() throws Exception {
        String shop = "Invite Motors " + suffix();
        String email = "owner-" + suffix() + "@example.com";
        mvc.perform(post("/api/v1/tenants/demo-requests").header("X-Bayflow-Client", "web")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"firstName":"Ana","lastName":"Silva","email":"%s","phone":"5550100199",
                                 "shopName":"%s","shopType":"TIRES","locations":"2-3"}""".formatted(email, shop)))
                .andExpect(status().isCreated());
        String demoId = JsonPath.read(body(console(get("/api/v1/auth/platform/demo-requests").param("q", shop))),
                "$.content[0].id");

        String issued = body(console(post("/api/v1/auth/platform/invitations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","contactName":"Ana Silva","shopName":"%s","demoRequestId":"%s"}"""
                                .formatted(email.toUpperCase(), shop, demoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.email").value(email))
                .andExpect(jsonPath("$.invitation.state").value("PENDING"))
                .andExpect(jsonPath("$.invitation.createdBy").value(team))
                .andExpect(jsonPath("$.emailed").value(false))
                .andExpect(jsonPath("$.setupUrl", startsWith("https://example.test/setup?invite="))));
        String link = linkToken(issued);
        console(get("/api/v1/auth/platform/demo-requests/" + demoId)).andExpect(jsonPath("$.status").value("INVITED"));

        // One live invite per address.
        console(post("/api/v1/auth/platform/invitations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:already-invited"));

        // The setup page reads who it's for.
        check(link).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.shopName").value(shop))
                .andExpect(jsonPath("$.contactName").value("Ana Silva"));

        // No invite, a made-up one: no workshop.
        String slug = "inv-" + suffix();
        signup(slug, null).andExpect(status().isBadRequest());
        signup(slug, "not-a-real-link").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-invalid"));

        signup(slug, link).andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value(slug));
        console(get("/api/v1/auth/platform/demo-requests/" + demoId))
                .andExpect(jsonPath("$.status").value("ONBOARDED"))
                .andExpect(jsonPath("$.updatedBy").value("owner"));
        console(get("/api/v1/auth/platform/invitations").param("q", email))
                .andExpect(jsonPath("$.content[0].state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content[0].workshopSlug").value(slug));

        // Used once only.
        check(link).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-used"));
        signup("inv-" + suffix(), link).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-used"));
        console(post("/api/v1/auth/platform/invitations/" + inviteId(issued) + "/revoke"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendReplacesTheLinkAndRevokeStopsIt() throws Exception {
        String email = "owner-" + suffix() + "@example.com";
        String first = body(invite(email).andExpect(status().isCreated()));
        String id = inviteId(first);

        String second = body(console(post("/api/v1/auth/platform/invitations/" + id + "/resend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation.sentCount").value(2)));
        check(linkToken(first)).andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-invalid"));
        check(linkToken(second)).andExpect(status().isOk());

        console(post("/api/v1/auth/platform/invitations/" + id + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REVOKED"))
                .andExpect(jsonPath("$.revokedBy").value(team));
        check(linkToken(second)).andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-invalid"));
        console(post("/api/v1/auth/platform/invitations/" + id + "/resend")).andExpect(status().isBadRequest());

        // Revoked: the address can be invited again.
        invite(email).andExpect(status().isCreated());
    }

    @Test
    void anExpiredLinkSaysSoAndCanBeResent() throws Exception {
        String email = "owner-" + suffix() + "@example.com";
        String issued = body(invite(email).andExpect(status().isCreated()));
        String id = inviteId(issued);
        jdbc.update("update bayflow.auth_invitations set expires_at = now() - interval '1 minute' where id = ?::uuid",
                id);

        check(linkToken(issued)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-expired"));
        signup("inv-" + suffix(), linkToken(issued))
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invite-expired"));
        console(get("/api/v1/auth/platform/invitations").param("state", "EXPIRED").param("q", email))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));
        // An expired invite doesn't block a new one either.
        invite(email).andExpect(status().isCreated());

        String resent = body(console(post("/api/v1/auth/platform/invitations/" + id + "/resend"))
                .andExpect(jsonPath("$.invitation.state").value("PENDING")));
        check(linkToken(resent)).andExpect(status().isOk());
    }

    @Test
    void onlyTheConsoleManagesInvites() throws Exception {
        String admin = "owner-" + suffix();
        newUser(Tenants.DEFAULT_TENANT_ID, admin, admin + "@example.com", PASSWORD, Set.of(Role.ADMIN));
        String workshopToken = JsonPath.read(body(mvc.perform(post("/api/v1/auth/login")
                        .header("X-Bayflow-Client", "web")
                        .header("X-Forwarded-Host", "demo.localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(admin, PASSWORD)))
                .andExpect(status().isOk())), "$.accessToken");
        mvc.perform(post("/api/v1/auth/platform/invitations").header("Authorization", "Bearer " + workshopToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/platform/invitations")).andExpect(status().isUnauthorized());
        // The public check needs the web app's header, like sign-up.
        mvc.perform(post("/api/v1/tenants/invitations/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ---------------------------------------------------------------

    private ResultActions invite(String email) throws Exception {
        return console(post("/api/v1/auth/platform/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"shopName\":\"Test shop\"}".formatted(email)));
    }

    private ResultActions console(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions check(String link) throws Exception {
        return mvc.perform(post("/api/v1/tenants/invitations/check").header(CookieRequestGuard.HEADER, "web")
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(link)));
    }

    private ResultActions signup(String slug, String link) throws Exception {
        String invite = link == null ? "" : ",\"inviteToken\":\"" + link + "\"";
        return mvc.perform(post("/api/v1/tenants/signup").header(CookieRequestGuard.HEADER, "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"workshopName":"Invited %s","slug":"%s","currency":"USD","timeZone":"America/New_York",
                         "adminUsername":"owner","adminEmail":"owner@%s.example","adminPassword":"%s"%s}
                        """.formatted(slug, slug, slug, PASSWORD, invite)));
    }

    private static String linkToken(String issued) {
        String url = JsonPath.read(issued, "$.setupUrl");
        return url.substring(url.indexOf("invite=") + "invite=".length());
    }

    private static String inviteId(String issued) {
        return JsonPath.read(issued, "$.invitation.id");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
