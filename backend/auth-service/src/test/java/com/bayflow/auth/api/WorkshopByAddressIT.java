package com.bayflow.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.bayflow.auth.IntegrationTestBase;
import com.bayflow.auth.domain.Platform;
import com.bayflow.security.Tenants;
import com.jayway.jsonpath.JsonPath;

/** Online booking: appointment-service asks which workshop an address names (SYSTEM only). */
class WorkshopByAddressIT extends IntegrationTestBase {

    @Test
    void aServiceFindsTheWorkshopAtAnAddress() throws Exception {
        String token = systemToken();
        mvc.perform(get("/api/v1/auth/internal/workshops/DEMO").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Tenants.DEFAULT_TENANT_ID.toString()))
                .andExpect(jsonPath("$.slug").value("demo"))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/v1/auth/internal/workshops/no-such-shop").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        // The Ramplight console's organization is not a workshop.
        mvc.perform(get("/api/v1/auth/internal/workshops/" + Platform.SLUG).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        // Not for anyone else.
        mvc.perform(get("/api/v1/auth/internal/workshops/demo")).andExpect(status().isUnauthorized());
    }

    private String systemToken() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"appointment-service\",\"clientSecret\":\"test-client-secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
