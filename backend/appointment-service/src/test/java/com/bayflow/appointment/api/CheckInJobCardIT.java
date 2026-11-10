package com.bayflow.appointment.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bayflow.appointment.IntegrationTestBase;
import com.bayflow.appointment.domain.OutboxMessage;
import com.bayflow.appointment.infrastructure.OutboxRepository;
import com.bayflow.appointment.integration.JobCardDispatcher;
import com.bayflow.appointment.integration.SystemTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;

/**
 * Check-in opens the job card in workshop-service. WireMock stands in for workshop-service
 * and for auth-service's token endpoint.
 */
class CheckInJobCardIT extends IntegrationTestBase {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("bayflow.integration.auth-url", WIREMOCK::baseUrl);
        registry.add("bayflow.integration.workshop-url", WIREMOCK::baseUrl);
    }

    private static final String JOB_CARDS = "/api/v1/job-cards";
    private static final String TOKEN = "/api/v1/auth/token";
    private static final Instant TOMORROW_10AM = Instant.parse("2026-11-17T15:00:00Z");

    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", UUID.randomUUID());

    @Autowired
    private JobCardDispatcher dispatcher;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private SystemTokenProvider tokens;

    @BeforeEach
    void resetRemoteServices() {
        // The database is shared with other test classes, whose check-ins leave undelivered
        // messages behind (they have no workshop-service). Start each test with an empty outbox.
        outbox.deleteAll();
        WIREMOCK.resetAll();
        tokens.invalidate();
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(TOKEN))
                .willReturn(json(200, """
                        {"accessToken":"system-token","tokenType":"Bearer","expiresIn":600,
                         "expiresAt":"2030-01-01T00:00:00Z"}
                        """)));
    }

    @Test
    void checkInOpensExactlyOneJobCard() throws Exception {
        UUID jobCardId = UUID.randomUUID();
        WIREMOCK.stubFor(workshop().willReturn(jobCard(201, jobCardId)));
        String id = arrivedAppointment();

        checkIn(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()));

        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withHeader("Authorization", equalTo("Bearer system-token"))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id)))
                .withRequestBody(matchingJsonPath("$.mileage", equalTo("15000")))
                .withRequestBody(matchingJsonPath("$.customerName", equalTo("Jane Doe")))
                .withRequestBody(matchingJsonPath("$.vin")));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(TOKEN)).withRequestBody(equalToJson(
                "{\"clientId\":\"appointment-service\",\"clientSecret\":\"test-client-secret\"}")));
        assertThat(outbox.findByAppointmentId(UUID.fromString(id)))
                .singleElement()
                .satisfies(m -> assertThat(m.isProcessed()).isTrue());

        // Retrying the check-in is rejected, and a dispatcher run sends nothing more.
        checkIn(id).andExpect(status().isConflict());
        clock.advance(Duration.ofMinutes(10));
        assertThat(dispatcher.dispatchDue()).isZero();
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id))));
    }

    @Test
    void checkInSucceedsWhileWorkshopIsDownAndTheJobCardFollowsOnRetry() throws Exception {
        WIREMOCK.stubFor(workshop().willReturn(aResponse().withStatus(503)));
        String id = arrivedAppointment();

        checkIn(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.jobCardId").doesNotExist());
        OutboxMessage pending = single(id);
        assertThat(pending.isProcessed()).isFalse();
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getLastError()).contains("503");
        assertThat(pending.getNextAttemptAt()).isEqualTo(BASE.plusSeconds(10));

        // Not due yet: nothing is retried before the backoff has passed.
        assertThat(dispatcher.dispatchDue()).isZero();
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id))));

        UUID jobCardId = UUID.randomUUID();
        WIREMOCK.resetMappings();
        resetRemoteServicesTokenOnly();
        WIREMOCK.stubFor(workshop().willReturn(jobCard(201, jobCardId)));
        clock.advance(Duration.ofSeconds(11));
        dispatcher.dispatchDue();

        mvc.perform(get("/api/v1/appointments/" + id).with(advisor))
                .andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()));
        assertThat(single(id).isProcessed()).isTrue();
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id))));
    }

    @Test
    void aLostResponseIsRetriedWithTheSameAppointmentAndLinksOneJobCard() throws Exception {
        UUID jobCardId = UUID.randomUUID();
        // workshop-service created the card but the response never arrived ...
        WIREMOCK.stubFor(workshop().inScenario("lost response").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500)).willSetStateTo("created"));
        // ... so the retry gets the existing card back (200, idempotent on appointmentId).
        WIREMOCK.stubFor(workshop().inScenario("lost response").whenScenarioStateIs("created")
                .willReturn(jobCard(200, jobCardId)));
        String id = arrivedAppointment();

        checkIn(id).andExpect(jsonPath("$.jobCardId").doesNotExist());
        clock.advance(Duration.ofSeconds(11));
        dispatcher.dispatchDue();
        clock.advance(Duration.ofMinutes(10));
        dispatcher.dispatchDue();

        mvc.perform(get("/api/v1/appointments/" + id).with(advisor))
                .andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()));
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(JOB_CARDS))
                .withRequestBody(matchingJsonPath("$.appointmentId", equalTo(id))));
    }

    @Test
    void aRejectedTokenIsRefreshedOnce() throws Exception {
        UUID jobCardId = UUID.randomUUID();
        WIREMOCK.stubFor(workshop().inScenario("stale token").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401)).willSetStateTo("refreshed"));
        WIREMOCK.stubFor(workshop().inScenario("stale token").whenScenarioStateIs("refreshed")
                .willReturn(jobCard(201, jobCardId)));
        String id = arrivedAppointment();

        checkIn(id).andExpect(jsonPath("$.jobCardId").value(jobCardId.toString()));

        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(TOKEN)));
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(JOB_CARDS)));
    }

    // ---- helpers -------------------------------------------------------------------------

    private void resetRemoteServicesTokenOnly() {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(TOKEN)).willReturn(json(200,
                "{\"accessToken\":\"system-token\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}")));
    }

    private static MappingBuilder workshop() {
        return WireMock.post(urlEqualTo(JOB_CARDS));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jobCard(int status, UUID id) {
        return json(status, "{\"id\":\"" + id + "\",\"status\":\"OPEN\",\"mileage\":15000,\"findings\":[]}");
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    private OutboxMessage single(String appointmentId) {
        List<OutboxMessage> messages = outbox.findByAppointmentId(UUID.fromString(appointmentId));
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }

    /** A customer with a vehicle, booked, confirmed and arrived. */
    private String arrivedAppointment() throws Exception {
        RequestPostProcessor customer = as("CUSTOMER", UUID.randomUUID());
        mvc.perform(post("/api/v1/customers/me").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\"}"))
                .andExpect(status().isCreated());
        String vin = ("JT" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .replaceAll("[IOQ]", "1").substring(0, 17);
        String vehicle = mvc.perform(post("/api/v1/vehicles").with(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vin":"%s","registration":"BFL 1","make":"Toyota","model":"Yaris","year":2020,"mileage":0}
                                """.formatted(vin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String booking = mvc.perform(post("/api/v1/appointments").with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":"%s","scheduledAt":"%s","serviceType":"BRAKES"}
                                """.formatted((String) JsonPath.read(vehicle, "$.id"), TOMORROW_10AM)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(booking, "$.id");
        mvc.perform(post("/api/v1/appointments/" + id + "/confirm").with(advisor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/" + id + "/arrive").with(advisor)).andExpect(status().isOk());
        return id;
    }

    private ResultActions checkIn(String id) throws Exception {
        return mvc.perform(post("/api/v1/appointments/" + id + "/check-in").with(advisor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\":15000}"));
    }
}
