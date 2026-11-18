package com.bayflow.workshop.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/**
 * Job cards belong to the workshop named when they are opened: SYSTEM callers must name it,
 * users' own tokens decide for them, and other workshops can't see the card or its details.
 */
class MultiTenantJobCardIT extends IntegrationTestBase {

    private static final UUID OTHER = UUID.fromString("0b0b0b0b-0000-4000-8000-000000000002");

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aSystemCallerMustNameTheWorkshop() throws Exception {
        open(system, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:tenant-required"));
    }

    @Test
    void theCardAndItsDetailsBelongToTheNamedWorkshop() throws Exception {
        String id = id(open(system, OTHER).andExpect(status().isCreated()));
        RequestPostProcessor otherAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1", OTHER);

        mvc.perform(post("/api/v1/job-cards/" + id + "/findings").with(otherAdvisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Pads at 2 mm\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select tenant_id from bayflow.ws_job_cards where id = ?::uuid", UUID.class, id))
                .isEqualTo(OTHER);
        assertThat(jdbc.queryForObject("select tenant_id from bayflow.ws_inspection_findings where job_card_id = ?::uuid",
                UUID.class, id)).isEqualTo(OTHER);
    }

    @Test
    void otherWorkshopsCannotSeeTheCard() throws Exception {
        String id = id(open(system, Tenants.DEFAULT_TENANT_ID).andExpect(status().isCreated()));
        RequestPostProcessor hereAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");
        RequestPostProcessor otherAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1", OTHER);

        mvc.perform(get("/api/v1/job-cards/" + id).with(hereAdvisor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/job-cards/" + id).with(otherAdvisor)).andExpect(status().isNotFound());
    }

    @Test
    void anAdvisorAlwaysOpensCardsInTheirOwnWorkshop() throws Exception {
        RequestPostProcessor hereAdvisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");

        // The tenantId in the body is ignored for users: their token decides.
        String id = id(open(hereAdvisor, OTHER).andExpect(status().isCreated()));

        assertThat(jdbc.queryForObject("select tenant_id from bayflow.ws_job_cards where id = ?::uuid", UUID.class, id))
                .isEqualTo(Tenants.DEFAULT_TENANT_ID);
    }

    private ResultActions open(RequestPostProcessor who, UUID tenant) throws Exception {
        String tenantJson = tenant == null ? "" : ",\"tenantId\":\"" + tenant + "\"";
        return mvc.perform(post("/api/v1/job-cards").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"MT 1",
                         "customerUserId":"%s","customerName":"Jane Doe","mileage":1000%s}
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), tenantJson)));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
