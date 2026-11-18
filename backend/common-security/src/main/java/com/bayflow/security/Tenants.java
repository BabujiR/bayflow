package com.bayflow.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant (workshop) identifiers shared by every Bayflow service.
 * <p>
 * Each user access token carries its workshop in the {@value #CLAIM} claim. SYSTEM tokens
 * (service-to-service) carry none.
 */
public final class Tenants {

    /** JWT claim holding the tenant UUID. */
    public static final String CLAIM = "tid";

    /**
     * JWT claim holding the workshop's slug, the first label of its address
     * ({@code <slug>.<base domain>}). Services compare it with the address a request came to.
     */
    public static final String SLUG_CLAIM = "tsl";

    /**
     * The workshop all data created before multi-tenancy belongs to. The same fixed value is
     * used by every service's Flyway migrations (placeholder {@code defaultTenantId}).
     */
    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Stands for "no workshop known". A database session opened without a tenant (for example
     * by the framework at startup) uses it: no row ever has this id, so reads find nothing, and
     * every tenant_id column has a CHECK constraint that refuses it, so writes fail.
     */
    public static final UUID NO_TENANT = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    private Tenants() {
    }

    /** Parses a tenant id claim; empty when missing or not a UUID. */
    public static Optional<UUID> parse(Object claim) {
        if (claim == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(claim.toString().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
