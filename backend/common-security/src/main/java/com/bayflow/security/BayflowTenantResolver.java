package com.bayflow.security;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant a new session belongs to, so every entity with a
 * {@code @TenantId} field is filled and filtered automatically.
 * <p>
 * Registered by class name in each service's configuration
 * ({@code spring.jpa.properties.hibernate.tenant_identifier_resolver}).
 * <ul>
 *   <li>Normally: the workshop from {@link TenantContext} (explicit scope or the user's token).</li>
 *   <li>Inside {@link TenantContext#callAcrossTenants}: the root tenant, which Hibernate treats
 *       as "no tenant filter".</li>
 *   <li>Otherwise: {@link Tenants#NO_TENANT}. The framework opens sessions with no tenant (for
 *       example Spring Data preparing queries at startup), so this must not throw. It still fails
 *       closed: no row has that id, so reads find nothing, and a database CHECK on every
 *       {@code tenant_id} refuses to store it, so writes fail.</li>
 * </ul>
 */
public class BayflowTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Sentinel for platform-level sessions; never stored in a row. */
    public static final UUID ROOT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        if (TenantContext.isAcrossTenants()) {
            return ROOT;
        }
        return TenantContext.current().orElse(Tenants.NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return ROOT.equals(tenantId);
    }
}
