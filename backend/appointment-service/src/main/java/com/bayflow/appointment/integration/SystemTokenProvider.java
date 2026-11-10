package com.bayflow.appointment.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Gets SYSTEM access tokens from auth-service with the client-credentials grant and caches
 * each one until shortly before it expires.
 */
@Component
public class SystemTokenProvider {

    /** Fetch a new token this long before the cached one expires. */
    static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    public record TokenRequest(String clientId, String clientSecret) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(String accessToken, Instant expiresAt) {
    }

    private final RestClient auth;
    private final IntegrationProperties properties;
    private final Clock clock;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public SystemTokenProvider(IntegrationProperties properties, Clock clock) {
        this.auth = HttpClients.create(properties.getAuthUrl(), properties);
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
}
