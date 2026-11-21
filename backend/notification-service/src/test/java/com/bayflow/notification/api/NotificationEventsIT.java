package com.bayflow.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import com.bayflow.notification.IntegrationTestBase;
import com.bayflow.notification.integration.AuthClient.LocationInfo;
import com.bayflow.security.Tenants;

/**
 * Events from the other services: who is told, in which words, exactly once. auth-service's
 * answers (who works at the location) are mocked.
 */
class NotificationEventsIT extends IntegrationTestBase {

    private static final UUID TENANT = Tenants.DEFAULT_TENANT_ID;
    private static final UUID MAIN = TENANT;
    private static final String AT = "2026-11-24T15:00:00Z"; // 10:00 in New York

    private final UUID customer = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID mechanic = UUID.randomUUID();

    @BeforeEach
    void staff() {
        ownerJdbc().update("delete from bayflow.ntf_notifications");
        ownerJdbc().update("delete from bayflow.ntf_received_events");
        Mockito.doReturn(List.of(advisor, manager)).when(auth).staffAt(ArgumentMatchers.eq(TENANT),
                ArgumentMatchers.eq(MAIN), ArgumentMatchers.argThat(roles -> roles.contains("SERVICE_ADVISOR")));
        Mockito.doReturn(List.of(mechanic)).when(auth).staffAt(ArgumentMatchers.eq(TENANT),
                ArgumentMatchers.eq(MAIN), ArgumentMatchers.argThat(roles -> roles.contains("MECHANIC")));
    }

