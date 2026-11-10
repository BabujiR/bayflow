package com.bayflow.appointment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL 16 container for the whole run (singleton pattern), the real Flyway
 * migrations, a controllable clock, and helpers that sign requests as a given role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    /** Monday 16 Nov 2026, 09:00 in the workshop zone (America/New_York). */
    protected static final Instant BASE = Instant.parse("2026-11-16T14:00:00Z");

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BASE);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(BASE);
    }

    /** Signs the request as a user with the given role, like a real Bayflow access token would. */
    protected static RequestPostProcessor as(String role, UUID userId) {
        return jwt()
                .jwt(token -> token
                        .subject(userId.toString())
                        .claim("username", role.toLowerCase() + "-" + userId.toString().substring(0, 6))
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
