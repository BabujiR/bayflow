package com.bayflow.workshop.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;
import com.bayflow.workshop.IntegrationTestBase;
import com.bayflow.workshop.domain.OutboxMessage;
import com.bayflow.workshop.infrastructure.OutboxRepository;
import com.bayflow.workshop.integration.NotificationDispatcher;
import com.bayflow.workshop.integration.SystemTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * Notifications, phase N1: the estimate going to the customer, the customer's decision and the
 * finished work each queue one event in the same transaction, delivered through the outbox.
 * WireMock stands in for notification-service and auth-service's token endpoint.
 */
class NotificationEventsIT extends IntegrationTestBase {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("bayflow.workshop.auth-url", WIREMOCK::baseUrl);
        registry.add("bayflow.workshop.notifications.url", WIREMOCK::baseUrl);
        registry.add("bayflow.workshop.notifications.enabled", () -> "true");
    }

    private static final String EVENTS = "/api/v1/internal/notification-events";

    private final RequestPostProcessor system = as("SYSTEM", "appointment-service", "appointment-service");
    private final UUID advisorId = UUID.randomUUID();
    private final RequestPostProcessor advisor = as("SERVICE_ADVISOR", advisorId.toString(), "advisor1");
    private final RequestPostProcessor mechanic = as("MECHANIC", UUID.randomUUID().toString(), "mechanic1");
    private final UUID customerId = UUID.randomUUID();
    private final RequestPostProcessor customer = as("CUSTOMER", customerId.toString(), "customer-n1");

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private SystemTokenProvider tokens;

    @BeforeEach
    void reset() {
        ownerJdbc().update("delete from bayflow.ws_outbox");
        WIREMOCK.resetAll();
        tokens.invalidate();
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/token")).willReturn(json(200,
                "{\"accessToken\":\"system-token\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}")));
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(EVENTS)).willReturn(json(200, "{}")));
    }

    @Test
    void theEstimateTheDecisionAndTheFinishedWorkEachQueueOneEvent() throws Exception {
        String card = opened();
        String body = act(advisor, card, "recommendations",
                "{\"description\":\"Replace front pads\",\"estimatedPartsCost\":90.50,\"estimatedLabourHours\":1}")
                .andReturn().getResponse().getContentAsString();
        String pads = JsonPath.read(body, "$.recommendations[0].id");
        body = act(advisor, card, "recommendations",
                "{\"description\":\"Replace wipers\",\"estimatedPartsCost\":30,\"estimatedLabourHours\":0.25}")
                .andReturn().getResponse().getContentAsString();
        String wipers = JsonPath.read(body, "$.recommendations[1].id");
        act(advisor, card, "submit-for-approval", null).andExpect(status().isOk());

        act(customer, card, "recommendations/" + pads + "/approve", null).andExpect(status().isOk());
        assertThat(types(card)).containsExactly("ESTIMATE_READY");
        act(customer, card, "recommendations/" + wipers + "/decline", null).andExpect(status().isOk());

        act(mechanic, card, "start", null).andExpect(status().isOk());
        act(mechanic, card, "work-logs", "{\"recommendationId\":\"" + pads + "\",\"description\":\"Fitted pads\"}")
                .andExpect(status().isOk());
        act(mechanic, card, "labour", "{\"recommendationId\":\"" + pads + "\",\"hours\":1}").andExpect(status().isOk());
        act(advisor, card, "complete", null).andExpect(status().isOk());

        assertThat(types(card)).containsExactlyInAnyOrder("ESTIMATE_READY", "ESTIMATE_DECIDED", "WORK_COMPLETED");

        dispatch(card);

        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                .withHeader("Authorization", equalTo("Bearer system-token"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("ESTIMATE_DECIDED")))
                .withRequestBody(matchingJsonPath("$.actorUserId", equalTo(customerId.toString())))
                .withRequestBody(matchingJsonPath("$.actorCustomer", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.customerUserId", equalTo(customerId.toString())))
                .withRequestBody(matchingJsonPath("$.subjectId", equalTo(card)))
                .withRequestBody(matchingJsonPath("$.locationId", equalTo(Tenants.DEFAULT_TENANT_ID.toString())))
                .withRequestBody(matchingJsonPath("$.registration", equalTo("NTF 42")))
                .withRequestBody(matchingJsonPath("$.approvedCount", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.declinedCount", equalTo("1"))));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.type", equalTo("ESTIMATE_READY")))
                .withRequestBody(matchingJsonPath("$.actorUserId", equalTo(advisorId.toString()))));
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.type", equalTo("WORK_COMPLETED"))));
        assertThat(messages(card)).allSatisfy(m -> assertThat(m.isProcessed()).isTrue());
        // Events never carry prices.
        WIREMOCK.findAll(postRequestedFor(urlEqualTo(EVENTS))).forEach(request ->
                assertThat(request.getBodyAsString()).doesNotContain("90.5", "Cost", "Total"));
    }

    @Test
    void inspectionOnlyTellsNobodyAboutAnEstimate() throws Exception {
        String card = opened();
        act(advisor, card, "submit-for-approval", null).andExpect(status().isOk());
        assertThat(types(card)).isEmpty();
    }

    @Test
    void whileNotificationServiceIsDownEventsWaitInTheOutbox() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo(EVENTS)).willReturn(aResponse().withStatus(503)));
        String card = opened();
        act(advisor, card, "recommendations",
                "{\"description\":\"Replace front pads\",\"estimatedPartsCost\":90.50,\"estimatedLabourHours\":1}");
        act(advisor, card, "submit-for-approval", null).andExpect(status().isOk());

        dispatch(card);
        OutboxMessage pending = messages(card).getFirst();
        assertThat(pending.isProcessed()).isFalse();
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getLastError()).contains("503");

        WIREMOCK.stubFor(WireMock.post(urlEqualTo(EVENTS)).willReturn(json(200, "{}")));
        // A failed event waits out its backoff before anyone sends it again: until then, nothing.
        dispatch(card);
        assertThat(messages(card).getFirst().isProcessed()).isFalse();
        // The backoff has passed.
        ownerJdbc().update("update bayflow.ws_outbox set next_attempt_at = now() - interval '1 second' where id = ?",
                pending.getId());
        dispatch(card);
        assertThat(messages(card).getFirst().isProcessed()).isTrue();
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(matchingJsonPath("$.eventId", equalTo(pending.getId().toString()))));
    }

    // ---- helpers -------------------------------------------------------------------------

    /** Sends the card's pending events now, inside its workshop (as the app does after each change). */
    private void dispatch(String card) {
        TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> dispatcher.dispatchFor(UUID.fromString(card)));
    }

    private List<OutboxMessage> messages(String card) {
        return TenantContext.call(Tenants.DEFAULT_TENANT_ID, () -> outbox.findByJobCardId(UUID.fromString(card)));
    }

    private List<String> types(String card) {
        return messages(card).stream().map(m -> (String) JsonPath.read(m.getPayload(), "$.type")).toList();
    }

    private String opened() throws Exception {
        String body = mvc.perform(post("/api/v1/job-cards").with(system).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appointmentId":"%s","vehicleId":"%s","vin":"1HGCM82633A004352",
                                 "registration":"NTF 42","customerUserId":"%s","customerName":"Nia Tifi",
                                 "mileage":12480,"tenantId":"00000000-0000-0000-0000-000000000001"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), customerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        act(mechanic, id, "findings", "{\"description\":\"Front pads at 2 mm\",\"severity\":\"HIGH\"}")
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions act(RequestPostProcessor who, String card, String action, String json) throws Exception {
        var request = post("/api/v1/job-cards/" + card + "/" + action).with(who);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }
}
