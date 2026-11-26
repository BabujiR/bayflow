package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.UserRepository;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;

/**
 * One person, several roles: e.g. a shop owner who is also the service advisor and the mechanic.
 * Roles combine (everything each allows); the workshop always keeps an admin.
 */
class MultiRoleIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;

    @Autowired
    private UserRepository users;

    private final JdbcTemplate jdbc = ownerJdbc();

    @Test
    void anOwnerWhoAlsoAdvisesAndFixesCarsHasAllThree() throws Exception {
        String admin = login(newStaff(Set.of(Role.ADMIN)).getUsername());
        String username = unique("owner");
        mvc.perform(post("/api/v1/auth/users").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s",
                                 "roles":["ADMIN","SERVICE_ADVISOR","MECHANIC"]}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ADMIN", "SERVICE_ADVISOR", "MECHANIC")))
                .andExpect(jsonPath("$.allLocations").value(true));

        String token = login(username);
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactlyInAnyOrder("ADMIN", "SERVICE_ADVISOR", "MECHANIC");
        // Admin pages work for them too.
        mvc.perform(get("/api/v1/auth/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // As an admin they work at every location, so the desk's and the mechanics' notifications
        // reach them there.
        UUID id = UUID.fromString(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
        assertThat(TenantContext.call(MAIN, () -> users.findEnabledIdsWithRolesAt(List.of(Role.MECHANIC), MAIN)))
                .contains(id);
    }

    @Test
    void anAdminChangesWhatSomeoneDoes() throws Exception {
        String admin = login(newStaff(Set.of(Role.ADMIN)).getUsername());
        User mechanic = newStaff(Set.of(Role.MECHANIC));

        setRoles(admin, mechanic.getId(), "[\"MECHANIC\",\"SERVICE_ADVISOR\"]", List.of(MAIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("MECHANIC", "SERVICE_ADVISOR")))
                .andExpect(jsonPath("$.allLocations").value(false))
                .andExpect(jsonPath("$.locationIds[0]").value(MAIN.toString()));

        // Made an admin: everywhere, no location list left behind.
        setRoles(admin, mechanic.getId(), "[\"ADMIN\",\"MECHANIC\"]", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allLocations").value(true));
        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_user_locations where user_id = ?",
                Integer.class, mechanic.getId())).isZero();

        // Back to mechanic only: at the location given.
        setRoles(admin, mechanic.getId(), "[\"MECHANIC\"]", List.of(MAIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("MECHANIC")))
                .andExpect(jsonPath("$.locationIds[0]").value(MAIN.toString()));
        assertThat(SignedJWT.parse(login(mechanic.getUsername())).getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("MECHANIC");
    }

    @Test
    void theRulesThatKeepTheWorkshopSafe() throws Exception {
        User me = newStaff(Set.of(Role.ADMIN, Role.MECHANIC));
        String admin = login(me.getUsername());

        setRoles(admin, me.getId(), "[\"MECHANIC\"]", List.of(MAIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:cannot-remove-own-admin"));
        // Adding to my own roles is fine.
        setRoles(admin, me.getId(), "[\"ADMIN\",\"MECHANIC\",\"SERVICE_ADVISOR\"]", null)
                .andExpect(status().isOk());

        User other = newStaff(Set.of(Role.MECHANIC));
        setRoles(admin, other.getId(), "[\"CUSTOMER\"]", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-role"));
        setRoles(admin, other.getId(), "[\"MECHANIC\",\"PLATFORM_ADMIN\"]", null)
                .andExpect(status().isBadRequest());
        setRoles(admin, other.getId(), "[]", null).andExpect(status().isBadRequest());

        User customer = TenantContext.call(MAIN, () -> newUser(MAIN, unique("cust"), unique("cust") + "@example.com",
                PASSWORD, Set.of(Role.CUSTOMER)));
        setRoles(admin, customer.getId(), "[\"MECHANIC\"]", List.of(MAIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:not-staff"));

        // Only admins change roles.
        setRoles(login(other.getUsername()), me.getId(), "[\"MECHANIC\"]", null).andExpect(status().isForbidden());
    }

    // ---- helpers ------------------------------------------------------------------

    private User newStaff(Set<Role> roles) {
        String username = unique("mr");
        User user = newUser(MAIN, username, username + "@example.com", PASSWORD, roles);
        if (!roles.contains(Role.ADMIN)) {
            jdbc.update("insert into bayflow.auth_user_locations (id, tenant_id, user_id, location_id, created_at)"
                    + " values (gen_random_uuid(), ?, ?, ?, now())", MAIN, user.getId(), MAIN);
        }
        return user;
    }

    private ResultActions setRoles(String token, UUID userId, String roles, List<UUID> locations) throws Exception {
        String ids = locations == null ? "" : ",\"locationIds\":[" + String.join(",",
                locations.stream().map(l -> "\"" + l + "\"").toList()) + "]";
        return mvc.perform(put("/api/v1/auth/users/" + userId + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":" + roles + ids + "}"));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
