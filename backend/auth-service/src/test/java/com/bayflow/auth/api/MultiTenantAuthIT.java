package com.bayflow.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.application.AuthService.Session;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.UserRepository;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.nimbusds.jwt.SignedJWT;

/**
 * Two workshops in one database: accounts are independent per workshop, lookups never cross
 * workshops, and tokens name the user's workshop.
 */
class MultiTenantAuthIT extends IntegrationTestBase {

    private static final String PASSWORD = "Correct-Horse-1";
    private static final UUID OTHER = UUID.fromString("0b0b0b0b-0000-4000-8000-000000000002");

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void secondWorkshop() {
        jdbc.update("""
                insert into auth_tenants (id, slug, name, status, created_at, updated_at)
                values (?, 'other-workshop', 'Other workshop', 'ACTIVE', now(), now())
                on conflict (id) do nothing""", OTHER);
    }

    @Test
    void theSameUsernameAndEmailMayExistInTwoWorkshops() {
        String username = "same-" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@example.com";

        User inDefault = TenantContext.call(Tenants.DEFAULT_TENANT_ID,
                () -> authService.register(username, email, PASSWORD));
        User inOther = TenantContext.call(OTHER, () -> authService.register(username, email, PASSWORD));

        assertThat(inDefault.getId()).isNotEqualTo(inOther.getId());
        assertThat(inDefault.getTenantId()).isEqualTo(Tenants.DEFAULT_TENANT_ID);
        assertThat(inOther.getTenantId()).isEqualTo(OTHER);

        // ...but not twice in the same workshop.
        assertThatThrownBy(() -> TenantContext.call(OTHER,
                () -> authService.register(username, "x-" + email, PASSWORD)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("username is already taken");
    }

    @Test
    void lookupsNeverCrossWorkshops() {
        String username = "only-default-" + UUID.randomUUID().toString().substring(0, 8);
        TenantContext.run(Tenants.DEFAULT_TENANT_ID,
                () -> authService.register(username, username + "@example.com", PASSWORD));

        assertThat(TenantContext.call(Tenants.DEFAULT_TENANT_ID, () -> users.findByUsername(username))).isPresent();
        assertThat(TenantContext.call(OTHER, () -> users.findByUsername(username))).isEmpty();
    }

    @Test
    void rolesAndTokensCarryTheUsersWorkshop() throws Exception {
        String username = "other-" + UUID.randomUUID().toString().substring(0, 8);
        User user = TenantContext.call(OTHER, () -> authService.register(username, username + "@example.com", PASSWORD));

        UUID roleTenant = jdbc.queryForObject("select tenant_id from auth_user_roles where user_id = ?",
                UUID.class, user.getId());
        assertThat(roleTenant).isEqualTo(OTHER);

        Session session = TenantContext.call(OTHER, () -> authService.login(username, PASSWORD, "127.0.0.1"));
        String tid = SignedJWT.parse(session.accessToken().value()).getJWTClaimsSet().getStringClaim("tid");
        assertThat(tid).isEqualTo(OTHER.toString());

        UUID tokenTenant = jdbc.queryForObject("select tenant_id from auth_refresh_tokens where user_id = ? limit 1",
                UUID.class, user.getId());
        assertThat(tokenTenant).isEqualTo(OTHER);
    }

    @Test
    void signInThroughTheApiOnlyReachesTheRequestsWorkshop() throws Exception {
        String username = "elsewhere-" + UUID.randomUUID().toString().substring(0, 8);
        TenantContext.run(OTHER, () -> authService.register(username, username + "@example.com", PASSWORD));

        // Until workshops get their own addresses (phase 4), every request is for the default workshop.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workWithoutAWorkshopFailsClosed() {
        // Hibernate refuses to open a session without a workshop (Spring may wrap the error).
        Throwable thrown = catchThrowable(() -> authService.register("nobody", "nobody@example.com", PASSWORD));
        assertThat(thrown).isNotNull();
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(IllegalStateException.class);
    }
}