    @Test
    void aCustomersBookingTellsTheLocationsDeskButNotTheCustomer() throws Exception {
        send(event("APPOINTMENT_BOOKED", customer, true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.notified").value(2));

        assertThat(recipients()).containsOnlyKeys(advisor, manager);
        inbox(advisor).andExpect(jsonPath("$.content[0].title").value("New booking: AB12 CDE"))
                .andExpect(jsonPath("$.content[0].body").value("Booked for Tue 24 Nov, 10:00."))
                .andExpect(jsonPath("$.content[0].link").value("/appointments"))
                .andExpect(jsonPath("$.content[0].locationId").value(MAIN.toString()));
        inbox(customer).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aBookingByTheWorkshopTellsTheCustomer() throws Exception {
        send(event("APPOINTMENT_BOOKED", advisor, false)).andExpect(status().isOk());
        assertThat(recipients()).containsOnlyKeys(customer);
        inbox(customer).andExpect(jsonPath("$.content[0].title").value("Service booked"));
    }

    @Test
    void aRescheduleSaysWhenItWasAndWhenItIs() throws Exception {
        send(body("APPOINTMENT_RESCHEDULED", customer, true,
                Map.of("previousScheduledAt", "\"2026-11-23T15:00:00Z\""))).andExpect(status().isOk());
        inbox(advisor).andExpect(jsonPath("$.content[0].body")
                .value("Now Tue 24 Nov, 10:00 (was Mon 23 Nov, 10:00)."));
    }

    @Test
    void timesAreInTheLocationsOwnTimeZone() throws Exception {
        UUID north = UUID.randomUUID();
        Mockito.doReturn(List.of(new LocationInfo(MAIN, "Main", true, "America/New_York"),
                new LocationInfo(north, "North", true, "Pacific/Auckland"))).when(auth).locations(TENANT);
        send(body("APPOINTMENT_CONFIRMED", advisor, false, Map.of("locationId", "\"" + north + "\"")))
                .andExpect(status().isOk());
        // 15:00 UTC on 24 Nov is 04:00 on 25 Nov in Auckland.
        inbox(customer).andExpect(jsonPath("$.content[0].body")
                .value("AB12 CDE on Wed 25 Nov, 04:00 is confirmed. See you then!"));
    }

    @Test
    void aCheckInTellsTheCustomerAndTheMechanics() throws Exception {
        send(event("VEHICLE_CHECKED_IN", advisor, false)).andExpect(status().isOk());
        assertThat(recipients()).containsOnlyKeys(customer, mechanic);
        inbox(mechanic).andExpect(jsonPath("$.content[0].title").value("Car checked in: AB12 CDE"))
                .andExpect(jsonPath("$.content[0].link").value("/my-jobs"));
        inbox(customer).andExpect(jsonPath("$.content[0].title").value("Your car is checked in"));
    }

    @Test
    void decisionsTellTheDeskAndMechanicsOnlyWhenThereIsWorkToDo() throws Exception {
        UUID card = UUID.randomUUID();
        send(body("ESTIMATE_DECIDED", customer, true, Map.of("subjectId", "\"" + card + "\"",
                "approvedCount", "2", "declinedCount", "1"))).andExpect(status().isOk());
        assertThat(recipients()).containsOnlyKeys(advisor, manager, mechanic);
        inbox(mechanic).andExpect(jsonPath("$.content[0].body").value("2 items approved and ready to start."))
                .andExpect(jsonPath("$.content[0].link").value("/job-cards/" + card));
        inbox(advisor).andExpect(jsonPath("$.content[0].body").value("2 approved, 1 declined."));

        clear();
        // Everything declined, decided by the advisor on the customer's behalf: no work, and the
        // advisor isn't told about their own action.
        send(body("ESTIMATE_DECIDED", advisor, false, Map.of("approvedCount", "0", "declinedCount", "3")))
                .andExpect(status().isOk());
        assertThat(recipients()).containsOnlyKeys(manager);
    }

    @Test
    void customerEventsWithoutALoginTellNobody() throws Exception {
        send(body("WORK_COMPLETED", advisor, false, Map.of("customerUserId", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notified").value(0));
    }

    @Test
    void aRepeatedDeliveryIsStoredOnce() throws Exception {
        String event = event("ESTIMATE_READY", advisor, false);
        send(event).andExpect(jsonPath("$.duplicate").value(false)).andExpect(jsonPath("$.notified").value(1));
        send(event).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
        assertThat(ownerJdbc().queryForObject("select count(*) from bayflow.ntf_notifications", Long.class))
                .isEqualTo(1);
    }

    @Test
    void whileAuthServiceIsDownTheSenderIsToldToRetry() throws Exception {
        Mockito.doThrow(new ResourceAccessException("auth-service down")).when(auth)
                .staffAt(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        String event = event("APPOINTMENT_BOOKED", customer, true);
        send(event).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:bayflow:problem:recipients-unavailable"));
        assertThat(ownerJdbc().queryForObject("select count(*) from bayflow.ntf_received_events", Long.class))
                .isZero();

        Mockito.doReturn(List.of(advisor)).when(auth)
                .staffAt(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        send(event).andExpect(status().isOk()).andExpect(jsonPath("$.notified").value(1));
    }

    @Test
    void onlyOtherServicesDeliverEvents() throws Exception {
        String event = event("APPOINTMENT_BOOKED", customer, true);
        for (String role : new String[] { "CUSTOMER", "SERVICE_ADVISOR", "ADMIN" }) {
            mvc.perform(post("/api/v1/internal/notification-events").with(user(role, UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/internal/notification-events").contentType(MediaType.APPLICATION_JSON)
                .content(event)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/notification-events").with(system())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPOINTMENT_BOOKED\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private String event(String type, UUID actor, boolean actorCustomer) {
        return body(type, actor, actorCustomer, Map.of());
    }

    private String body(String type, UUID actor, boolean actorCustomer, Map<String, String> overrides) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("eventId", "\"" + UUID.randomUUID() + "\"");
        fields.put("tenantId", "\"" + TENANT + "\"");
        fields.put("locationId", "\"" + MAIN + "\"");
        fields.put("type", "\"" + type + "\"");
        fields.put("actorUserId", "\"" + actor + "\"");
        fields.put("actorCustomer", String.valueOf(actorCustomer));
        fields.put("customerUserId", "\"" + customer + "\"");
        fields.put("subjectId", "\"" + UUID.randomUUID() + "\"");
        fields.put("registration", "\"AB12 CDE\"");
        fields.put("scheduledAt", "\"" + AT + "\"");
        fields.putAll(overrides);
        StringBuilder json = new StringBuilder("{");
        fields.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
        return json.append('}').toString();
    }

    private ResultActions send(String event) throws Exception {
        return mvc.perform(post("/api/v1/internal/notification-events").with(system())
                .contentType(MediaType.APPLICATION_JSON).content(event));
    }

    private ResultActions inbox(UUID user) throws Exception {
        return mvc.perform(get("/api/v1/notifications").with(user("CUSTOMER", user))).andExpect(status().isOk());
    }

    /** Recipient → number of notifications, straight from the database. */
    private Map<UUID, Long> recipients() {
        Map<UUID, Long> counts = new java.util.HashMap<>();
        ownerJdbc().query("select recipient_id, count(*) from bayflow.ntf_notifications group by recipient_id",
                rs -> {
                    counts.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                });
        return counts;
    }

    private void clear() {
        ownerJdbc().update("delete from bayflow.ntf_notifications");
    }
}
