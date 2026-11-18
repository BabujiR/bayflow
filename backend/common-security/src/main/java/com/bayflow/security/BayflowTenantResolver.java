package com.bayflow.security;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant a new session belongs to, so every entity with a
 * {@code @TenantId} field is filled and filtered automatically.
 * <p>
 * Registered by class name in each service's configuration
 * ({@code spring.jpa.properties.hibernate.tenant_identifier_resolver}). It throws when no
 * tenant is known, so code that forgot to set one fails instead of reading every workshop's
 * data. Inside {@link TenantContext#callAcrossTenants} it returns the root tenant, which
 * Hibernate treats as "no tenant filter".
 */
public class BayflowTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Sentinel for platform-level sessions; never stored in a row. */
    public static final UUID ROOT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        if (TenantContext.isAcrossTenants()) {
            return ROOT;
        }
        return TenantContext.current().orElseThrow(() -> new IllegalStateException(
                "No tenant for this database session. Use the signed-in user's token or TenantContext."));
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
