package com.bayflow.workshop.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.workshop.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/** Quick estimates: a job card opened before the visit, priced and approved, worked on after check-in. */
class EstimateJobCardIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mechanic1");

    @Test
    void anEstimateIsPricedBeforeTheVisitAndWorkedOnAfterCheckIn() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        String body = openEstimate(appointmentId, "Brakes squeal; quote front pads")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.arrivedAt").doesNotExist())
                .andExpect(jsonPath("$.findings[0].description")
                        .value("Customer's request: Brakes squeal; quote front pads"))
                .andExpect(jsonPath("$.findings[0].recordedBy").value("advisor1"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // Desk views: listed with the estimates, not with the cars at the shop; mechanics don't see it.
        mvc.perform(get("/api/v1/job-cards?estimates=true&size=100").with(advisor))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));
        mvc.perform(get("/api/v1/job-cards?estimates=false&size=100").with(advisor))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
        mvc.perform(get("/api/v1/job-cards?size=100").with(mechanic))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));

        String item = JsonPath.read(mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendation("Front pads", 90, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.recommendations[0].id");
        mvc.perform(post("/api/v1/job-cards/" + id + "/submit-for-approval").with(advisor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations/" + item + "/approve").with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Approved, but the car isn't here yet.
        start(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:not-arrived"));

        // Check-in opens the same appointment again: the same card, now arrived with the new reading.
        open(appointmentId, 13_100, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.mileage").value(13_100))
                .andExpect(jsonPath("$.arrivedAt").isNotEmpty())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        start(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        closeEstimate(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:not-an-estimate"));
    }

    @Test
    void anEstimateTheCustomerWontGoAheadWithIsClosed() throws Exception {
        String body = openEstimate(UUID.randomUUID(), "Quote timing belt").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations").with(advisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendation("Timing belt", 300, 4)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/job-cards/" + id + "/complete").with(advisor))
                .andExpect(status().isConflict());
        closeEstimate(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.recommendations[0].approvalStatus").value("DECLINED"));
        // Repeats are harmless; mechanics can't close estimates.
        closeEstimate(id).andExpect(status().isOk());
        mvc.perform(post("/api/v1/job-cards/" + id + "/close-estimate").with(mechanic))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCardOpenedAtCheckInHasArrived() throws Exception {
        open(UUID.randomUUID(), 12_480, false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.arrivedAt").isNotEmpty());
    }

    private ResultActions openEstimate(UUID appointmentId, String request) throws Exception {
        return mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                .content(json(appointmentId, 12_480, true, ",\"request\":\"%s\",\"requestedBy\":\"advisor1\""
                        .formatted(request))));
    }

    private ResultActions open(UUID appointmentId, int mileage, boolean estimate) throws Exception {
        return mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                .content(json(appointmentId, mileage, estimate, "")));
    }

    private static String json(UUID appointmentId, int mileage, boolean estimate, String extra) {
        return """
                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"EST 1",
                 "customerName":"Pat Lee","mileage":%d,"estimate":%s,
                 "tenantId":"00000000-0000-0000-0000-000000000001"%s}
                """.formatted(appointmentId, UUID.randomUUID(), mileage, estimate, extra);
    }

    private static String recommendation(String description, int parts, int hours) {
        return "{\"description\":\"%s\",\"estimatedPartsCost\":%d,\"estimatedLabourHours\":%d}"
                .formatted(description, parts, hours);
    }

    private ResultActions start(String id) throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + id + "/start").with(mechanic));
    }

    private ResultActions closeEstimate(String id) throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + id + "/close-estimate").with(advisor));
    }
}
