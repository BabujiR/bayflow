package com.bayflow.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * The Team page lists staff only; customers' online accounts are handled from the customer's
 * page: status, unlock (advisors too), disable/enable (managers and admins), never staff's.
 */
class CustomerAccountsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;

    private final JdbcTemplate jdbc = ownerJdbc();

    @Test
    void theTeamListHasNoCustomersAndTheCustomerListHasOnlyThem() throws Exception {
        String admin = token(newAccount(Set.of(Role.ADMIN)));
        User customer = newAccount(Set.of(Role.CUSTOMER));

        mvc.perform(get("/api/v1/auth/users").param("kind", "staff").param("size", "200")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].roles[*]", not(hasItem("CUSTOMER"))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(customer.getId().toString()))));
        mvc.perform(get("/api/v1/auth/users").param("kind", "customer").param("size", "200")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.content[*].roles[0]", everyItem(org.hamcrest.Matchers.is("CUSTOMER"))))
                .andExpect(jsonPath("$.content[*].id", hasItem(customer.getId().toString())));
        mvc.perform(get("/api/v1/auth/users").param("kind", "robots").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAdvisorSeesAndUnlocksAManagerAlsoDisables() throws Exception {
        User customer = newAccount(Set.of(Role.CUSTOMER));
        String advisor = token(newAccount(Set.of(Role.SERVICE_ADVISOR)));
        String manager = token(newAccount(Set.of(Role.MANAGER)));
        jdbc.update("update bayflow.auth_users set locked_until = now() + interval '1 hour' where id = ?",
                customer.getId());

        account(advisor, customer.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(customer.getUsername()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.locked").value(true));
        mvc.perform(get("/api/v1/auth/customer-accounts").param("ids", customer.getId().toString(),
                        UUID.randomUUID().toString()).header("Authorization", "Bearer " + advisor))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].locked").value(true));

        mvc.perform(post("/api/v1/auth/customer-accounts/" + customer.getId() + "/unlock")
                        .header("Authorization", "Bearer " + advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        // Disabling: not for advisors; managers can, and it signs the customer out.
        enabled(advisor, customer.getId(), false).andExpect(status().isForbidden());
        enabled(manager, customer.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(customer.getUsername(),
                                PASSWORD)))
                .andExpect(status().isUnauthorized());
        enabled(manager, customer.getId(), true).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void staffAccountsAreNeverReachableHere() throws Exception {
        String manager = token(newAccount(Set.of(Role.MANAGER)));
        User mechanic = newAccount(Set.of(Role.MECHANIC));
        account(manager, mechanic.getId()).andExpect(status().isNotFound());
        enabled(manager, mechanic.getId(), false).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/customer-accounts/" + mechanic.getId() + "/unlock")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isNotFound());
        // Mechanics and customers don't manage accounts.
        account(token(newAccount(Set.of(Role.MECHANIC))), mechanic.getId()).andExpect(status().isForbidden());
    }

    // ---- helpers ------------------------------------------------------------------

    private User newAccount(Set<Role> roles) {
        String username = "ca-" + UUID.randomUUID().toString().substring(0, 8);
        return newUser(MAIN, username, username + "@example.com", PASSWORD, roles);
    }

    private String token(User user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.getUsername(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions account(String token, UUID userId) throws Exception {
        return mvc.perform(get("/api/v1/auth/customer-accounts/" + userId).header("Authorization", "Bearer " + token));
    }

    private ResultActions enabled(String token, UUID userId, boolean enabled) throws Exception {
        return mvc.perform(patch("/api/v1/auth/customer-accounts/" + userId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"));
    }
}
