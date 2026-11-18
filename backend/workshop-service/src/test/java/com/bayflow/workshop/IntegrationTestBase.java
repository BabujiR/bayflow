package com.bayflow.workshop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.bayflow.security.Tenants;

/** Spring context on the shared PostgreSQL container, with helpers that sign requests as a role. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresContainer.INSTANCE::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

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
