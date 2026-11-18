package com.bayflow.workshop.api;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;
import com.jayway.jsonpath.JsonPath;

/**
 * Per-workshop money (SaaS phase 3): a job card copies the workshop's labour rate and currency
 * when it opens and keeps them, and is not opened at all while they can't be confirmed.
 */
class FrozenMoneyIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mechanic1");

    @Test
    void aCardKeepsTheRateAndCurrencyItOpenedWith() throws Exception {
        charge("120.00", "EUR");
        String id = open(UUID.randomUUID()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.labourRate", closeTo(120.00, 0.001)))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn().getResponse().getContentAsString();
        String cardId = JsonPath.read(id, "$.id");

        // The workshop changes its settings afterwards: the open card is not repriced.
        charge("200.00", "GBP");
        act(mechanic, cardId, "findings", "{\"description\":\"Pads worn\",\"severity\":\"HIGH\"}");
        act(advisor, cardId, "recommendations",
                "{\"description\":\"Replace pads\",\"estimatedPartsCost\":50,\"estimatedLabourHours\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labourRate", closeTo(120.00, 0.001)))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.recommendations[0].estimatedLabourCost", closeTo(120.00, 0.001)))
                .andExpect(jsonPath("$.estimatedTotal", closeTo(170.00, 0.001)));
    }

    @Test
    void labourWithoutARateUsesTheCardsRateAndHistoryKeepsItsCurrency() throws Exception {
        charge("80.00", "NZD");
        UUID owner = UUID.randomUUID();
        String body = open(owner).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String cardId = JsonPath.read(body, "$.id");
        String vehicleId = JsonPath.read(body, "$.vehicleId");
        charge("999.00", "USD");

        act(mechanic, cardId, "findings", "{\"description\":\"Noise\",\"severity\":\"LOW\"}");
        String rec = JsonPath.read(act(advisor, cardId, "recommendations",
                "{\"description\":\"Fix noise\",\"estimatedPartsCost\":0,\"estimatedLabourHours\":0.5}")
                .andReturn().getResponse().getContentAsString(), "$.recommendations[0].id");
        act(advisor, cardId, "submit-for-approval", null).andExpect(status().isOk());
        RequestPostProcessor customer = as("CUSTOMER", owner.toString(), "customer1");
        act(customer, cardId, "recommendations/" + rec + "/approve", null).andExpect(status().isOk());
        act(mechanic, cardId, "start", null).andExpect(status().isOk());
        act(mechanic, cardId, "work-logs", "{\"recommendationId\":\"" + rec + "\",\"description\":\"Done\"}")
                .andExpect(status().isOk());
        act(mechanic, cardId, "labour", "{\"recommendationId\":\"" + rec + "\",\"hours\":0.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labour[0].hourlyRate", closeTo(80.00, 0.001)))
                .andExpect(jsonPath("$.labour[0].cost", closeTo(40.00, 0.001)));
        act(advisor, cardId, "complete", null).andExpect(status().isOk());

        mvc.perform(get("/api/v1/service-history/vehicles/" + vehicleId).with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalCost", closeTo(40.00, 0.001)))
                .andExpect(jsonPath("$.content[0].currency").value("NZD"))
                .andExpect(jsonPath("$.content[0].locationId").value(Tenants.DEFAULT_TENANT_ID.toString()));
    }

    @Test
    void noCardOpensWhileTheWorkshopsSettingsAreUnavailable() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        Mockito.doThrow(new ResourceAccessException("auth-service down"))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());

        open(UUID.randomUUID(), appointmentId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("settings-unavailable")));
        org.assertj.core.api.Assertions.assertThat(ownerJdbc().queryForObject(
                "select count(*) from bayflow.ws_job_cards where appointment_id = ?", Integer.class, appointmentId))
                .isZero();

        // Once settings are back, the outbox's retry opens it.
        charge("95.00", "USD");
        open(UUID.randomUUID(), appointmentId).andExpect(status().isCreated());
    }

    @Test
    void aCardFreezesItsLocationsRateAndKeepsItsLocation() throws Exception {
        UUID north = UUID.randomUUID();
        charge("95.00", "USD");
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("140.00"), "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.eq(north));

        String body = open(UUID.randomUUID(), UUID.randomUUID(), north).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(north.toString()))
                .andExpect(jsonPath("$.labourRate", closeTo(140.00, 0.001)))
                .andReturn().getResponse().getContentAsString();
        String cardId = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/v1/job-cards").with(advisor))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(ownerJdbc().queryForObject(
                "select location_id from bayflow.ws_job_cards where id = ?::uuid", UUID.class, cardId))
                .isEqualTo(north);

        // Without a location: the organization's first location, "Main" (id = tenant id).
        open(UUID.randomUUID()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(Tenants.DEFAULT_TENANT_ID.toString()))
                .andExpect(jsonPath("$.labourRate", closeTo(95.00, 0.001)));
    }

    @Test
    void aLocationOfAnotherOrganizationIsRefused() throws Exception {
        UUID foreign = UUID.randomUUID();
        Mockito.doThrow(new com.bayflow.workshop.integration.WorkshopSettingsClient.UnknownLocationException(
                Tenants.DEFAULT_TENANT_ID, foreign))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.eq(foreign));

        open(UUID.randomUUID(), UUID.randomUUID(), foreign)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-location")));
    }

    private void charge(String rate, String currency) {
        Mockito.doReturn(new WorkshopSettings(new BigDecimal(rate), currency))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private ResultActions open(UUID customerId) throws Exception {
        return open(customerId, UUID.randomUUID());
    }

    private ResultActions open(UUID customerId, UUID appointmentId) throws Exception {
        return open(customerId, appointmentId, null);
    }

    private ResultActions open(UUID customerId, UUID appointmentId, UUID locationId) throws Exception {
        String location = locationId == null ? "" : ",\"locationId\":\"" + locationId + "\"";
        return mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352",
                         "registration":"BFL 1234","customerUserId":"%s","customerName":"Jane Doe",
                         "mileage":12480,"tenantId":"%s"%s}
                        """.formatted(appointmentId, UUID.randomUUID(), customerId, Tenants.DEFAULT_TENANT_ID,
                        location)));
    }

    private ResultActions act(RequestPostProcessor who, String cardId, String action, String json) throws Exception {
        var request = post("/api/v1/job-cards/" + cardId + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }
}
