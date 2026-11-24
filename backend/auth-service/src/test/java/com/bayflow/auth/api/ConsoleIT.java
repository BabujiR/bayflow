package com.bayflow.auth.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.HourlyLimiter;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** The Ramplight console: who can sign in to it, and the demo requests it shows. */
class ConsoleIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Autowired
    private HourlyLimiter limiter;

    private String username;

    @BeforeEach
    void setUp() {
        limiter.reset();
        username = "team-" + UUID.randomUUID().toString().substring(0, 8);
        newUser(Platform.TENANT_ID, username, username + "@example.com", PASSWORD, Set.of(Role.PLATFORM_ADMIN));
    }

    @Test
    void theTeamSignsInAtTheConsoleOnly() throws Exception {
        consoleLogin(username).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", hasItem("PLATFORM_ADMIN")));
        // Not at a workshop's address, nor at the console organization's own slug.
        login(username, "web", "demo.localhost").andExpect(status().isUnauthorized());
        login(username, "web", Platform.SLUG + ".localhost").andExpect(status().isNotFound());

        // A workshop admin can't sign in to the console, nor use it.
        String admin = "owner-" + UUID.randomUUID().toString().substring(0, 8);
        newUser(Tenants.DEFAULT_TENANT_ID, admin, admin + "@example.com", PASSWORD, Set.of(Role.ADMIN));
        consoleLogin(admin).andExpect(status().isUnauthorized());
        String workshopToken = JsonPath.read(body(login(admin, "web", "demo.localhost")
                .andExpect(status().isOk())), "$.accessToken");
        mvc.perform(get("/api/v1/auth/platform/demo-requests").header("Authorization", "Bearer " + workshopToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/platform/demo-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void theTeamWorksThroughTheDemoRequests() throws Exception {
        String shop = "Console Motors " + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/v1/tenants/demo-requests").header("X-Bayflow-Client", "web")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"firstName":"Ana","lastName":"Silva","email":"ana@example.com","phone":"5550100199",
                                 "shopName":"%s","shopType":"TIRES","locations":"2-3"}""".formatted(shop)))
                .andExpect(status().isCreated());
        String token = JsonPath.read(body(consoleLogin(username)), "$.accessToken");

        String found = body(mvc.perform(get("/api/v1/auth/platform/demo-requests").param("q", shop)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shopName").value(shop))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].locations").value("2-3")));
        String id = JsonPath.read(found, "$.content[0].id");

        update(token, id, "{\"status\":\"CONTACTED\",\"notes\":\"Called, demo Thursday\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.notes").value("Called, demo Thursday"))
                .andExpect(jsonPath("$.updatedBy").value(username));
        // A stale version, and statuses only invitations set, are refused.
        update(token, id, "{\"status\":\"DECLINED\",\"version\":0}").andExpect(status().isConflict());
        update(token, id, "{\"status\":\"ONBOARDED\",\"version\":1}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/auth/platform/demo-requests").param("status", "NEW").param("q", shop)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
    }

    private ResultActions update(String token, String id, String json) throws Exception {
        return mvc.perform(put("/api/v1/auth/platform/demo-requests/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions consoleLogin(String name) throws Exception {
        return login(name, Platform.CONSOLE_CLIENT, "localhost");
    }

    private ResultActions login(String name, String client, String host) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .header("X-Bayflow-Client", client)
                .header("X-Forwarded-Host", host)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(name, PASSWORD)));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
