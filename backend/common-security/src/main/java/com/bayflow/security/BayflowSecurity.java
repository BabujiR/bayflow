package com.bayflow.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Shared filter-chain setup for Bayflow services: stateless, no CSRF (bearer tokens only),
 * JWT resource server with role mapping and Problem Details for 401/403.
 * Each service adds its own {@code authorizeHttpRequests} rules.
 */
public final class BayflowSecurity {

    private BayflowSecurity() {
    }

    public static HttpSecurity statelessJwt(HttpSecurity http,
            RolesClaimAuthenticationConverter converter,
            ProblemDetailsSecurityHandlers handlers) throws Exception {
        return http
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
