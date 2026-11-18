package com.bayflow.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * Shared filter-chain setup for Bayflow services: stateless, no CSRF (bearer tokens only),
 * JWT resource server with role mapping, Problem Details for 401/403, and the check that a
 * user token is used at its own workshop's address ({@link WorkshopHostFilter}).
 * Each service adds its own {@code authorizeHttpRequests} rules.
 */
public final class BayflowSecurity {

    private BayflowSecurity() {
    }

    public static HttpSecurity statelessJwt(HttpSecurity http,
            RolesClaimAuthenticationConverter converter,
            ProblemDetailsSecurityHandlers handlers,
            WorkshopHosts hosts) throws Exception {
        return http
                .addFilterAfter(new WorkshopHostFilter(hosts, handlers), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new LocationScopeFilter(handlers), WorkshopHostFilter.class)
                // CSRF protection is off on purpose: every request is authenticated by a bearer
                // token in the Authorization header, which a browser never attaches by itself,
                // and there are no sessions or authentication cookies to ride on. (Spring
                // Security's guidance for stateless, non-browser-session APIs.) The one place a
                // cookie authenticates a request, auth-service's /refresh and /logout, has its own
                // CSRF defence: see CookieRequestGuard in auth-service.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers));
    }
}
