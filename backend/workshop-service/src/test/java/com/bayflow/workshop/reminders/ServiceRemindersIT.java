package com.bayflow.workshop.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.security.TenantContext;
import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;
import com.jayway.jsonpath.JsonPath;

/**
 * Customer reminders: the daily pass (once per due date, one per vehicle), Send reminder, the
 * customer's opt-out and the shop's switch. Events stay in the outbox (nothing is delivered).
 */
@TestPropertySource(properties = "bayflow.workshop.notifications.enabled=true")
class ServiceRemindersIT extends IntegrationTestBase {

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");

    @Autowired
    private ReminderService reminders;

    private UUID tenant;
    private UUID customerId;
    private RequestPostProcessor admin;
    private RequestPostProcessor advisor;
    private RequestPostProcessor mechanic;
    private RequestPostProcessor customer;

    @BeforeEach
    void setUp() {
        tenant = UUID.randomUUID();
        customerId = UUID.randomUUID();
        admin = as("ADMIN", UUID.randomUUID().toString(), "owner", tenant);
        advisor = as("SERVICE_ADVISOR", UUID.randomUUID().toString(), "sam", tenant);
        mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mike", tenant);
        customer = as("CUSTOMER", customerId.toString(), "ana", tenant);
        Mockito.doReturn(new WorkshopSettings(new BigDecimal("100.00"), "USD"))
                .when(workshopSettings).get(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void theDailyPassRemindsEachVehicleOnceListingWhatIsDue() throws Exception {
        UUID vehicle = UUID.randomUUID();
        String card = complete(vehicle, "Oil change and tire rotation");
        dueInDays(vehicle, 5);

        assertThat(dailyPass()).isEqualTo(1);
        List<String> payloads = reminderPayloads(card);
        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0)).contains("\"customerUserId\":\"" + customerId + "\"")
                .contains("\"registration\":\"REM 1\"")
                .contains("Oil change, Tire rotation");

        // Reminded once per due date.
        assertThat(dailyPass()).isZero();
        mvc.perform(get("/api/v1/service-due").param("days", "30").with(advisor))
                .andExpect(jsonPath("$[0].remindedAt").isNotEmpty());

        // Staff can send one again by hand.
        mvc.perform(post("/api/v1/service-due/vehicles/" + vehicle + "/remind").with(advisor))
                .andExpect(status().isNoContent());
        assertThat(reminderPayloads(card)).hasSize(2);
        mvc.perform(post("/api/v1/service-due/vehicles/" + vehicle + "/remind").with(mechanic))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersWhoTurnedRemindersOffAndShopsThatDidAreLeftOut() throws Exception {
        mvc.perform(get("/api/v1/service-reminders/me").with(customer)).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(put("/api/v1/service-reminders/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/service-reminders/me").with(customer)).andExpect(jsonPath("$.enabled").value(false));

        UUID vehicle = UUID.randomUUID();
        String card = complete(vehicle, "Oil change");
        dueInDays(vehicle, 3);
        assertThat(dailyPass()).isZero();
        assertThat(reminderPayloads(card)).isEmpty();
        mvc.perform(post("/api/v1/service-due/vehicles/" + vehicle + "/remind").with(advisor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:reminders-off"));

        // Back on, but the shop switched reminders off.
        mvc.perform(put("/api/v1/service-reminders/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/service-reminders/settings").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"remindDaysBefore\":14,\"milesPerDay\":30,\"version\":0}"))
                .andExpect(status().isOk());
        assertThat(dailyPass()).isZero();
        // Nothing due on a vehicle without a record.
        mvc.perform(post("/api/v1/service-due/vehicles/" + UUID.randomUUID() + "/remind").with(advisor))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** The daily pass for this test's organization (as the schedule runs it). */
    private int dailyPass() {
        assertThat(reminders.tenantsToRemind()).isNotNull();
        return TenantContext.call(tenant, reminders::remindDue);
    }

    private void dueInDays(UUID vehicle, int days) {
        ownerJdbc().update("update bayflow.ws_vehicle_services set due_at = now() + make_interval(days => ?) "
                + "where vehicle_id = ?", days, vehicle);
    }

    private List<String> reminderPayloads(String card) {
        return ownerJdbc().queryForList("select payload from bayflow.ws_outbox where job_card_id = ?::uuid "
                + "and payload like '%SERVICE_DUE%' order by created_at", String.class, card);
    }

    /** A job card for the vehicle taken to COMPLETED with one approved recommendation. */
    private String complete(UUID vehicle, String work) throws Exception {
        String card = JsonPath.read(body(mvc.perform(post("/api/v1/job-cards").with(system)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352","registration":"REM 1",
                                 "customerUserId":"%s","customerName":"Ana Silva","mileage":30000,"tenantId":"%s"}
                                """.formatted(UUID.randomUUID(), vehicle, customerId, tenant)))
                .andExpect(status().isCreated())), "$.id");
        act(mechanic, card, "findings", "{\"description\":\"Due for service\",\"severity\":\"LOW\"}");
        String item = JsonPath.read(body(act(advisor, card, "recommendations",
                "{\"description\":\"%s\",\"estimatedPartsCost\":40,\"estimatedLabourHours\":0.5}".formatted(work))),
                "$.recommendations[0].id");
        act(advisor, card, "submit-for-approval", null);
        act(customer, card, "recommendations/" + item + "/approve", null);
        act(mechanic, card, "start", null);
        act(mechanic, card, "work-logs", "{\"recommendationId\":\"%s\",\"description\":\"Done\"}".formatted(item));
        act(mechanic, card, "complete", null);
        return card;
    }

    private ResultActions act(RequestPostProcessor who, String card, String action, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/job-cards/" + card + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
