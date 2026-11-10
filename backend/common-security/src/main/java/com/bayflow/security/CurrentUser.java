package com.bayflow.security;

import java.util.Collection;
import java.util.Set;
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
 */
public record CurrentUser(String subject, String username, Set<String> roles) {

    public static CurrentUser from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        String username = authentication.getName();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String claim = jwtAuth.getToken().getClaimAsString("username");
            if (claim != null && !claim.isBlank()) {
                username = claim;
            }
        }
        return new CurrentUser(authentication.getName(), username, roleNames(authentication.getAuthorities()));
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

    public boolean isSystem() {
        return hasRole(Roles.SYSTEM);
    }

    private static Set<String> roleNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(Roles.AUTHORITY_PREFIX))
                .map(a -> a.substring(Roles.AUTHORITY_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
