package com.bayflow.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The caller behind the current request.
 *
 * @param subject  JWT "sub": the user id, or the client id for SYSTEM tokens
 * @param username JWT "username" claim (falls back to the subject)
 * @param roles    plain role names, e.g. CUSTOMER
 * @param tenantId the workshop from the {@value Tenants#CLAIM} claim; null for SYSTEM tokens
 */
public record CurrentUser(String subject, String username, Set<String> roles, UUID tenantId) {

    public CurrentUser(String subject, String username, Set<String> roles) {
        this(subject, username, roles, null);
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        String username = authentication.getName();
        UUID tenantId = null;
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String claim = jwtAuth.getToken().getClaimAsString("username");
            if (claim != null && !claim.isBlank()) {
                username = claim;
            }
            tenantId = Tenants.parse(jwtAuth.getToken().getClaim(Tenants.CLAIM)).orElse(null);
        }
        return new CurrentUser(authentication.getName(), username, roleNames(authentication.getAuthorities()),
                tenantId);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isCustomer() {
        return hasRole(Roles.CUSTOMER);
    }

    public boolean isServiceAdvisor() {
        return hasRole(Roles.SERVICE_ADVISOR);
    }

    public boolean isMechanic() {
        return hasRole(Roles.MECHANIC);
    }

    public boolean isAdmin() {
        return hasRole(Roles.ADMIN);
    }

    public boolean isSystem() {
        return hasRole(Roles.SYSTEM);
    }

    public boolean isManager() {
        return hasRole(Roles.MANAGER);
    }

    /** The locations this request may work with (see {@link LocationContext}). */
    public LocationScope locationScope() {
        return LocationContext.current();
    }

    private static Set<String> roleNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(Roles.AUTHORITY_PREFIX))
                .map(a -> a.substring(Roles.AUTHORITY_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
