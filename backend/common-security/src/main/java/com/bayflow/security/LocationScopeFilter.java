package com.bayflow.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Works out the request's {@link LocationScope}: the token's locations, narrowed to one by the
 * web app's location switcher ({@code X-Bayflow-Location: <location id>}; {@code all} or no
 * header keeps the token's). The header can only narrow: a location outside the token's scope
 * gets 403 {@code location-not-allowed}.
 */
public class LocationScopeFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Bayflow-Location";

    private final ProblemDetailsSecurityHandlers handlers;

    public LocationScopeFilter(ProblemDetailsSecurityHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            LocationScope scope = LocationContext.ofToken(token);
            String header = request.getHeader(HEADER);
            if (StringUtils.hasText(header) && !"all".equalsIgnoreCase(header.trim())) {
                Optional<LocationScope> narrowed = Tenants.parse(header).flatMap(scope::narrowTo);
                if (narrowed.isEmpty()) {
                    handlers.locationNotAllowed(request, response);
                    return;
                }
                scope = narrowed.get();
            }
            request.setAttribute(LocationContext.REQUEST_ATTRIBUTE, scope);
        }
        chain.doFilter(request, response);
    }
}
