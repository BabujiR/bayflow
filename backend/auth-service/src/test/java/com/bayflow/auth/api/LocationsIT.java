package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.application.SignupRateLimiter;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * Organizations and locations, phase L1: every organization has its locations, starting with
 * "Main" (id = tenant id); a location's effective settings are its overrides, else the
 * organization's.
 */
class LocationsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID MAIN = Tenants.DEFAULT_TENANT_ID;

    @Autowired
    private AuthService authService;

    @Autowired
    private SignupRateLimiter signupLimiter;

    private final JdbcTemplate jdbc = ownerJdbc();

    @BeforeEach
    void freshLimits() {
        signupLimiter.reset();
    }

    @Test
    void theDefaultOrganizationHasItsMainLocation() throws Exception {
        mvc.perform(get("/api/v1/auth/locations").header("Authorization", "Bearer " + tokenFor(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'main')].id").value(MAIN.toString()))
                .andExpect(jsonPath("$[?(@.code == 'main')].name").value("Main"))
                .andExpect(jsonPath("$[?(@.code == 'main')].status").value("ACTIVE"));
    }

    @Test
    void aLocationInheritsTheOrganizationsSettingsUnlessItOverridesThem() throws Exception {
        UUID north = UUID.randomUUID();
        jdbc.update("""
                insert into bayflow.auth_locations (id, tenant_id, code, name, time_zone, labour_rate, status,
                    created_at, updated_at)
                values (?, ?, ?, 'North', 'Pacific/Auckland', 140.00, 'ACTIVE', now(), now())""",
                north, MAIN, "north-" + north.toString().substring(0, 6));
        String orgZone = jdbc.queryForObject(
                "select time_zone from bayflow.auth_tenant_settings where tenant_id = ?", String.class, MAIN);
        String system = systemToken();

        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations/" + north + "/settings")
                        .header("Authorization", "Bearer " + system))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Pacific/Auckland"))
                .andExpect(jsonPath("$.labourRate").value(140.00))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations/" + MAIN + "/settings")
                        .header("Authorization", "Bearer " + system))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value(orgZone));

        mvc.perform(get("/api/v1/auth/locations").header("Authorization", "Bearer " + tokenFor(Role.ADMIN)))
                .andExpect(jsonPath("$[?(@.id == '" + north + "')].ownTimeZone").value("Pacific/Auckland"))
                .andExpect(jsonPath("$[?(@.id == '" + MAIN + "')].ownTimeZone")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void aNewOrganizationStartsWithItsMainLocation() throws Exception {
        String slug = "loc-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/v1/tenants/signup").header(CookieRequestGuard.HEADER, "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workshopName":"Loc Test","slug":"%s","currency":"NZD","timeZone":"Pacific/Auckland",
                                 "adminUsername":"owner","adminEmail":"owner@example.com","adminPassword":"%s"}
                                """.formatted(slug, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID tenant = UUID.fromString(JsonPath.read(body, "$.id"));

        mvc.perform(get("/api/v1/auth/internal/tenants/" + tenant + "/locations")
                        .header("Authorization", "Bearer " + systemToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tenant.toString()))
                .andExpect(jsonPath("$[0].code").value("main"))
                .andExpect(jsonPath("$[0].timeZone").value("Pacific/Auckland"))
                .andExpect(jsonPath("$[0].currency").value("NZD"));
    }

    @Test
    void locationsNeverCrossOrganizations() throws Exception {
        UUID other = UUID.randomUUID();
        jdbc.update("""
                insert into bayflow.auth_tenants (id, slug, name, status, created_at, updated_at)
                values (?, ?, 'Other', 'ACTIVE', now(), now())""", other, "o-" + other.toString().substring(0, 8));
        String system = systemToken();
        // The other organization's Main appears on first use.
        mvc.perform(get("/api/v1/auth/internal/tenants/" + other + "/locations")
                        .header("Authorization", "Bearer " + system))
                .andExpect(jsonPath("$", hasSize(1)));

        // Asking for its location under the default organization finds nothing.
        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations/" + other + "/settings")
                        .header("Authorization", "Bearer " + system))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/auth/locations").header("Authorization", "Bearer " + tokenFor(Role.ADMIN)))
                .andExpect(jsonPath("$[?(@.id == '" + other + "')]").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from bayflow.auth_locations where id = ?", Integer.class,
                other)).isEqualTo(1);
    }

    @Test
    void theInternalEndpointsAreForServicesOnly() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations/" + MAIN + "/settings")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/internal/tenants/" + MAIN + "/locations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/locations")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ---------------------------------------------------------------------

    private String tokenFor(Role role) throws Exception {
        String username = role.name().toLowerCase().replace('_', '-') + "-" + UUID.randomUUID().toString().substring(0, 8);
        TenantContext.run(Tenants.DEFAULT_TENANT_ID,
                () -> authService.createUser(username, username + "@example.com", PASSWORD, Set.of(role)));
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
