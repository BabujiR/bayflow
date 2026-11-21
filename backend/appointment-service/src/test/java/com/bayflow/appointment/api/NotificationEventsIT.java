package com.bayflow.appointment.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.domain.OutboxMessage;
import com.bayflow.appointment.infrastructure.OutboxRepository;
import com.bayflow.appointment.integration.JobCardDispatcher;
import com.bayflow.appointment.integration.SystemTokenProvider;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * Notifications, phase N1: every booking change queues an event for notification-service in
 * the same transaction, and the outbox delivers it, retrying while notification-service is
 * down. WireMock stands in for notification-service and auth-service's token endpoint.
 */
class NotificationEventsIT extends IntegrationTestBase {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("bayflow.integration.auth-url", WIREMOCK::baseUrl);
        registry.add("bayflow.integration.notification-url", WIREMOCK::baseUrl);
        registry.add("bayflow.integration.notifications.enabled", () -> "true");
    }

    private static final String EVENTS = "/api/v1/internal/notification-events";
    private static final Instant TOMORROW_10AM = Instant.parse("2026-11-17T15:00:00Z");

    private final UUID customerId = UUID.randomUUID();
    private final RequestPostProcessor customer = as("CUSTOMER", customerId);
    private final UUID advisorId = UUID.randomUUID();
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", advisorId);

    @Autowired
    private JobCardDispatcher dispatcher;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private SystemTokenProvider tokens;

    @BeforeEach
    void reset() {
        ownerJdbc().update("delete from bayflow.apt_outbox");
        WIREMOCK.resetAll();
        tokens.invalidate();
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/token")).willReturn(json(200,
                "{\"accessToken\":\"system-token\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}")));
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(EVENTS)).willReturn(json(200,
                "{\"duplicate\":false,\"notified\":1}")));
    }

    @Test
    void eachBookingChangeQueuesOneEventWithWhoDidIt() throws Exception {
        String id = book();
        mvc.perform(post("/api/v1/appointments/" + id + "/confirm").with(advisor)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/appointments/" + id + "/reschedule").with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"" + TOMORROW_10AM.plus(Duration.ofHours(2)) + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/cancel").with(customer)).andExpect(status().isOk());

        assertThat(events(id)).extracting(m -> (String) JsonPath.read(m.getPayload(), "$.type"))
                .containsExactly("APPOINTMENT_BOOKED", "APPOINTMENT_CONFIRMED", "APPOINTMENT_RESCHEDULED",
                        "APPOINTMENT_CANCELLED");

        dispatcher.dispatchDue();

        WIREMOCK.verify(4, postRequestedFor(urlEqualTo(EVENTS))
                .withHeader("Authorization", equalTo("Bearer system-token")));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.type", equalTo("APPOINTMENT_BOOKED")))
                .withRequestBody(matchingJsonPath("$.actorUserId", equalTo(customerId.toString())))
                .withRequestBody(matchingJsonPath("$.actorCustomer", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.customerUserId", equalTo(customerId.toString())))
                .withRequestBody(matchingJsonPath("$.subjectId", equalTo(id)))
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(Tenants.DEFAULT_TENANT_ID.toString())))
                .withRequestBody(matchingJsonPath("$.locationId", equalTo(Tenants.DEFAULT_TENANT_ID.toString())))
                .withRequestBody(matchingJsonPath("$.registration", equalTo("NTF 1")))
                .withRequestBody(matchingJsonPath("$.scheduledAt")));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.type", equalTo("APPOINTMENT_CONFIRMED")))
                .withRequestBody(matchingJsonPath("$.actorUserId", equalTo(advisorId.toString())))
                .withRequestBody(matchingJsonPath("$.actorCustomer", equalTo("false"))));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.type", equalTo("APPOINTMENT_RESCHEDULED")))
                .withRequestBody(matchingJsonPath("$.previousScheduledAt")));
        // Each event carries its outbox message id: notification-service stores it once.
        for (OutboxMessage message : events(id)) {
            assertThat(message.isProcessed()).isTrue();
            WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                    .withRequestBody(matchingJsonPath("$.eventId", equalTo(message.getId().toString()))));
        }
    }

    @Test
    void whileNotificationServiceIsDownEventsWaitInTheOutbox() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(EVENTS)).willReturn(aResponse().withStatus(503)));
        String id = book();

        dispatcher.dispatchDue();
        OutboxMessage pending = events(id).get(0);
        assertThat(pending.isProcessed()).isFalse();
        assertThat(pending.getAttempts()).isEqualTo(1);

        WIREMOCK.stubFor(WireMock.post(urlEqualTo(EVENTS)).willReturn(json(200, "{}")));
        clock.advance(Duration.ofSeconds(11));
        dispatcher.dispatchDue();

        assertThat(events(id).get(0).isProcessed()).isTrue();
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.eventId", equalTo(pending.getId().toString()))));
    }

    @Test
    void aRefusedChangeQueuesNothing() throws Exception {
        String id = book();
        // Already confirmed once; confirming again is refused and queues no second event.
        mvc.perform(post("/api/v1/appointments/" + id + "/confirm").with(advisor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/confirm").with(advisor)).andExpect(status().isConflict());
        assertThat(events(id)).hasSize(2);
    }

    // ---- helpers -------------------------------------------------------------------------

    private List<OutboxMessage> events(String appointmentId) {
        return TenantContext.call(Tenants.DEFAULT_TENANT_ID,
                () -> outbox.findByAppointmentId(UUID.fromString(appointmentId))).stream()
                .filter(m -> OutboxMessage.NOTIFY.equals(m.getType()))
                .sorted(java.util.Comparator.comparing(OutboxMessage::getCreatedAt)
                        .thenComparing(m -> (String) JsonPath.read(m.getPayload(), "$.type"),
                                java.util.Comparator.comparingInt(NotificationEventsIT::order)))
                .toList();
    }

    /** The order the test makes its changes in (they can share a clock tick). */
    private static int order(String type) {
        return List.of("APPOINTMENT_BOOKED", "APPOINTMENT_CONFIRMED", "APPOINTMENT_RESCHEDULED",
                "APPOINTMENT_CANCELLED").indexOf(type);
    }

    private String book() throws Exception {
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nia Tifi\",\"email\":\"nia@example.com\"}"))
                .andExpect(status().isCreated());
        String vin = ("JN" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .replaceAll("[IOQ]", "1").substring(0, 17);
        String vehicle = mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vin":"%s","registration":"NTF 1","make":"Nissan","model":"Leaf","year":2021,"mileage":0}
                                """.formatted(vin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String booking = mvc.perform(post("/api/v1/appointments").with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":"%s","scheduledAt":"%s","serviceType":"ROUTINE_SERVICE"}
                                """.formatted((String) JsonPath.read(vehicle, "$.id"), TOMORROW_10AM)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(booking, "$.id");
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }
}
