package com.bayflow.security;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The tenant (workshop) the current thread works for.
 * <p>
 * Normally it comes from the signed-in user's access token ({@value Tenants#CLAIM} claim).
 * Code that runs without a user token sets it explicitly for a block of work:
 * <ul>
 *   <li>{@link #call(UUID, Supplier)}: sign-in and registration (the tenant is known from the
 *       request), SYSTEM calls that name the tenant, one outbox row at a time;</li>
 *   <li>{@link #callAcrossTenants(Supplier)}: the few platform-level operations that are not
 *       about one workshop (service clients, finding due outbox rows). Use sparingly.</li>
 * </ul>
 * With neither set and no tenant in the token, {@link #current()} is empty and Hibernate works
 * as {@link Tenants#NO_TENANT}: it reads nothing and cannot write, so a missing tenant fails closed.
 */
public final class TenantContext {

    private record Scope(UUID tenantId, boolean acrossTenants) {
    }

    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Runs {@code work} as the given tenant, then restores the previous scope. */
    public static <T> T call(UUID tenantId, Supplier<T> work) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return within(new Scope(tenantId, false), work);
    }

    public static void run(UUID tenantId, Runnable work) {
        call(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /** Runs {@code work} at platform level, not as any one tenant. */
    public static <T> T callAcrossTenants(Supplier<T> work) {
        return within(new Scope(null, true), work);
    }

    public static void runAcrossTenants(Runnable work) {
        callAcrossTenants(() -> {
            work.run();
            return null;
        });
    }

    /** The current tenant: an explicit scope first, otherwise the signed-in user's token. */
    public static Optional<UUID> current() {
        Scope scope = SCOPE.get();
        if (scope != null) {
            return Optional.ofNullable(scope.tenantId());
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Tenants.parse(jwt.getToken().getClaim(Tenants.CLAIM));
        }
        return Optional.empty();
    }

    /** The current tenant, or an exception when there is none. */
    public static UUID require() {
        return current().orElseThrow(() -> new IllegalStateException("No tenant for the current operation"));
    }

    /** True inside {@link #callAcrossTenants(Supplier)}. */
    public static boolean isAcrossTenants() {
        Scope scope = SCOPE.get();
        return scope != null && scope.acrossTenants();
    }

    private static <T> T within(Scope scope, Supplier<T> work) {
        Scope previous = SCOPE.get();
        SCOPE.set(scope);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                SCOPE.remove();
            } else {
                SCOPE.set(previous);
            }
        }
    }
}
