package com.bayflow.workshop.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
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

class JobCardApiIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "advisor1");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mechanic1");
    private final RequestPostProcessor admin = as("ADMIN", UUID.randomUUID().toString(), "admin1");

    // ---- opening (idempotent) --------------------------------------------------------

    @Test
    void openingIsIdempotentOnTheAppointment() throws Exception {
        UUID appointmentId = UUID.randomUUID();

        String first = open(system, appointmentId, UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.appointmentId").value(appointmentId.toString()))
                .andExpect(jsonPath("$.mileage").value(12_480))
                .andExpect(jsonPath("$.findings").isEmpty())
                .andReturn().getResponse().getContentAsString();

        open(system, appointmentId, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));

        mvc.perform(get("/api/v1/job-cards/by-appointment/" + appointmentId).with(system))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));
    }

    @Test
    void onlyTheSystemAndAdvisorsOpenJobCards() throws Exception {
        open(advisor, UUID.randomUUID(), null).andExpect(status().isCreated());
        open(customer(UUID.randomUUID()), UUID.randomUUID(), null).andExpect(status().isForbidden());
        open(mechanic, UUID.randomUUID(), null).andExpect(status().isForbidden());
        open(admin, UUID.randomUUID(), null).andExpect(status().isForbidden());
    }

    // ---- inspection and approval -----------------------------------------------------

    @Test
    void inspectionRecommendationsAndPartialApproval() throws Exception {
        UUID customerId = UUID.randomUUID();
        RequestPostProcessor customer = customer(customerId);
        String id = openedId(customerId);

        finding(mechanic, id, "Front pads at 2 mm", "HIGH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSPECTED"))
                .andExpect(jsonPath("$.findings[0].recordedBy").value("mechanic1"))
                .andExpect(jsonPath("$.findings[0].severity").value("HIGH"));

        recommend(id, "Replace front pads", "120.50", "1.5").andExpect(status().isOk());
        String body = recommend(id, "Replace wiper blades", "40.00", "0.25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].estimatedLabourCost", closeTo(142.50, 0.001)))
                .andExpect(jsonPath("$.recommendations[0].estimatedTotal", closeTo(263.00, 0.001)))
                .andExpect(jsonPath("$.estimatedTotal", closeTo(326.75, 0.001)))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.recommendations[1].approvalStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String pads = JsonPath.read(body, "$.recommendations[0].id");
        String wipers = JsonPath.read(body, "$.recommendations[1].id");

        submit(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        decide(customer, id, pads, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.recommendations[0].approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.recommendations[0].decidedBy").value("customer-" + customerId.toString().substring(0, 6)));
        decide(customer, id, wipers, "decline")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.approvedEstimate", closeTo(263.00, 0.001)))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void advisorCanRecordTheCustomersDecision() throws Exception {
        String id = openedId(UUID.randomUUID());
        finding(advisor, id, "Oil overdue", "LOW");
        String item = JsonPath.read(recommend(id, "Oil and filter", "45", "0.5")
                .andReturn().getResponse().getContentAsString(), "$.recommendations[0].id");
        submit(id);

        decide(advisor, id, item, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.recommendations[0].decidedBy",
                        containsString("advisor1 (on behalf of the customer)")));
    }

    @Test
    void submittingWithNothingRecommendedIsInspectionOnly() throws Exception {
        String id = openedId(UUID.randomUUID());
        finding(mechanic, id, "All checks passed", "LOW");

        submit(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void stepsOutOfOrderAreRejected() throws Exception {
        UUID customerId = UUID.randomUUID();
        String id = openedId(customerId);

        submit(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-transition"));

        finding(mechanic, id, "Pads worn", "MEDIUM");
        String item = JsonPath.read(recommend(id, "Pads", "100", "1")
                .andReturn().getResponse().getContentAsString(), "$.recommendations[0].id");
        decide(customer(customerId), id, item, "approve").andExpect(status().isConflict());

        submit(id).andExpect(status().isOk());
        finding(mechanic, id, "Late finding", "LOW").andExpect(status().isConflict());
        recommend(id, "Late work", "1", "1").andExpect(status().isConflict());

        decide(customer(customerId), id, item, "approve").andExpect(status().isOk());
        decide(customer(customerId), id, item, "decline").andExpect(status().isConflict());
        decide(customer(customerId), id, UUID.randomUUID().toString(), "approve")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:invalid-transition"));
    }

    @Test
    void decidingTwiceOrOnAnUnknownItemIsReported() throws Exception {
        UUID customerId = UUID.randomUUID();
        String id = openedId(customerId);
        finding(mechanic, id, "Two issues", "MEDIUM");
        recommend(id, "First", "10", "0.5");
        String second = JsonPath.read(recommend(id, "Second", "10", "0.5")
                .andReturn().getResponse().getContentAsString(), "$.recommendations[1].id");
        submit(id);

        decide(customer(customerId), id, second, "approve").andExpect(status().isOk());
        decide(customer(customerId), id, second, "decline").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:item-already-decided"));
        decide(customer(customerId), id, UUID.randomUUID().toString(), "approve")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:item-not-found"));
    }

    // ---- roles and visibility ---------------------------------------------------------

    @Test
    void endpointsEnforceRoles() throws Exception {
        UUID customerId = UUID.randomUUID();
        String id = openedId(customerId);

        finding(customer(customerId), id, "x", "LOW").andExpect(status().isForbidden());
        finding(admin, id, "x", "LOW").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations").with(mechanic)
                        .contentType(MediaType.APPLICATION_JSON).content(recommendationJson("x", "1", "1")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/job-cards/" + id + "/submit-for-approval").with(mechanic))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/job-cards/" + id).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/job-cards/" + id).with(mechanic)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/job-cards/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void customersOnlySeeTheirOwnJobCards() throws Exception {
        UUID aliceId = UUID.randomUUID();
        RequestPostProcessor alice = customer(aliceId);
        RequestPostProcessor bob = customer(UUID.randomUUID());
        String id = openedId(aliceId);
        finding(mechanic, id, "Pads", "HIGH");
        String item = JsonPath.read(recommend(id, "Pads", "100", "1")
                .andReturn().getResponse().getContentAsString(), "$.recommendations[0].id");
        submit(id);

        mvc.perform(get("/api/v1/job-cards/" + id).with(alice)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/job-cards/" + id).with(bob)).andExpect(status().isNotFound());
        decide(bob, id, item, "approve").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/job-cards?size=100").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
        mvc.perform(get("/api/v1/job-cards?status=AWAITING_APPROVAL").with(alice))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void staffCanFilterByStatus() throws Exception {
        String id = openedId(UUID.randomUUID());
        finding(mechanic, id, "Inspected", "LOW");

        mvc.perform(get("/api/v1/job-cards?status=INSPECTED&size=100").with(advisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id)))
                .andExpect(jsonPath("$.content[*].status", not(hasItem("OPEN"))));
    }

    @Test
    void invalidRequestsGetFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("appointmentId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("mileage")));

        String id = openedId(UUID.randomUUID());
        recommend(id, "Bad", "-5", "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("estimatedPartsCost")));
        recommend(id, "Bad", "5", "1000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("estimatedLabourHours")));
        finding(mechanic, id, "Bad severity", "SEVERE").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/job-cards/" + UUID.randomUUID()).with(advisor)).andExpect(status().isNotFound());
    }

    // ---- helpers -------------------------------------------------------------------------

    private static RequestPostProcessor customer(UUID userId) {
        return as("CUSTOMER", userId.toString(), "customer-" + userId.toString().substring(0, 6));
    }

    private ResultActions open(RequestPostProcessor who, UUID appointmentId, UUID customerUserId) throws Exception {
        String customer = customerUserId == null ? "null" : "\"" + customerUserId + "\"";
        return mvc.perform(post("/api/v1/job-cards").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"BFL 1234",
                         "customerUserId":%s,"customerName":"Jane Doe","mileage":12480}
                        """.formatted(appointmentId, UUID.randomUUID(), customer)));
    }

    private String openedId(UUID customerUserId) throws Exception {
        String body = open(system, UUID.randomUUID(), customerUserId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions finding(RequestPostProcessor who, String id, String description, String severity)
            throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + id + "/findings").with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"%s\",\"severity\":\"%s\"}".formatted(description, severity)));
    }

    private ResultActions recommend(String id, String description, String parts, String hours) throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations").with(advisor)
                .contentType(MediaType.APPLICATION_JSON).content(recommendationJson(description, parts, hours)));
    }

    private static String recommendationJson(String description, String parts, String hours) {
        return "{\"description\":\"%s\",\"estimatedPartsCost\":%s,\"estimatedLabourHours\":%s}"
                .formatted(description, parts, hours);
    }

    private ResultActions submit(String id) throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + id + "/submit-for-approval").with(advisor));
    }

    private ResultActions decide(RequestPostProcessor who, String id, String itemId, String decision)
            throws Exception {
        return mvc.perform(post("/api/v1/job-cards/" + id + "/recommendations/" + itemId + "/" + decision).with(who));
    }
}
