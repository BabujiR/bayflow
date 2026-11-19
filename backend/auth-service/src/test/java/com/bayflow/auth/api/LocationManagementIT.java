package com.bayflow.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.UpcomingAppointments;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Organizations and locations, phase L3: an admin adds, changes, archives and reactivates locations. */
class LocationManagementIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;

    /** appointment-service, asked for upcoming appointments before archiving. */
    @MockitoBean
    private UpcomingAppointments upcoming;

    private final JdbcTemplate jdbc = ownerJdbc();
    private String admin;

    @BeforeEach
    void signIn() throws Exception {
        admin = tokenFor(Role.ADMIN);
        Mockito.doReturn(0L).when(upcoming).count(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @AfterEach
    void removeExtraLocations() {
        jdbc.update("delete from bayflow.auth_user_locations where location_id in (select id from"
                + " bayflow.auth_locations where tenant_id = ? and id <> ?)", MAIN, MAIN);
        jdbc.update("delete from bayflow.auth_locations where tenant_id = ? and id <> ?", MAIN, MAIN);
        jdbc.update("update bayflow.auth_locations set status = 'ACTIVE' where id = ?", MAIN);
    }

    @Test
    void anAdminAddsAndChangesALocation() throws Exception {
        String code = code();
        String body = create(code, "North", "Pacific/Auckland", "140.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timeZone").value("Pacific/Auckland"))
                .andExpect(jsonPath("$.ownTimeZone").value("Pacific/Auckland"))
                .andExpect(jsonPath("$.labourRate").value(140.00))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        long version = ((Number) JsonPath.read(body, "$.version")).longValue();

        // Clearing the overrides: the organization's settings apply again.
        update(id, code, "North Shore", "", null, version).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("North Shore"))
                .andExpect(jsonPath("$.ownTimeZone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ownLabourRate").value(org.hamcrest.Matchers.nullValue()));
        update(id, code, "Stale", "", null, version).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:concurrent-update"));
    }

    @Test
    void locationsAreChecked() throws Exception {
        String code = code();
        create(code, "North", null, null).andExpect(status().isCreated());
        create(code, "Again", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:code-taken"));
        create("main", "Main again", null, null).andExpect(status().isConflict());
        create("Bad Code", "X", null, null).andExpect(status().isBadRequest());
        create(code(), "X", "Mars/Olympus", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-time-zone"));
        create(code(), "X", null, "-1").andExpect(status().isBadRequest());
    }

    @Test
    void archivingIsRefusedForTheLastLocationAndWhileBookingsAreUpcoming() throws Exception {
        archive(MAIN.toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:last-location"));

        String north = JsonPath.read(create(code(), "North", null, null).andReturn().getResponse()
                .getContentAsString(), "$.id");
        Mockito.doReturn(2L).when(upcoming).count(ArgumentMatchers.any(), ArgumentMatchers.any());
        archive(north).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:location-has-appointments"));
        Mockito.doThrow(new IllegalStateException("down")).when(upcoming)
                .count(ArgumentMatchers.any(), ArgumentMatchers.any());
        archive(north).andExpect(status().isServiceUnavailable());

        Mockito.doReturn(0L).when(upcoming).count(ArgumentMatchers.any(), ArgumentMatchers.any());
        archive(north).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        // Customers no longer see it; staff still do (its history stays).
        mvc.perform(get("/api/v1/auth/locations").header("Authorization", "Bearer " + tokenFor(Role.CUSTOMER)))
                .andExpect(jsonPath("$[?(@.id == '" + north + "')]").isEmpty());
        mvc.perform(get("/api/v1/auth/locations").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.id == '" + north + "')].status").value("ARCHIVED"));

        mvc.perform(post("/api/v1/auth/locations/" + north + "/reactivate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void onlyAdminsManageLocations() throws Exception {
        for (Role role : new Role[] { Role.CUSTOMER, Role.SERVICE_ADVISOR, Role.MANAGER, Role.MECHANIC }) {
            mvc.perform(post("/api/v1/auth/locations").header("Authorization", "Bearer " + tokenFor(role))
                            .contentType(MediaType.APPLICATION_JSON).content(json(code(), "X", null, null, null)))
                    .andExpect(status().isForbidden());
        }
    }

    // ---- helpers ------------------------------------------------------------------------

    private static String code() {
        return "loc-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions create(String code, String name, String zone, String rate) throws Exception {
        return mvc.perform(post("/api/v1/auth/locations").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(code, name, zone, rate, null)));
    }

    private ResultActions update(String id, String code, String name, String zone, String rate, long version)
            throws Exception {
        return mvc.perform(put("/api/v1/auth/locations/" + id).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(code, name, zone, rate, version)));
    }

    private ResultActions archive(String id) throws Exception {
        return mvc.perform(post("/api/v1/auth/locations/" + id + "/archive").header("Authorization", "Bearer " + admin));
    }

    private static String json(String code, String name, String zone, String rate, Long version) {
        StringBuilder sb = new StringBuilder("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"");
        if (zone != null) {
            sb.append(",\"timeZone\":\"").append(zone).append('"');
        }
        if (rate != null) {
            sb.append(",\"labourRate\":").append(rate);
        }
        if (version != null) {
            sb.append(",\"version\":").append(version);
        }
        return sb.append('}').toString();
    }

    private String tokenFor(Role role) throws Exception {
        String username = role.name().toLowerCase().replace('_', '-') + "-" + UUID.randomUUID().toString().substring(0, 8);
        newUser(MAIN, username, username + "@example.com", PASSWORD, Set.of(role));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
