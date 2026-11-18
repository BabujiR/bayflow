package com.bayflow.workshop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.math.BigDecimal;
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

import com.bayflow.security.Tenants;
import com.bayflow.workshop.integration.WorkshopSettingsClient;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;

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

    /** Each workshop's settings live in auth-service; tests answer for it. */
    @MockitoBean
    protected WorkshopSettingsClient workshopSettings;

    /** Every workshop charges 95.00 USD unless a test says otherwise. */
    @BeforeEach
    void defaultWorkshopSettings() {
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("95.00"), "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /** The table owner: sees every workshop's rows. For test setup and checks only. */
    protected static JdbcTemplate ownerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(PostgresContainer.INSTANCE.getJdbcUrl(),
                PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword()));
    }

    /**
     * Signs the request like a Bayflow access token for {@code subject} with one role, in the
     * default workshop. SYSTEM tokens carry no workshop, as in production.
     */
    protected static RequestPostProcessor as(String role, String subject, String username) {
        return as(role, subject, username, "SYSTEM".equals(role) ? null : Tenants.DEFAULT_TENANT_ID);
    }

    /** As above, in the given workshop ({@code tid} claim); null for none. */
    protected static RequestPostProcessor as(String role, String subject, String username, UUID tenantId) {
        return jwt()
                .jwt(token -> {
                    token.subject(subject)
                            .claim("username", username)
                            .claim("roles", List.of(role));
                    if (tenantId != null) {
                        token.claim(Tenants.CLAIM, tenantId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
