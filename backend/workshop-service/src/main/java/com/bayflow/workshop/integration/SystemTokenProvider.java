package com.bayflow.workshop.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bayflow.workshop.config.WorkshopProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** SYSTEM access tokens from auth-service (client credentials), cached until shortly before they expire. */
@Component
public class SystemTokenProvider {

    static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    record TokenRequest(String clientId, String clientSecret) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, Instant expiresAt) {
    }

    private final RestClient auth;
    private final WorkshopProperties properties;
    private final Clock clock;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public SystemTokenProvider(WorkshopProperties properties, Clock clock) {
        this.auth = RestClient.builder().baseUrl(properties.getAuthUrl()).requestFactory(factory(properties)).build();
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized String token() {
        if (token != null && clock.instant().isBefore(expiresAt.minus(REFRESH_MARGIN))) {
            return token;
        }
        TokenResponse response = auth.post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TokenRequest(properties.getClientId(), properties.getClientSecret()))
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("auth-service returned no access token");
        }
        token = response.accessToken();
        expiresAt = response.expiresAt() != null ? response.expiresAt() : clock.instant().plusSeconds(120);
        return token;
    }

    /** Forget the cached token (e.g. after a 401 because auth-service restarted with a new key). */
    public synchronized void invalidate() {
        token = null;
        expiresAt = Instant.EPOCH;
    }

    static SimpleClientHttpRequestFactory factory(WorkshopProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}
