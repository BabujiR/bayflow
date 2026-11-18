package com.bayflow.auth.api;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.application.TenantSettingsService;
import com.bayflow.auth.application.TenantSettingsService.SettingsUpdate;
import com.bayflow.auth.domain.Role;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/**
 * SaaS phase 3: each workshop's own settings (name, contact, labour rate, currency, time zone).
 * Everyone in the workshop reads them, only its ADMIN changes them, other services read them
 * with a SYSTEM token, and no workshop sees another's.
 */
class TenantSettingsIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID OTHER = UUID.fromString("0c0c0c0c-0000-4000-8000-000000000003");

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantSettingsService settingsService;

    private final JdbcTemplate jdbc = ownerJdbc();

    @BeforeEach
    void otherWorkshop() {
        jdbc.update("""
                insert into bayflow.auth_tenants (id, slug, name, status, created_at, updated_at)
                values (?, 'settings-other', 'Kiwi Motors', 'ACTIVE', now(), now())
                on conflict (id) do nothing""", OTHER);
    }

    @Test
    void everyRoleReadsItsWorkshop() throws Exception {
        for (Role role : Role.values()) {
            mvc.perform(get("/api/v1/auth/tenant").header("Authorization", "Bearer " + tokenFor(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(Tenants.DEFAULT_TENANT_ID.toString()))
                    .andExpect(jsonPath("$.labourRate").isNumber())
                    .andExpect(jsonPath("$.currency").isString())
                    .andExpect(jsonPath("$.timeZone").isString())
                    .andExpect(jsonPath("$.version").isNumber());
        }
        mvc.perform(get("/api/v1/auth/tenant")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdminChangesTheSettings() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        long version = version(admin);

        update(admin, body("Bayflow Downtown", "120.50", "eur", "Europe/Berlin", version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bayflow Downtown"))
                .andExpect(jsonPath("$.labourRate").value(120.50))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.version").value(version + 1));

        for (Role role : new Role[] { Role.CUSTOMER, Role.SERVICE_ADVISOR, Role.MECHANIC }) {
            update(tokenFor(role), body("Hijacked", "1.00", "USD", "UTC", version + 1))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/auth/tenant").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.displayName").value("Bayflow Downtown"));
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        long version = version(admin);
        update(admin, body("First", "95.00", "USD", "America/New_York", version)).andExpect(status().isOk());

        update(admin, body("Second", "99.00", "USD", "America/New_York", version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:concurrent-update"));
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        long version = version(admin);

        update(admin, body("X", "95.00", "ZZZ", "UTC", version))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-currency"));
        update(admin, body("X", "95.00", "USD", "Mars/Olympus", version))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-time-zone"));
        update(admin, body("X", "-1", "USD", "UTC", version)).andExpect(status().isBadRequest());
        update(admin, body("", "95.00", "USD", "UTC", version)).andExpect(status().isBadRequest());
        // No version: the client must say which settings it is changing.
        update(admin, """
                {"displayName":"X","labourRate":95,"currency":"USD","timeZone":"UTC"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void workshopsNeverSeeEachOthersSettings() throws Exception {
        TenantContext.run(OTHER, () -> {
            long version = settingsService.current().settings().getVersion();
            settingsService.update(new SettingsUpdate("Kiwi Motors", null, null, null,
                    new BigDecimal("140.00"), "NZD", "Pacific/Auckland", version));
        });

        mvc.perform(get("/api/v1/auth/tenant").header("Authorization", "Bearer " + tokenFor(Role.ADMIN)))
                .andExpect(jsonPath("$.id").value(Tenants.DEFAULT_TENANT_ID.toString()))
                .andExpect(jsonPath("$.currency").value(not("NZD")))
                .andExpect(jsonPath("$.timeZone").value(not("Pacific/Auckland")));
    }

    @Test
    void servicesReadAnyWorkshopsSettingsWithASystemToken() throws Exception {
        TenantContext.run(OTHER, () -> settingsService.current());
        String system = systemToken();

        mvc.perform(get("/api/v1/auth/internal/tenants/" + OTHER + "/settings")
                        .header("Authorization", "Bearer " + system))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OTHER.toString()))
                .andExpect(jsonPath("$.name").value("Kiwi Motors"));
        mvc.perform(get("/api/v1/auth/internal/tenants/" + UUID.randomUUID() + "/settings")
                        .header("Authorization", "Bearer " + system))
                .andExpect(status().isNotFound());

        // Users can't use the internal endpoint, not even an admin.
        mvc.perform(get("/api/v1/auth/internal/tenants/" + OTHER + "/settings")
                        .header("Authorization", "Bearer " + tokenFor(Role.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/internal/tenants/" + OTHER + "/settings"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ------------------------------------------------------------------

    private String tokenFor(Role role) throws Exception {
        String username = role.name().toLowerCase().replace('_', '-') + "-"
                + UUID.randomUUID().toString().substring(0, 8);
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

    private long version(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/auth/tenant").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version")).longValue();
    }

    private ResultActions update(String token, String json) throws Exception {
        return mvc.perform(put("/api/v1/auth/tenant/settings").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String name, String rate, String currency, String zone, long version) {
        return """
                {"displayName":"%s","phone":"+1 555 0100","email":"desk@example.com","address":"1 Main St",
                 "labourRate":%s,"currency":"%s","timeZone":"%s","version":%d}
                """.formatted(name, rate, currency, zone, version);
    }
}
