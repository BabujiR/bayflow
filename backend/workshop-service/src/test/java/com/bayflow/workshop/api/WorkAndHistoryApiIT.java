package com.bayflow.workshop.api;

import static org.hamcrest.Matchers.closeTo;
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

/** Starting work, logging work, parts and labour, completion and the service history. */
class WorkAndHistoryApiIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mechanic1");

    /** A job card, its vehicle, its owner and its two recommendation ids. */
    private record Card(String id, String vehicleId, UUID customerId, String pads, String wipers) {
    }

    @Test
    void fullJobFromApprovalToServiceHistory() throws Exception {
        Card card = awaitingApproval();
        RequestPostProcessor customer = customer(card.customerId());
        decide(customer, card, card.pads(), "approve");
        decide(customer, card, card.wipers(), "decline").andExpect(jsonPath("$.status").value("PARTIALLY_APPROVED"));

        act(mechanic, card, "start", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
        act(mechanic, card, "work-logs", """
                {"recommendationId":"%s","description":"Fitted new front pads and bedded them in"}
                """.formatted(card.pads()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workLogs[0].mechanic").value("mechanic1"));
        act(mechanic, card, "parts", """
                {"recommendationId":"%s","partNumber":"BP-220","name":"Front brake pad set","quantity":2,"unitPrice":45.25}
                """.formatted(card.pads()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].lineTotal", closeTo(90.50, 0.001)));
        act(mechanic, card, "labour", """
                {"recommendationId":"%s","hours":1.25}
                """.formatted(card.pads()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labour[0].hourlyRate", closeTo(95.00, 0.001)))
                .andExpect(jsonPath("$.labour[0].cost", closeTo(118.75, 0.001)));

        act(advisor, card, "complete", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.partsTotal", closeTo(90.50, 0.001)))
                .andExpect(jsonPath("$.labourTotal", closeTo(118.75, 0.001)))
                .andExpect(jsonPath("$.grandTotal", closeTo(209.25, 0.001)))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mvc.perform(get("/api/v1/service-history/vehicles/" + card.vehicleId()).with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].jobCardId").value(card.id()))
                .andExpect(jsonPath("$.content[0].mileage").value(12_480))
                .andExpect(jsonPath("$.content[0].totalCost", closeTo(209.25, 0.001)))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].details.findings[0].description").value("Front pads at 2 mm"))
                .andExpect(jsonPath("$.content[0].details.work[0].recommendation").value("Replace front pads"))
                .andExpect(jsonPath("$.content[0].details.work[0].workPerformed[0]")
                        .value("Fitted new front pads and bedded them in"))
                .andExpect(jsonPath("$.content[0].details.work[0].mechanics[0]").value("mechanic1"))
                .andExpect(jsonPath("$.content[0].details.declined[0]").value("Replace wiper blades"))
                .andExpect(jsonPath("$.content[0].details.parts[0].partNumber").value("BP-220"))
                .andExpect(jsonPath("$.content[0].details.labour[0].hours", closeTo(1.25, 0.001)));

        mvc.perform(get("/api/v1/service-history/vehicles/" + card.vehicleId()).with(mechanic))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/service-history/vehicles/" + card.vehicleId()).with(customer(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void workRulesAreEnforced() throws Exception {
        Card card = awaitingApproval();
        RequestPostProcessor customer = customer(card.customerId());

        act(mechanic, card, "start", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-transition"));
        decide(customer, card, card.pads(), "approve");
        decide(customer, card, card.wipers(), "approve");

        act(mechanic, card, "work-logs", workLog(card.pads())).andExpect(status().isConflict());
        act(customer, card, "start", null).andExpect(status().isForbidden());
        act(mechanic, card, "start", null).andExpect(status().isOk());
        act(advisor, card, "work-logs", workLog(card.pads())).andExpect(status().isForbidden());

        act(mechanic, card, "work-logs", workLog(card.pads())).andExpect(status().isOk());
        act(mechanic, card, "complete", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:work-incomplete"));

        act(mechanic, card, "work-logs", workLog(card.wipers())).andExpect(status().isOk());
        act(mechanic, card, "complete", null).andExpect(status().isOk());
        act(mechanic, card, "complete", null).andExpect(status().isConflict());
        act(mechanic, card, "work-logs", workLog(card.pads())).andExpect(status().isConflict());
    }

    @Test
    void workOnADeclinedItemIsRejected() throws Exception {
        Card card = awaitingApproval();
        RequestPostProcessor customer = customer(card.customerId());
        decide(customer, card, card.pads(), "approve");
        decide(customer, card, card.wipers(), "decline");
        act(mechanic, card, "start", null);

        act(mechanic, card, "work-logs", workLog(card.wipers()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:item-not-approved"));
        act(mechanic, card, "labour", """
                {"recommendationId":"%s","hours":1}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:item-not-found"));
    }

    @Test
    void inspectionOnlyJobCompletesWithZeroCost() throws Exception {
        UUID customerId = UUID.randomUUID();
        Card card = opened(customerId);
        act(mechanic, card, "findings", "{\"description\":\"All checks passed\",\"severity\":\"LOW\"}");
        act(advisor, card, "submit-for-approval", null).andExpect(jsonPath("$.status").value("DECLINED"));

        act(advisor, card, "complete", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.grandTotal", closeTo(0.0, 0.001)));

        mvc.perform(get("/api/v1/service-history/vehicles/" + card.vehicleId()).with(customer(customerId)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].totalCost", closeTo(0.0, 0.001)));
    }

    @Test
    void invalidWorkEntriesGetFieldErrors() throws Exception {
        Card card = awaitingApproval();
        decide(customer(card.customerId()), card, card.pads(), "approve");
        decide(customer(card.customerId()), card, card.wipers(), "approve");
        act(mechanic, card, "start", null);

        act(mechanic, card, "labour", """
                {"recommendationId":"%s","hours":0}
                """.formatted(card.pads()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("hours"));
        act(mechanic, card, "parts", """
                {"recommendationId":"%s","partNumber":"X","name":"Y","quantity":0,"unitPrice":1}
                """.formatted(card.pads()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    // ---- helpers ------------------------------------------------------------------------

    private static RequestPostProcessor customer(UUID userId) {
        return as("CUSTOMER", userId.toString(), "customer-" + userId.toString().substring(0, 6));
    }

    private Card opened(UUID customerId) throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        String body = mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352",
                                 "registration":"BFL 1234","customerUserId":"%s","customerName":"Jane Doe",
                                 "mileage":12480}
                                """.formatted(UUID.randomUUID(), vehicleId, customerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Card(JsonPath.read(body, "$.id"), vehicleId, customerId, null, null);
    }

    /** Inspected, two recommendations (pads, wipers), submitted. */
    private Card awaitingApproval() throws Exception {
        Card card = opened(UUID.randomUUID());
        act(mechanic, card, "findings", "{\"description\":\"Front pads at 2 mm\",\"severity\":\"HIGH\"}")
                .andExpect(status().isOk());
        act(advisor, card, "recommendations",
                "{\"description\":\"Replace front pads\",\"estimatedPartsCost\":90.50,\"estimatedLabourHours\":1.25}");
        String body = act(advisor, card, "recommendations",
                "{\"description\":\"Replace wiper blades\",\"estimatedPartsCost\":30,\"estimatedLabourHours\":0.25}")
                .andReturn().getResponse().getContentAsString();
        act(advisor, card, "submit-for-approval", null).andExpect(status().isOk());
        return new Card(card.id(), card.vehicleId(), card.customerId(),
                JsonPath.read(body, "$.recommendations[0].id"), JsonPath.read(body, "$.recommendations[1].id"));
    }

    private ResultActions act(RequestPostProcessor who, Card card, String action, String json) throws Exception {
        var request = post("/api/v1/job-cards/" + card.id() + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private ResultActions decide(RequestPostProcessor who, Card card, String itemId, String decision)
            throws Exception {
        return act(who, card, "recommendations/" + itemId + "/" + decision, null).andExpect(status().isOk());
    }

    private static String workLog(String recommendationId) {
        return "{\"recommendationId\":\"" + recommendationId + "\",\"description\":\"Work done\"}";
    }
}
