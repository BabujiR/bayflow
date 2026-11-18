package com.bayflow.appointment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.bayflow.appointment.integration.WorkshopSettingsClient;
import com.bayflow.appointment.integration.WorkshopSettingsClient.WorkshopSettings;
import com.bayflow.security.Tenants;

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
        createAppRole(POSTGRES);
    }

    /** The services' runtime login (not the table owner), as in docker-compose. */
    protected static final String APP_USER = "bayflow_app";
    protected static final String APP_PASSWORD = "bayflow_app";

    /** Creates the app login once per container; Flyway (as the owner) grants it table access. */
    static void createAppRole(PostgreSQLContainer postgres) {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'bayflow_app') THEN
                            CREATE ROLE bayflow_app LOGIN PASSWORD 'bayflow_app' NOSUPERUSER NOBYPASSRLS;
                        END IF;
                    END $$""");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the app role", e);
        }
    }

    /** The table owner: sees every workshop's rows. For test setup and checks only. */
    protected static JdbcTemplate ownerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    }


    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // The service runs as the app login, subject to row-level security; Flyway as the owner.
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
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

    /** Each workshop's settings live in auth-service; tests answer for it. */
    @MockitoBean
    protected WorkshopSettingsClient workshopSettings;

    /** Every workshop is in New York unless a test says otherwise. */
    @BeforeEach
    void resetClock() {
        clock.set(BASE);
        Mockito.doReturn(new WorkshopSettings("America/New_York", "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any());
    }

    /** Signs the request as a user with the given role in the default workshop, like a real access token. */
    protected static RequestPostProcessor as(String role, UUID userId) {
        return as(role, userId, Tenants.DEFAULT_TENANT_ID);
    }

    /** Signs the request as a user with the given role in the given workshop ({@code tid} claim). */
    protected static RequestPostProcessor as(String role, UUID userId, UUID tenantId) {
        return jwt()
                .jwt(token -> token
                        .subject(userId.toString())
                        .claim("username", role.toLowerCase() + "-" + userId.toString().substring(0, 6))
                        .claim("roles", List.of(role))
                        .claim(Tenants.CLAIM, tenantId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
