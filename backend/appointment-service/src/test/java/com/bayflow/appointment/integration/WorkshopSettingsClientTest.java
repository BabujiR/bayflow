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

/** Reading a workshop's settings from auth-service: the SYSTEM token, caching and a stale token. */
class WorkshopSettingsClientTest {

    static final WireMockServer AUTH = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String TOKEN = "/api/v1/auth/token";

    final UUID tenant = UUID.randomUUID();
    final String settingsUrl = "/api/v1/auth/internal/tenants/" + tenant + "/settings";
    final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T14:00:00Z"));
    WorkshopSettingsClient client;

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
        client = new WorkshopSettingsClient(properties, new SystemTokenProvider(properties, clock), clock);
    }

    @Test
    void readsWithASystemTokenAndCachesPerWorkshop() {
        AUTH.stubFor(get(urlEqualTo(settingsUrl)).willReturn(okJson(
                "{\"timeZone\":\"Pacific/Auckland\",\"currency\":\"NZD\",\"labourRate\":120.00}")));

        assertThat(client.get(tenant).timeZone()).isEqualTo("Pacific/Auckland");
        assertThat(client.get(tenant).currency()).isEqualTo("NZD");
        AUTH.verify(1, getRequestedFor(urlEqualTo(settingsUrl)).withHeader("Authorization", equalTo("Bearer t1")));

        clock.advance(Duration.ofMinutes(6));
        client.get(tenant);
        AUTH.verify(2, getRequestedFor(urlEqualTo(settingsUrl)));
        AUTH.verify(1, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void aRejectedTokenIsReplacedOnce() {
        AUTH.stubFor(get(urlEqualTo(settingsUrl)).inScenario("stale").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401)).willSetStateTo("fresh"));
        AUTH.stubFor(get(urlEqualTo(settingsUrl)).inScenario("stale").whenScenarioStateIs("fresh")
                .willReturn(okJson("{\"timeZone\":\"Europe/London\",\"currency\":\"GBP\"}")));

        assertThat(client.get(tenant).timeZone()).isEqualTo("Europe/London");
        AUTH.verify(2, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void failuresAreNotCached() {
        AUTH.stubFor(get(urlEqualTo(settingsUrl)).inScenario("down").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
        AUTH.stubFor(get(urlEqualTo(settingsUrl)).inScenario("down").whenScenarioStateIs("up")
                .willReturn(okJson("{\"timeZone\":\"Asia/Kolkata\",\"currency\":\"INR\"}")));

        assertThatThrownBy(() -> client.get(tenant)).isInstanceOf(RuntimeException.class);
        assertThat(client.get(tenant).currency()).isEqualTo("INR");
    }
}
