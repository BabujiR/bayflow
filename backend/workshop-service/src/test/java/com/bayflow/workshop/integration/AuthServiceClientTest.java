package com.bayflow.workshop.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.integration.AuthServiceClient.Mail;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/** The workshop's and locations' details are kept for a few minutes; e-mail is never kept back. */
class AuthServiceClientTest {

    static final WireMockServer AUTH = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-28T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final UUID tenant = UUID.randomUUID();
    final UUID location = UUID.randomUUID();
    final String locationUrl = "/api/v1/auth/internal/tenants/" + tenant + "/locations/" + location + "/settings";
    final TestClock clock = new TestClock();
    AuthServiceClient client;

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
        AUTH.stubFor(post(urlEqualTo("/api/v1/auth/token")).willReturn(okJson(
                "{\"accessToken\":\"t1\",\"expiresAt\":\"2026-11-28T14:00:00Z\"}")));
        AUTH.stubFor(post(urlPathMatching("/api/v1/auth/internal/tenants/.*/mail"))
                .willReturn(aResponse().withStatus(202)));
        WorkshopProperties properties = new WorkshopProperties();
        properties.setAuthUrl(AUTH.baseUrl());
        properties.setSettingsCacheTtl(Duration.ofMinutes(5));
        client = new AuthServiceClient(properties, new SystemTokenProvider(properties, clock), clock);
    }

    @Test
    void aLocationIsAskedForOnceEveryFewMinutes() {
        AUTH.stubFor(get(urlEqualTo(locationUrl)).willReturn(okJson("""
                {"name":"Main","timeZone":"America/New_York","reviews":{"enabled":true,
                 "url":"https://g.page/r/main/review","delay":"TWO_HOURS"},"billing":{}}
                """)));

        assertThat(client.location(tenant, location).reviewsOrOff().url()).isEqualTo("https://g.page/r/main/review");
        assertThat(client.location(tenant, location).name()).isEqualTo("Main");
        AUTH.verify(1, getRequestedFor(urlEqualTo(locationUrl)));

        clock.now = clock.now.plus(Duration.ofMinutes(6));
        client.location(tenant, location);
        AUTH.verify(2, getRequestedFor(urlEqualTo(locationUrl)));
    }

    @Test
    void aFailureIsNotKept() {
        AUTH.stubFor(get(urlEqualTo(locationUrl)).inScenario("down").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
        AUTH.stubFor(get(urlEqualTo(locationUrl)).inScenario("down").whenScenarioStateIs("up")
                .willReturn(okJson("{\"name\":\"Main\"}")));

        assertThatThrownBy(() -> client.location(tenant, location)).isInstanceOf(RuntimeException.class);
        assertThat(client.location(tenant, location).name()).isEqualTo("Main");
    }

    @Test
    void mailIsAlwaysSent() {
        Mail mail = new Mail("pat@example.com", "Lotus Auto", null, "Hi", "Hi", "<p>Hi</p>");
        client.send(tenant, mail);
        client.send(tenant, mail);
        AUTH.verify(2, postRequestedFor(urlPathMatching("/api/v1/auth/internal/tenants/.*/mail")));
        // One token for all of it.
        AUTH.verify(1, postRequestedFor(urlEqualTo("/api/v1/auth/token")));
    }
}
