package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.domain.Role;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

class AuthApiIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final String COOKIE = "bayflow_rt";

    @Autowired
    private AuthService authService;

    // ---- registration ------------------------------------------------------

    @Test
    void registerCreatesACustomer() throws Exception {
        String username = unique("reg");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@example.com", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsDuplicateUsernameAndEmail() throws Exception {
        String username = unique("dup");
        register(username);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username.toUpperCase(), "other-" + username + "@example.com", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:username-taken"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("dup2"), username + "@example.com", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:email-taken"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("a b", "not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:malformed-request"));
    }

    // ---- login, me, lockout -------------------------------------------------

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        String username = unique("login");
        register(username);

        MvcResult result = login(username, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).hasSize(43);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(7 * 24 * 3600);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void usernameIsCaseInsensitiveAtLogin() throws Exception {
        String username = unique("case");
        register(username);

        login(username.toUpperCase(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void meWithoutTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:unauthorized"));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameError() throws Exception {
        String username = unique("wrong");
        register(username);

        login(username, "Wrong-Password-1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-credentials"));
        login(unique("nobody"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-credentials"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        String username = unique("lock");
        register(username);

        for (int i = 0; i < 5; i++) {
            login(username, "Wrong-Password-" + i)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-credentials"));
        }

        login(username, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:account-locked"));
    }

    // ---- refresh rotation, reuse detection, logout ----------------------------

    @Test
    void refreshRotatesTheCookieAndReturnsANewAccessToken() throws Exception {
        String username = unique("refresh");
        register(username);
        String first = refreshCookie(login(username, PASSWORD).andReturn());

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn();
        String second = refreshCookie(refreshed);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, second)))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        String username = unique("reuse");
        register(username);
        String first = refreshCookie(login(username, PASSWORD).andReturn());
        String second = refreshCookie(
                mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, first))).andReturn());

        // The old token is replayed (e.g. stolen) ...
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-refresh-token"));

        // ... so the legitimate newer token stops working too.
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-refresh-token"));
    }

    @Test
    void publicEndpointsIgnoreAStaleBearerToken() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer expired.or.invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-refresh-token"));
    }

    @Test
    void logoutClearsTheCookieAndEndsTheSession() throws Exception {
        String username = unique("logout");
        register(username);
        String cookie = refreshCookie(login(username, PASSWORD).andReturn());

        MvcResult result = mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getCookie(COOKIE).getMaxAge()).isZero();

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isUnauthorized());
    }

    // ---- client credentials and JWKS -----------------------------------------

    @Test
    void clientCredentialsIssueASystemToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"appointment-service","clientSecret":"test-client-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn();

        Jwt jwt = decodeWithJwks(accessToken(result));
        assertThat(jwt.getSubject()).isEqualTo("appointment-service");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SYSTEM");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken(result)))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongClientSecretIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"appointment-service","clientSecret":"nope"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-client"));
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"unknown","clientSecret":"x"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwksPublishesThePublicKeyThatVerifiesUserTokens() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());

        String username = unique("jwks");
        register(username);
        Jwt jwt = decodeWithJwks(accessToken(login(username, PASSWORD).andReturn()));

        assertThat(jwt.getClaimAsString("username")).isEqualTo(username);
        assertThat(jwt.getAudience()).containsExactly("bayflow");
    }

    // ---- staff administration -----------------------------------------------

    @Test
    void customersCannotManageStaff() throws Exception {
        String username = unique("cust");
        register(username);
        String token = accessToken(login(username, PASSWORD).andReturn());

        mvc.perform(get("/api/v1/auth/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:forbidden"));
    }

    @Test
    void advisorCreatesListsAndDisablesStaff() throws Exception {
        String advisor = unique("adv");
        authService.createUser(advisor, advisor + "@example.com", PASSWORD, Set.of(Role.SERVICE_ADVISOR));
        String token = accessToken(login(advisor, PASSWORD).andReturn());
        String mechanic = unique("mech");

        MvcResult created = mvc.perform(post("/api/v1/auth/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s","role":"MECHANIC"}
                                """.formatted(mechanic, mechanic, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("MECHANIC"))
                .andReturn();
        String mechanicId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/auth/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s","role":"CUSTOMER"}
                                """.formatted(unique("c"), unique("c"), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-role"));

        mvc.perform(get("/api/v1/auth/users?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", hasItem(mechanic)))
                .andExpect(jsonPath("$.size").value(100));

        String mechanicCookie = refreshCookie(login(mechanic, PASSWORD).andReturn());

        mvc.perform(patch("/api/v1/auth/users/" + mechanicId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Disabled: existing session ends and new logins are refused.
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, mechanicCookie)))
                .andExpect(status().isUnauthorized());
        login(mechanic, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:account-disabled"));
    }

    @Test
    void advisorCannotDisableThemselves() throws Exception {
        String advisor = unique("self");
        String id = authService.createUser(advisor, advisor + "@example.com", PASSWORD,
                Set.of(Role.SERVICE_ADVISOR)).getId().toString();
        String token = accessToken(login(advisor, PASSWORD).andReturn());

        mvc.perform(patch("/api/v1/auth/users/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:cannot-disable-self"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---- helpers --------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String registerJson(String username, String email, String password) {
        return """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@example.com", PASSWORD)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    private Jwt decodeWithJwks(String token) throws Exception {
        String json = mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        RSAKey key = JWKSet.parse(json).getKeys().get(0).toRSAKey();
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token);
    }
}
