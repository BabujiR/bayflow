package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.ops.RequestIds;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Admin > Activity: account changes are recorded, with who made them, for the workshop's admins. */
class AuditEventsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;

    @Test
    void accountChangesAreRecordedWithTheirAdmin() throws Exception {
        String adminName = unique("admin");
        String admin = tokenFor(adminName, Role.ADMIN);
        String staffName = unique("mechanic");

        String created = mvc.perform(post("/api/v1/auth/users").header("Authorization", "Bearer " + admin)
                        .header(RequestIds.HEADER, "audit-it-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s","role":"MECHANIC"}
                                """.formatted(staffName, staffName, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String staffId = JsonPath.read(created, "$.id");
        mvc.perform(put("/api/v1/auth/users/{id}/roles", staffId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"MECHANIC\",\"SERVICE_ADVISOR\"]}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/auth/users/{id}", staffId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/api/v1/auth/audit-events").param("userId", staffId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();

        List<String> actions = JsonPath.read(body, "$.content[*].action");
        assertThat(actions).containsExactly("ACCOUNT_DISABLED", "ROLES_CHANGED", "STAFF_CREATED");
        List<String> actors = JsonPath.read(body, "$.content[*].actorName");
        assertThat(actors).containsOnly(adminName);
        assertThat((String) JsonPath.read(body, "$.content[1].details"))
                .startsWith("Service advisor, Mechanic").endsWith("(was Mechanic)");
        assertThat((String) JsonPath.read(body, "$.content[2].targetName")).isEqualTo(staffName);
        assertThat((String) JsonPath.read(body, "$.content[2].requestId")).isEqualTo("audit-it-request-1");
    }

    @Test
    void onlyAdminsSeeTheTrail() throws Exception {
        String manager = tokenFor(unique("manager"), Role.MANAGER);
        mvc.perform(get("/api/v1/auth/audit-events").header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
    }

    @Test
    void theTrailCannotBeChangedByTheServices() {
        // The app login may add events but never edit or delete them.
        List<String> grants = ownerJdbc().queryForList("""
                select privilege_type from information_schema.role_table_grants
                where table_name = 'auth_audit_events' and grantee = ?""", String.class, APP_USER);
        assertThat(grants).containsExactlyInAnyOrder("SELECT", "INSERT");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String tokenFor(String username, Role role) throws Exception {
        newUser(MAIN, username, username + "@example.com", PASSWORD, Set.of(role));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
