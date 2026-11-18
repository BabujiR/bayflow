package com.bayflow.appointment.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bayflow.appointment.MutableClock;
import com.bayflow.appointment.config.IntegrationProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/** Reading an organization's locations from auth-service: SYSTEM token, caching, a stale token. */
class LocationsClientTest {

    static final WireMockServer AUTH = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String TOKEN = "/api/v1/auth/token";

    final UUID tenant = UUID.randomUUID();
    final String url = "/api/v1/auth/internal/tenants/" + tenant + "/locations";
    final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T14:00:00Z"));
    LocationsClient client;

    @BeforeAll
    static void start() {
        AUTH.start();
    }

    @AfterAll
    static void stop() {
        AUTH.stop();
    }

    @BeforeEach
    void setUp() {
        AUTH.resetAll();
        AUTH.stubFor(post(urlEqualTo(TOKEN)).willReturn(okJson(
                "{\"accessToken\":\"t1\",\"expiresAt\":\"2026-11-16T15:00:00Z\"}")));
        IntegrationProperties properties = new IntegrationProperties();
        properties.setAuthUrl(AUTH.baseUrl());
        properties.setClientSecret("secret");
        properties.setSettingsCacheTtl(Duration.ofMinutes(5));
        client = new LocationsClient(properties, new SystemTokenProvider(properties, clock), clock);
    }

    private String body(String zone) {
        return """
                [{"id":"%s","code":"main","name":"Main","active":true,"timeZone":"%s","labourRate":95.00,
                  "currency":"USD","unknownField":1},
                 {"id":"%s","code":"north","name":"North","active":false,"timeZone":"Pacific/Auckland",
                  "labourRate":120.00,"currency":"USD"}]
                """.formatted(tenant, zone, UUID.randomUUID());
    }

    @Test
    void readsWithASystemTokenAndCachesPerOrganization() {
        AUTH.stubFor(get(urlEqualTo(url)).willReturn(okJson(body("America/Chicago"))));

        assertThat(client.locations(tenant)).hasSize(2);
        assertThat(client.locations(tenant).get(0).timeZone()).isEqualTo("America/Chicago");
        assertThat(client.locations(tenant).get(1).active()).isFalse();
        AUTH.verify(1, getRequestedFor(urlEqualTo(url)).withHeader("Authorization", equalTo("Bearer t1")));

        clock.advance(Duration.ofMinutes(6));
        client.locations(tenant);
        AUTH.verify(2, getRequestedFor(urlEqualTo(url)));
        AUTH.verify(1, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void aRejectedTokenIsReplacedOnce() {
        AUTH.stubFor(get(urlEqualTo(url)).inScenario("stale").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401)).willSetStateTo("fresh"));
        AUTH.stubFor(get(urlEqualTo(url)).inScenario("stale").whenScenarioStateIs("fresh")
                .willReturn(okJson(body("Europe/London"))));

        assertThat(client.locations(tenant).get(0).timeZone()).isEqualTo("Europe/London");
        AUTH.verify(2, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void failuresAndEmptyAnswersAreNotCached() {
        AUTH.stubFor(get(urlEqualTo(url)).inScenario("down").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("empty"));
        AUTH.stubFor(get(urlEqualTo(url)).inScenario("down").whenScenarioStateIs("empty")
                .willReturn(okJson("[]")).willSetStateTo("up"));
        AUTH.stubFor(get(urlEqualTo(url)).inScenario("down").whenScenarioStateIs("up")
                .willReturn(okJson(body("Asia/Kolkata"))));

        assertThatThrownBy(() -> client.locations(tenant)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> client.locations(tenant)).isInstanceOf(IllegalStateException.class);
        assertThat(client.locations(tenant).get(0).timeZone()).isEqualTo("Asia/Kolkata");
    }
}
