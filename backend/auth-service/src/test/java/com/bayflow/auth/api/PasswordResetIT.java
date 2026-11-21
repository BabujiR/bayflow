package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Account recovery: sign-in with the e-mail address, staff password resets, changing a password. */
class PasswordResetIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final String NEW_PASSWORD = "Battery-Staple-2";
    private static final String COOKIE = "bayflow_rt";

    // ---- sign-in with the e-mail address --------------------------------------------------------

    @Test
    void customersCanSignInWithTheirEmailAddress() throws Exception {
        String name = unique("mail");
        account(name, Role.CUSTOMER);

        login((name + "@Example.com").toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(name))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
        login(name + "@example.com", "Wrong-Password-1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-credentials"));
        login(unique("nobody") + "@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-credentials"));
    }

    // ---- staff resets ----------------------------------------------------------------------------

    @Test
    void anAdminResetsAStaffPasswordAndTheyChooseTheirOwnAtSignIn() throws Exception {
        String admin = unique("admin");
        account(admin, Role.ADMIN);
        String mechanic = unique("mech");
        UUID mechanicId = account(mechanic, Role.MECHANIC);
        String oldSession = refreshCookie(login(mechanic, PASSWORD).andReturn());

        String temporary = reset(token(admin), mechanicId)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.username").value(mechanic))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String password = JsonPath.read(temporary, "$.temporaryPassword");
        assertThat(password).matches("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}");

        // The old password and the old sessions no longer work.
        login(mechanic, PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").header("X-Bayflow-Client", "web")
                        .cookie(new Cookie(COOKIE, oldSession)))
                .andExpect(status().isUnauthorized());

        // The temporary one signs in, flagged: the app asks for a new password first.
        MvcResult signedIn = login(mechanic, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();

        changePassword(accessToken(signedIn), password, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andExpect(header().exists("Set-Cookie"));

        login(mechanic, password).andExpect(status().isUnauthorized());
        login(mechanic, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    @Test
    void aResetUnlocksALockedAccount() throws Exception {
        String advisor = unique("adv");
        account(advisor, Role.SERVICE_ADVISOR);
        String customer = unique("locked");
        UUID customerId = account(customer, Role.CUSTOMER);
        for (int i = 1; i <= 5; i++) {
            login(customer, "Wrong-Password-" + i).andExpect(status().isUnauthorized());
        }
        login(customer, PASSWORD)
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:account-locked"));

        String password = JsonPath.read(reset(token(advisor), customerId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.temporaryPassword");

        login(customer, password).andExpect(status().isOk());
    }

    @Test
    void advisorsResetCustomersOnlyAndNobodyResetsTheirOwn() throws Exception {
        String admin = unique("admin");
        UUID adminId = account(admin, Role.ADMIN);
        String advisor = unique("adv");
        account(advisor, Role.SERVICE_ADVISOR);
        String customer = unique("cust");
        UUID customerId = account(customer, Role.CUSTOMER);
        UUID mechanicId = account(unique("mech"), Role.MECHANIC);

        String advisorToken = token(advisor);
        reset(advisorToken, customerId).andExpect(status().isOk());
        reset(advisorToken, mechanicId).andExpect(status().isForbidden());
        reset(advisorToken, adminId).andExpect(status().isForbidden());
        reset(token(unique("other"), Role.CUSTOMER), customerId).andExpect(status().isForbidden());
        reset(token(admin), adminId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:cannot-reset-self"));
        reset(token(admin), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ---- changing one's own password -------------------------------------------------------------

    @Test
    void changingAPasswordNeedsTheCurrentOneAndANewOne() throws Exception {
        String name = unique("chg");
        account(name, Role.CUSTOMER);
        MvcResult first = login(name, PASSWORD).andReturn();
        String otherSession = refreshCookie(login(name, PASSWORD).andReturn());
        String access = accessToken(first);

        changePassword(access, "Not-The-Password-1", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:current-password-incorrect"));
        changePassword(access, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:password-unchanged"));
        changePassword(access, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:validation-failed"));

        changePassword(access, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        // Other devices are signed out; the new password works, the old one doesn't.
        mvc.perform(post("/api/v1/auth/refresh").header("X-Bayflow-Client", "web")
                        .cookie(new Cookie(COOKIE, otherSession)))
                .andExpect(status().isUnauthorized());
        login(name, PASSWORD).andExpect(status().isUnauthorized());
        login(name, NEW_PASSWORD).andExpect(status().isOk());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private UUID account(String username, Role role) {
        return newUser(Tenants.DEFAULT_TENANT_ID, username, username + "@example.com", PASSWORD, Set.of(role))
                .getId();
    }

    private String token(String username) throws Exception {
        return accessToken(login(username, PASSWORD).andExpect(status().isOk()).andReturn());
    }

    private String token(String username, Role role) throws Exception {
        account(username, role);
        return token(username);
    }

    private ResultActions reset(String bearer, UUID userId) throws Exception {
        return mvc.perform(post("/api/v1/auth/users/" + userId + "/password-reset")
                .header("Authorization", "Bearer " + bearer));
    }

    private ResultActions changePassword(String bearer, String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/auth/me/password").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}
                        """.formatted(current, next)));
    }

    private ResultActions login(String username, String password) throws Exception {
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

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
