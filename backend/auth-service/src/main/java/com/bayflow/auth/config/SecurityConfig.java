package com.bayflow.auth.config;

import java.time.Clock;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.bayflow.auth.application.RsaKeyProvider;
import com.bayflow.security.BayflowSecurity;
import com.bayflow.security.BayflowTokenValidators;
import com.bayflow.security.ProblemDetailsSecurityHandlers;
import com.bayflow.security.RolesClaimAuthenticationConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints that never need a bearer token (a stale one sent by the browser is ignored). */
    static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/token");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** BCrypt by default, with the {id} prefix so the algorithm can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** auth-service checks its own tokens with its local public key; no JWKS round trip. */
    @Bean
    JwtDecoder jwtDecoder(RsaKeyProvider keys, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        decoder.setJwtValidator(BayflowTokenValidators.of(properties.getIssuer(), properties.getAudience()));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RolesClaimAuthenticationConverter converter,
            ProblemDetailsSecurityHandlers handlers) throws Exception {
        BayflowSecurity.statelessJwt(http, converter, handlers)
                .oauth2ResourceServer(o -> o.bearerTokenResolver(bearerTokenResolver()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> "POST".equals(request.getMethod()) && PUBLIC_POST_PATHS.contains(request.getRequestURI())
                ? null
                : defaultResolver.resolve(request);
    }
}
