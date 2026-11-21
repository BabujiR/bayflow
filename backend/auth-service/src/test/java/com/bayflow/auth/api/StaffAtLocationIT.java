package com.bayflow.auth.api;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Notifications: notification-service asks who works at a location (SYSTEM only). */
class StaffAtLocationIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;
    private static final UUID NORTH = UUID.fromString("0a0a0a0a-5555-4000-8000-0000000000a5");

    private final JdbcTemplate jdbc = ownerJdbc();

    /** Other test classes expect the default organization to have one location. */
    @AfterEach
    void removeNorth() {
        jdbc.update("delete from bayflow.auth_user_locations where location_id = ?", NORTH);
        jdbc.update("delete from bayflow.auth_locations where id = ?", NORTH);
    }

    @Test
    void listsTheEnabledStaffWithTheRolesAtTheLocation() throws Exception {
        jdbc.update("""
                insert into bayflow.auth_locations (id, tenant_id, code, name, status, created_at, updated_at)
                values (?, ?, 'north-staff', 'North', 'ACTIVE', now(), now())""", NORTH, MAIN);
        String advisor = user(Role.SERVICE_ADVISOR);
        String manager = user(Role.MANAGER);
        String mechanic = user(Role.MECHANIC);
        String disabled = user(Role.SERVICE_ADVISOR);
        jdbc.update("update bayflow.auth_users set enabled = false where id = ?::uuid", disabled);
        String atNorth = user(Role.SERVICE_ADVISOR);
        jdbc.update("delete from bayflow.auth_user_locations where user_id = ?::uuid", atNorth);
        jdbc.update("""
                insert into bayflow.auth_user_locations (id, tenant_id, user_id, location_id, created_at)
                values (?, ?, ?::uuid, ?, now())""", UUID.randomUUID(), MAIN, atNorth, NORTH);
        String admin = user(Role.ADMIN);
        String system = systemToken();

        staff(system, MAIN, "SERVICE_ADVISOR,MANAGER").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems(advisor, manager)))
                .andExpect(jsonPath("$", not(hasItem(mechanic))))
                .andExpect(jsonPath("$", not(hasItem(disabled))))
                .andExpect(jsonPath("$", not(hasItem(atNorth))))
                .andExpect(jsonPath("$", not(hasItem(admin))));
        staff(system, MAIN, "MECHANIC").andExpect(jsonPath("$", hasItem(mechanic)))
                .andExpect(jsonPath("$", not(hasItem(advisor))));
        staff(system, NORTH, "SERVICE_ADVISOR").andExpect(jsonPath("$", hasItem(atNorth)))
                .andExpect(jsonPath("$", not(hasItem(advisor))));
        // Asked for in another organization, Main's people are not there.
        mvc.perform(get("/api/v1/auth/internal/tenants/" + UUID.randomUUID() + "/locations/" + MAIN + "/staff")
                        .param("roles", "SERVICE_ADVISOR").header("Authorization", "Bearer " + system))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyRolesThatWorkAtLocationsAndOnlyForServices() throws Exception {
        String system = systemToken();
        staff(system, MAIN, "CUSTOMER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-role"));
        staff(system, MAIN, "ADMIN").andExpect(status().isBadRequest());
        staff(system, MAIN, "WIZARD").andExpect(status().isBadRequest());

        staff(token(Role.ADMIN), MAIN, "SERVICE_ADVISOR").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations/" + MAIN + "/staff")
                .param("roles", "SERVICE_ADVISOR")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ---------------------------------------------------------------------

    private ResultActions staff(String token, UUID location, String roles) throws Exception {
        return mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations/" + location + "/staff")
                .param("roles", roles).header("Authorization", "Bearer " + token));
    }

    /** A new user at Main; returns their id. */
    private String user(Role role) {
        String username = "staff-" + UUID.randomUUID().toString().substring(0, 8);
        return newUser(MAIN, username, username + "@example.com", PASSWORD, Set.of(role)).getId().toString();
    }

    private String token(Role role) throws Exception {
        String username = "staff-" + UUID.randomUUID().toString().substring(0, 8);
        newUser(MAIN, username, username + "@example.com", PASSWORD, Set.of(role));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String systemToken() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"appointment-service\",\"clientSecret\":\"test-client-secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
