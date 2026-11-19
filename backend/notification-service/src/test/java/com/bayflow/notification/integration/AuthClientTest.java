package com.bayflow.notification.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bayflow.notification.config.NotificationProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/** How notification-service asks auth-service who works at a location. */
class AuthClientTest {

    private final WireMockServer wiremock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private AuthClient client;

    private final UUID tenant = UUID.randomUUID();
    private final UUID location = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @BeforeEach
    void start() {
        wiremock.start();
        NotificationProperties properties = new NotificationProperties();
        properties.setAuthUrl(wiremock.baseUrl());
        client = new AuthClient(properties, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC));
        wiremock.stubFor(post(urlEqualTo("/api/v1/auth/token")).willReturn(json("""
                {"accessToken":"system-token","expiresAt":"2030-01-01T00:00:00Z"}""")));
    }

    @AfterEach
    void stop() {
        wiremock.stop();
    }

    @Test
    void asksForTheRolesAtTheLocationOnceAndCachesTheAnswer() {
        String path = "/api/v1/auth/internal/tenants/" + tenant + "/locations/" + location + "/staff";
        wiremock.stubFor(get(urlPathEqualTo(path)).willReturn(json("[\"" + advisor + "\"]")));

        assertThat(client.staffAt(tenant, location, List.of("SERVICE_ADVISOR", "MANAGER"))).containsExactly(advisor);
        assertThat(client.staffAt(tenant, location, List.of("MANAGER", "SERVICE_ADVISOR"))).containsExactly(advisor);

        wiremock.verify(1, getRequestedFor(urlPathEqualTo(path))
                .withQueryParam("roles", equalTo("MANAGER,SERVICE_ADVISOR"))
                .withHeader("Authorization", equalTo("Bearer system-token")));
    }

    @Test
    void readsTheLocationsWithTheirTimeZones() {
        wiremock.stubFor(get(urlEqualTo("/api/v1/auth/internal/tenants/" + tenant + "/locations")).willReturn(json(
                "[{\"id\":\"" + location + "\",\"code\":\"north\",\"name\":\"North\",\"active\":true,"
                        + "\"timeZone\":\"Pacific/Auckland\",\"labourRate\":140,\"currency\":\"NZD\"}]")));

        assertThat(client.locations(tenant)).singleElement()
                .satisfies(l -> {
                    assertThat(l.name()).isEqualTo("North");
                    assertThat(l.timeZone()).isEqualTo("Pacific/Auckland");
                });
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
