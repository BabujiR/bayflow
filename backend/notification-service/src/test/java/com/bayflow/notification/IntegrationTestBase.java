package com.bayflow.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.notification.integration.AuthClient;
import com.bayflow.notification.integration.AuthClient.LocationInfo;
import com.bayflow.security.Tenants;

/** Spring context on the shared PostgreSQL container, with helpers that sign requests as a role. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        // The service runs as the app login, subject to row-level security; Flyway as the owner.
        registry.add("spring.datasource.username", () -> PostgresContainer.APP_USER);
        registry.add("spring.datasource.password", () -> PostgresContainer.APP_PASSWORD);
        registry.add("spring.flyway.user", PostgresContainer.INSTANCE::getUsername);
        registry.add("spring.flyway.password", PostgresContainer.INSTANCE::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    /** auth-service knows who works where; tests answer for it. */
    @MockitoBean
    protected AuthClient auth;

    /** Nobody works anywhere, and every organization has "Main" in New York, unless a test says otherwise. */
    @BeforeEach
    void defaultAuthAnswers() {
        Mockito.doReturn(List.of()).when(auth)
                .staffAt(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doAnswer(call -> List.of(new LocationInfo(call.getArgument(0), "Main", true, "America/New_York")))
                .when(auth).locations(ArgumentMatchers.any());
    }

    /** The table owner: sees every workshop's rows. For test setup and checks only. */
    protected static JdbcTemplate ownerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(PostgresContainer.INSTANCE.getJdbcUrl(),
                PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword()));
    }

    /** A signed-in user of the default workshop with one role. */
    protected static RequestPostProcessor user(String role, UUID id) {
        return user(role, id, Tenants.DEFAULT_TENANT_ID);
    }

    protected static RequestPostProcessor user(String role, UUID id, UUID tenantId) {
        return jwt()
                .jwt(token -> token.subject(id.toString())
                        .claim("username", role.toLowerCase() + "-" + id.toString().substring(0, 4))
                        .claim("roles", List.of(role))
                        .claim(Tenants.CLAIM, tenantId.toString())
                        .claim(Tenants.LOCATIONS_CLAIM, List.of("*")))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** Another Bayflow service, as in production: a SYSTEM token without a workshop. */
    protected static RequestPostProcessor system() {
        return jwt()
                .jwt(token -> token.subject("appointment-service")
                        .claim("username", "appointment-service")
                        .claim("roles", List.of("SYSTEM")))
                .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM"));
    }
}
