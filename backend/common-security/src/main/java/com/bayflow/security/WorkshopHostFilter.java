package com.bayflow.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A user token only works at its own workshop's address: on {@code acme.localhost} a token
 * whose {@value Tenants#SLUG_CLAIM} claim is not {@code acme} gets 401. The workshop inside the
 * token stays the only source of the tenant; this stops a session from one workshop being used
 * through another's address (and the confusion that would cause).
 * <p>
 * SYSTEM tokens (no workshop) and addresses without a workshop label are not checked.
 */
public class WorkshopHostFilter extends OncePerRequestFilter {

    private final WorkshopHosts hosts;
    private final ProblemDetailsSecurityHandlers handlers;

    public WorkshopHostFilter(WorkshopHosts hosts, ProblemDetailsSecurityHandlers handlers) {
        this.hosts = hosts;
        this.handlers = handlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.getToken().hasClaim(Tenants.CLAIM)) {
            Optional<String> addressed = hosts.slugOf(request);
            if (addressed.isPresent() && !addressed.get().equals(slugOf(token.getToken()))) {
                SecurityContextHolder.clearContext();
                handlers.workshopMismatch(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String slugOf(Jwt jwt) {
        String slug = jwt.getClaimAsString(Tenants.SLUG_CLAIM);
        return slug == null ? null : slug.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
