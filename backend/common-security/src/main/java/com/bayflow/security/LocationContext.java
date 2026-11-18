package com.bayflow.security;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The location scope the current thread works with, in this order:
 * <ol>
 *   <li>an explicit scope set with {@link #call(LocationScope, Supplier)};</li>
 *   <li>the request's scope, worked out by {@link LocationScopeFilter} from the token and the
 *       {@code X-Bayflow-Location} header;</li>
 *   <li>the token: SYSTEM tokens work with every location of the organization they name;</li>
 *   <li>background work inside an explicit {@link TenantContext} (the outbox, start-up tasks):
 *       every location of that organization;</li>
 *   <li>otherwise none, so location-bound tables show nothing.</li>
 * </ol>
 */
public final class LocationContext {

    /** Request attribute holding the request's {@link LocationScope}. */
    public static final String REQUEST_ATTRIBUTE = LocationContext.class.getName() + ".scope";

    private static final ThreadLocal<LocationScope> SCOPE = new ThreadLocal<>();

    private LocationContext() {
    }

    public static <T> T call(LocationScope scope, Supplier<T> work) {
        LocationScope previous = SCOPE.get();
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

    public static LocationScope current() {
        LocationScope explicit = SCOPE.get();
        if (explicit != null) {
            return explicit;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof LocationScope scope) {
                return scope;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return ofToken(token);
        }
        return TenantContext.isExplicit() ? LocationScope.ALL : LocationScope.NONE;
    }

    /**
     * A token's own scope: SYSTEM, ADMIN and CUSTOMER tokens cover every location; staff tokens
     * cover the locations in their {@value Tenants#LOCATIONS_CLAIM} claim (none if it is missing:
     * {@link RolesClaimAuthenticationConverter} refuses such tokens anyway).
     */
    public static LocationScope ofToken(JwtAuthenticationToken token) {
        CurrentUser user = CurrentUser.from(token);
        if (user.isSystem() || user.isAdmin() || !isLocationScoped(user)) {
            return LocationScope.ALL;
        }
        return LocationScope.fromClaim(token.getToken().getClaim(Tenants.LOCATIONS_CLAIM)).orElse(LocationScope.NONE);
    }

    static boolean isLocationScoped(CurrentUser user) {
        return user.roles().stream().anyMatch(Roles.LOCATION_SCOPED::contains);
    }
}
