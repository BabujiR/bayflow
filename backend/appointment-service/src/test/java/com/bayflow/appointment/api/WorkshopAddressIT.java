package com.bayflow.appointment.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.security.Tenants;

/** SaaS phase 4: a user token only works at its own workshop's address (<slug>.localhost). */
class WorkshopAddressIT extends IntegrationTestBase {

    private static final String LIST = "/api/v1/appointments";

    private static RequestPostProcessor advisorOf(String slug) {
        return jwt()
                .jwt(token -> {
                    token.subject(UUID.randomUUID().toString())
                            .claim("username", "advisor1")
                            .claim("roles", List.of("SERVICE_ADVISOR"))
                            .claim(Tenants.CLAIM, Tenants.DEFAULT_TENANT_ID.toString())
                            .claim(Tenants.LOCATIONS_CLAIM, List.of("*"));
                    if (slug != null) {
                        token.claim(Tenants.SLUG_CLAIM, slug);
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_ADVISOR"));
    }

    @Test
    void aTokenWorksAtItsOwnWorkshopsAddress() throws Exception {
        mvc.perform(get(LIST).with(advisorOf("demo")).header("X-Forwarded-Host", "demo.localhost:3000"))
                .andExpect(status().isOk());
        mvc.perform(get(LIST).with(advisorOf("demo")).header("Host", "Demo.LocalHost:3000"))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenIsRefusedAtAnotherWorkshopsAddress() throws Exception {
        mvc.perform(get(LIST).with(advisorOf("demo")).header("X-Forwarded-Host", "acme.localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:workshop-mismatch"));
        // A token from before phase 4 (no slug) is refused at any workshop address.
        mvc.perform(get(LIST).with(advisorOf(null)).header("X-Forwarded-Host", "demo.localhost"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addressesWithoutAWorkshopAreNotChecked() throws Exception {
        mvc.perform(get(LIST).with(advisorOf("acme"))).andExpect(status().isOk());
        mvc.perform(get(LIST).with(advisorOf("acme")).header("X-Forwarded-Host", "localhost:3000"))
                .andExpect(status().isOk());
    }
}
