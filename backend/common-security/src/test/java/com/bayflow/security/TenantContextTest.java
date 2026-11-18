package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private final BayflowTenantResolver resolver = new BayflowTenantResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noTokenAndNoScopeMeansNoTenantAndTheResolverUsesTheNoTenantId() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(Tenants.NO_TENANT);
        assertThat(resolver.isRoot(Tenants.NO_TENANT)).isFalse();
    }

    @Test
    void readsTheTenantFromTheSignedInUsersToken() {
        SecurityContextHolder.getContext().setAuthentication(new RolesClaimAuthenticationConverter()
                .convert(TestJwts.userJwt("user-1", "advisor1", List.of("SERVICE_ADVISOR"))));

        assertThat(TenantContext.current()).contains(Tenants.DEFAULT_TENANT_ID);
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(Tenants.DEFAULT_TENANT_ID);
    }

    @Test
    void anExplicitScopeWinsAndNestedScopesAreRestored() {
        TenantContext.run(A, () -> {
            assertThat(TenantContext.require()).isEqualTo(A);
            TenantContext.run(B, () -> assertThat(TenantContext.require()).isEqualTo(B));
            assertThat(TenantContext.require()).isEqualTo(A);
        });
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void theScopeIsRestoredWhenWorkThrows() {
        assertThatThrownBy(() -> TenantContext.run(A, () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void acrossTenantsResolvesToRoot() {
        UUID resolved = TenantContext.callAcrossTenants(resolver::resolveCurrentTenantIdentifier);

        assertThat(resolved).isEqualTo(BayflowTenantResolver.ROOT);
        assertThat(resolver.isRoot(resolved)).isTrue();
        assertThat(resolver.isRoot(A)).isFalse();
        assertThat(TenantContext.isAcrossTenants()).isFalse();
    }

    @Test
    void parsesOnlyUuidClaims() {
        assertThat(Tenants.parse(" " + A + " ")).contains(A);
        assertThat(Tenants.parse("acme")).isEmpty();
        assertThat(Tenants.parse(null)).isEmpty();
    }
}
