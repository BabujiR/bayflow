package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.SignupRateLimiter;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;

/**
 * SaaS phase 4: workshops at their own addresses (<slug>.localhost) and sign-up (each with its
 * own setup invite here; InvitationsIT covers the invites themselves).
 * Requests carry X-Forwarded-Host like the web app's nginx sends it.
 */
class WorkshopSignupIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Autowired
    private SignupRateLimiter signupLimiter;

    private final JdbcTemplate jdbc = ownerJdbc();

    @BeforeEach
    void freshLimits() {
        signupLimiter.reset();
    }

    // ---- sign-up -----------------------------------------------------------------

    @Test
    void aNewWorkshopSignsUpAndItsAdminSignsInAtItsAddress() throws Exception {
        String slug = uniqueSlug();

        signup(slug, "owner", "owner@" + slug + ".example")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.name").value("Workshop " + slug))
                .andExpect(jsonPath("$.adminUsername").value("owner"));

        String token = accessToken(login(slug, "owner", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN")));
        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getJWTClaimsSet().getStringClaim("tsl")).isEqualTo(slug);

        // Its settings exist, from the sign-up form.
        mvc.perform(get("/api/v1/auth/tenant").header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-Host", slug + ".localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.displayName").value("Workshop " + slug))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"));

        // The public name for its sign-in page.
        mvc.perform(get("/api/v1/tenants/current").header("X-Forwarded-Host", slug + ".localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.displayName").value("Workshop " + slug))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void theSameUsernamesWorkIndependentlyInTwoWorkshops() throws Exception {
        String a = uniqueSlug();
        String b = uniqueSlug();
        signup(a, "owner", "owner@example.com").andExpect(status().isCreated());
        signup(b, "owner", "owner@example.com").andExpect(status().isCreated());

        String tokenA = accessToken(login(a, "owner", PASSWORD).andExpect(status().isOk()));
        String tokenB = accessToken(login(b, "owner", PASSWORD).andExpect(status().isOk()));
        assertThat(SignedJWT.parse(tokenA).getJWTClaimsSet().getStringClaim("tid"))
                .isNotEqualTo(SignedJWT.parse(tokenB).getJWTClaimsSet().getStringClaim("tid"));

        // Customer registration at one address does not touch the other.
        mvc.perform(post("/api/v1/auth/register").header("X-Forwarded-Host", a + ".localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("kiri", "kiri@example.com")))
                .andExpect(status().isCreated());
        login(b, "kiri", PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").header("X-Forwarded-Host", b + ".localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("kiri", "kiri@example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void aTokenOnlyWorksAtItsOwnWorkshopsAddress() throws Exception {
        String a = uniqueSlug();
        String b = uniqueSlug();
        signup(a, "owner", "owner@example.com").andExpect(status().isCreated());
        signup(b, "owner", "owner@example.com").andExpect(status().isCreated());
        String tokenA = accessToken(login(a, "owner", PASSWORD).andExpect(status().isOk()));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenA)
                        .header("X-Forwarded-Host", a + ".localhost:3000"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenA)
                        .header("X-Forwarded-Host", b + ".localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:workshop-mismatch"));
        // Addresses without a workshop (the service's own port) don't check it.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void addressesMustBeValidFreeAndNotReserved() throws Exception {
        String taken = uniqueSlug();
        attempt(taken, "owner", "owner@example.com").andExpect(status().isCreated());

        attempt(taken, "other", "other@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:slug-taken"));
        attempt("demo", "other", "other@example.com").andExpect(status().isConflict());
        for (String reserved : new String[] { "www", "api", "admin", "default" }) {
            attempt(reserved, "other", "other@example.com")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("urn:bayflow:problem:slug-reserved"));
        }
        for (String invalid : new String[] { "-abc", "abc-", "a--b", "ab_c", "a.b", "Ab C" }) {
            attempt(invalid, "other", "other@example.com")
                    .andExpect(status().isBadRequest());
        }
        attempt("ab", "other", "other@example.com").andExpect(status().isBadRequest());

        // Upper case is accepted and stored in lower case.
        String mixed = uniqueSlug();
        attempt(mixed.toUpperCase(), "owner", "owner@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(mixed));

        mvc.perform(get("/api/v1/tenants/slug-availability").param("slug", taken))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("taken"));
        mvc.perform(get("/api/v1/tenants/slug-availability").param("slug", "www"))
                .andExpect(jsonPath("$.reason").value("reserved"));
        mvc.perform(get("/api/v1/tenants/slug-availability").param("slug", "x"))
                .andExpect(jsonPath("$.reason").value("invalid"));
        mvc.perform(get("/api/v1/tenants/slug-availability").param("slug", uniqueSlug()))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void aFailedSignUpLeavesNothingBehind() throws Exception {
        String slug = uniqueSlug();
        signupRequest(slug, "owner", "owner@example.com", "XYZ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-currency"));

        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_tenants where slug = ?", Integer.class, slug))
                .isZero();
        signup(slug, "owner", "owner@example.com").andExpect(status().isCreated());
    }

    @Test
    void signUpNeedsTheWebAppHeaderAndIsRateLimitedPerAddress() throws Exception {
        mvc.perform(post("/api/v1/tenants/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueSlug(), "owner", "owner@example.com", "USD")))
                .andExpect(status().isForbidden());

        for (int i = 0; i < 5; i++) {
            signup(uniqueSlug(), "owner", "owner@example.com").andExpect(status().isCreated());
        }
        signup(uniqueSlug(), "owner", "owner@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Retry-After"));
    }

    // ---- addresses ------------------------------------------------------------

    @Test
    void unknownAndSuspendedWorkshops() throws Exception {
        login("no-such-workshop", "owner", PASSWORD)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:workshop-not-found"));
        mvc.perform(get("/api/v1/tenants/current").header("X-Forwarded-Host", "no-such-workshop.localhost"))
                .andExpect(status().isNotFound());

        String slug = uniqueSlug();
        signup(slug, "owner", "owner@example.com").andExpect(status().isCreated());
        jdbc.update("update bayflow.auth_tenants set status = 'SUSPENDED' where slug = ?", slug);

        login(slug, "owner", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:workshop-suspended"));
        mvc.perform(get("/api/v1/tenants/current").header("X-Forwarded-Host", slug + ".localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void theDemoWorkshopIsTheOldDefaultOne() throws Exception {
        assertThat(jdbc.queryForObject("select slug from bayflow.auth_tenants where id = ?", String.class,
                com.bayflow.security.Tenants.DEFAULT_TENANT_ID)).isEqualTo("demo");
        // MockMvc's own "localhost" has no workshop label: the test profile's fallback is "demo".
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bootstrap-admin\",\"password\":\"Bootstrap-Admin-1\"}"))
                .andExpect(status().isOk());
        login("demo", "bootstrap-admin", "Bootstrap-Admin-1").andExpect(status().isOk());
    }

    // ---- helpers ---------------------------------------------------------------

    private static String uniqueSlug() {
        return "ws-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * A sign-up attempt that doesn't use up the per-IP limit: tests of the address rules make
     * more attempts than the limit allows (every attempt counts, refused ones too).
     */
    private ResultActions attempt(String slug, String username, String email) throws Exception {
        signupLimiter.reset();
        return signup(slug, username, email);
    }

    private ResultActions signup(String slug, String username, String email) throws Exception {
        return signupRequest(slug, username, email, "EUR");
    }

    private ResultActions signupRequest(String slug, String username, String email, String currency)
            throws Exception {
        return mvc.perform(withClientHeader(post("/api/v1/tenants/signup"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson(slug, username, email, currency)));
    }

    private static MockHttpServletRequestBuilder withClientHeader(MockHttpServletRequestBuilder builder) {
        return builder.header(CookieRequestGuard.HEADER, "web");
    }

    private String signupJson(String slug, String username, String email, String currency) {
        return """
                {"workshopName":"Workshop %s","slug":"%s","labourRate":110,"currency":"%s",
                 "timeZone":"Europe/Berlin","adminUsername":"%s","adminEmail":"%s","adminPassword":"%s",
                 "inviteToken":"%s"}
                """.formatted(slug.toLowerCase(), slug, currency, username, email, PASSWORD, inviteToken());
    }

    private ResultActions login(String slug, String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").header("X-Forwarded-Host", slug + ".localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private static String registerJson(String username, String email) {
        return "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(username, email, PASSWORD);
    }

    private static String accessToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
