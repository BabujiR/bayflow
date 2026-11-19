package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;

/**
 * Organizations and locations, phase L2: staff work at particular locations, named in their
 * token's {@code lsc} claim; MANAGER is a location's manager.
 */
class ScopedRolesIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;

    @Autowired
    private AuthService authService;

    private final JdbcTemplate jdbc = ownerJdbc();

    @AfterEach
    void removeExtraLocations() {
        jdbc.update("delete from bayflow.auth_user_locations where location_id in (select id from"
                + " bayflow.auth_locations where tenant_id = ? and id <> ?)", MAIN, MAIN);
        jdbc.update("delete from bayflow.auth_locations where tenant_id = ? and id <> ?", MAIN, MAIN);
    }

    @Test
    void tokensNameWhereTheUserWorks() throws Exception {
        assertThat(lsc(tokenFor(Role.ADMIN))).containsExactly("*");
        assertThat(lsc(tokenFor(Role.CUSTOMER))).containsExactly("*");
        for (Role staff : new Role[] { Role.MANAGER, Role.SERVICE_ADVISOR, Role.MECHANIC }) {
            assertThat(lsc(tokenFor(staff))).as(staff.name()).containsExactly(MAIN.toString());
        }
    }

    @Test
    void anAdminCreatesStaffAtTheirLocations() throws Exception {
        String admin = tokenFor(Role.ADMIN);

        // One location: staff work there without saying so.
        createStaff(admin, "MANAGER", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"))
                .andExpect(jsonPath("$.allLocations").value(false))
                .andExpect(jsonPath("$.locationIds[0]").value(MAIN.toString()));
        createStaff(admin, "ADMIN", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.allLocations").value(true))
                .andExpect(jsonPath("$.locationIds").isEmpty());
        createStaff(admin, "ADMIN", List.of(MAIN)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:not-location-scoped"));

        // Several locations: name them.
        UUID north = addLocation("north", "ACTIVE");
        UUID closed = addLocation("closed", "ARCHIVED");
        createStaff(admin, "MECHANIC", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:locations-required"));
        createStaff(admin, "MECHANIC", List.of(closed)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-location"));
        createStaff(admin, "MECHANIC", List.of(UUID.randomUUID())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-location"));
        createStaff(admin, "SERVICE_ADVISOR", List.of(north, MAIN)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationIds.length()").value(2));
    }

    @Test
    void changingLocationsAppliesToTheNextToken() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        UUID north = addLocation("north", "ACTIVE");
        String username = unique("adv");
        String body = createStaff(admin, "SERVICE_ADVISOR", List.of(MAIN), username)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat(lsc(login(username))).containsExactly(MAIN.toString());

        mvc.perform(put("/api/v1/auth/users/" + id + "/locations").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationIds\":[\"" + north + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationIds[0]").value(north.toString()));
        assertThat(lsc(login(username))).containsExactly(north.toString());

        // Staff need at least one location; admins take none; only admins change them.
        mvc.perform(put("/api/v1/auth/users/" + id + "/locations").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locationIds\":[]}"))
                .andExpect(status().isBadRequest());
        String adminId = JsonPath.read(createStaff(admin, "ADMIN", null).andReturn().getResponse()
                .getContentAsString(), "$.id");
        mvc.perform(put("/api/v1/auth/users/" + adminId + "/locations").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locationIds\":[\"" + MAIN + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:not-location-scoped"));
        mvc.perform(put("/api/v1/auth/users/" + id + "/locations").header("Authorization", "Bearer " + login(username))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locationIds\":[\"" + MAIN + "\"]}"))
                .andExpect(status().isForbidden());

        // The account list shows where each person works.
        mvc.perform(get("/api/v1/auth/users?size=200").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].locationIds[0]").value(north.toString()));
    }

    @Test
    void staffWithoutALocationCannotSignIn() throws Exception {
        String username = unique("lost");
        TenantContext.run(MAIN, () -> authService.createUser(username, username + "@example.com", PASSWORD,
                Set.of(Role.MECHANIC)));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:no-locations"));
    }

    @Test
    void aManagerCanDoWhatAnAdvisorCan() throws Exception {
        String manager = tokenFor(Role.MANAGER);
        mvc.perform(get("/api/v1/auth/tenant").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"))
                .andExpect(jsonPath("$.locationIds[0]").value(MAIN.toString()));
        // ...but is not an admin.
        mvc.perform(get("/api/v1/auth/users").header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
    }

    // ---- helpers -----------------------------------------------------------------------

    private UUID addLocation(String code, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into bayflow.auth_locations (id, tenant_id, code, name, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())""", id, MAIN, code + "-" + id.toString().substring(0, 6), code,
                status);
        return id;
    }

    private ResultActions createStaff(String adminToken, String role, List<UUID> locations) throws Exception {
        return createStaff(adminToken, role, locations, unique(role.toLowerCase().replace('_', '-')));
    }

    private ResultActions createStaff(String adminToken, String role, List<UUID> locations, String username)
            throws Exception {
        String ids = locations == null ? "" : ",\"locationIds\":[" + String.join(",",
                locations.stream().map(l -> "\"" + l + "\"").toList()) + "]";
        return mvc.perform(post("/api/v1/auth/users").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","email":"%s@example.com","password":"%s","role":"%s"%s}
                        """.formatted(username, username, PASSWORD, role, ids)));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String tokenFor(Role role) throws Exception {
        String username = unique(role.name().toLowerCase().replace('_', '-'));
        newUser(MAIN, username, username + "@example.com", PASSWORD, Set.of(role));
        return login(username);
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static List<String> lsc(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet().getStringListClaim("lsc");
    }
}
