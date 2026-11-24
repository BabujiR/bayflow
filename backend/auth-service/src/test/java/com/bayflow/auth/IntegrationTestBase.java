package com.bayflow.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one PostgreSQL 16 container for the whole test run (singleton pattern) and
 * points the application at it. The real Flyway migrations create schema "bayflow".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

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

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private com.bayflow.auth.application.AuthService testAuthService;

    @Autowired
    private com.bayflow.auth.application.UserLocationService testUserLocations;

    @Autowired
    private com.bayflow.auth.application.InvitationService testInvitations;

    /** A fresh setup invite's token, as the console would send it (sign-up needs one). */
    protected String inviteToken() {
        String email = "invitee-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        var issued = com.bayflow.security.TenantContext.call(com.bayflow.auth.domain.Platform.TENANT_ID,
                () -> testInvitations.create("test", email, null, null, null));
        return issued.setupUrl().substring(issued.setupUrl().indexOf("invite=") + "invite=".length());
    }

    /**
     * Creates an account in {@code tenant} like an admin would: staff who work at particular
     * locations (MANAGER, SERVICE_ADVISOR, MECHANIC) are assigned to the organization's first
     * location, "Main" (id = tenant id), so they can sign in.
     */
    protected com.bayflow.auth.domain.User newUser(java.util.UUID tenant, String username, String email,
            String password, java.util.Set<com.bayflow.auth.domain.Role> roles) {
        return com.bayflow.security.TenantContext.call(tenant, () -> {
            var user = testAuthService.createUser(username, email, password, roles);
            if (com.bayflow.auth.application.UserLocationService.isLocationScoped(user)) {
                testUserLocations.assign(user, java.util.List.of(tenant));
            }
            return user;
        });
    }
}
