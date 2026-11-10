package com.bayflow.workshop.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.bayflow.security.BayflowSecurity;
import com.bayflow.security.ProblemDetailsSecurityHandlers;
import com.bayflow.security.RolesClaimAuthenticationConverter;

/**
 * Tokens are verified with auth-service's public key (JWKS, configured by
 * bayflow.security.jwt.* in common-security). Endpoint roles are set with @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RolesClaimAuthenticationConverter converter,
            ProblemDetailsSecurityHandlers handlers) throws Exception {
        BayflowSecurity.statelessJwt(http, converter, handlers)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
