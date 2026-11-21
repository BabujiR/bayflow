package com.bayflow.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Beans every Bayflow resource server needs. The {@link JwtDecoder} is only created when
 * {@code bayflow.security.jwt.jwk-set-uri} is set; auth-service validates its own tokens
 * with its local public key instead.
 */
@AutoConfiguration
@EnableConfigurationProperties({ BayflowJwtProperties.class, BayflowTenancyProperties.class })
public class BayflowSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RolesClaimAuthenticationConverter rolesClaimAuthenticationConverter() {
        return new RolesClaimAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsSecurityHandlers problemDetailsSecurityHandlers() {
        return new ProblemDetailsSecurityHandlers();
    }

    @Bean
    @ConditionalOnMissingBean
    WorkshopHosts workshopHosts(BayflowTenancyProperties properties) {
        return new WorkshopHosts(properties.getBaseDomain());
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "bayflow.security.jwt", name = "jwk-set-uri")
    JwtDecoder bayflowJwtDecoder(BayflowJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(BayflowTokenValidators.of(properties.getIssuer(), properties.getAudience()));
        return decoder;
    }

    /** Services share one schema; their migrations take turns (see {@link SharedSchemaMigrations}). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy")
    static class MigrationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        FlywayMigrationStrategy sharedSchemaMigrations() {
            return new SharedSchemaMigrations();
        }
    }
}
