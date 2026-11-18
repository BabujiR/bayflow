package com.bayflow.appointment.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.security.Tenants;

/**
 * With a pool of ONE database connection, requests from two workshops take turns on the very
 * same connection. The workshop is set per transaction (set_config(..., true)), so it never
 * carries over to the next request: each workshop always sees exactly its own data.
 */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class SingleConnectionPoolIT extends IntegrationTestBase {

    private static final UUID OTHER = UUID.fromString("0c0c0c0c-0000-4000-8000-000000000003");

    @Test
    void theWorkshopNeverLeaksBetweenRequestsOnAPooledConnection() throws Exception {
        String marker = "Pool " + UUID.randomUUID().toString().substring(0, 8);
        createCustomer(Tenants.DEFAULT_TENANT_ID, marker + " here");
        createCustomer(OTHER, marker + " there");
        RequestPostProcessor hereAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID(), Tenants.DEFAULT_TENANT_ID);
        RequestPostProcessor otherAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID(), OTHER);

        for (int round = 0; round < 5; round++) {
            mvc.perform(get("/api/v1/customers").param("q", marker).with(hereAdvisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value(marker + " here"));
            mvc.perform(get("/api/v1/customers").param("q", marker).with(otherAdvisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value(marker + " there"));
        }
    }

    private void createCustomer(UUID tenant, String name) throws Exception {
        mvc.perform(post("/api/v1/customers").with(as("SERVICE_ADVISOR", UUID.randomUUID(), tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isCreated());
    }
}
