package com.bayflow.workshop.integration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.workshop.config.WorkshopProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Reads a workshop's labour rate and currency from auth-service (which owns workshop settings)
 * with a SYSTEM token, keeping each workshop's answer for a few minutes.
 */
@Component
public class WorkshopSettingsClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkshopSettings(BigDecimal labourRate, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, Instant expiresAt) {
    }

    record TokenRequest(String clientId, String clientSecret) {
    }

    private record Cached(WorkshopSettings settings, Instant fetchedAt) {
    }

    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient auth;
    private final WorkshopProperties properties;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public WorkshopSettingsClient(WorkshopProperties properties, Clock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.auth = RestClient.builder().baseUrl(properties.getAuthUrl()).requestFactory(factory).build();
        this.properties = properties;
        this.clock = clock;
    }

    /** The workshop's settings; throws when auth-service can't be reached and nothing is cached. */
    public WorkshopSettings get(UUID tenantId) {
        Cached cached = cache.get(tenantId);
        if (cached != null && clock.instant().isBefore(cached.fetchedAt().plus(properties.getSettingsCacheTtl()))) {
            return cached.settings();
        }
        WorkshopSettings fresh;
        try {
            fresh = fetch(tenantId);
        } catch (HttpClientErrorException.Unauthorized e) {
            invalidateToken();
            fresh = fetch(tenantId);
        }
        cache.put(tenantId, new Cached(fresh, clock.instant()));
        return fresh;
    }

    private WorkshopSettings fetch(UUID tenantId) {
        WorkshopSettings settings = auth.get()
                .uri("/api/v1/auth/internal/tenants/{id}/settings", tenantId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .retrieve()
                .body(WorkshopSettings.class);
        if (settings == null || settings.labourRate() == null || settings.currency() == null) {
            throw new IllegalStateException("auth-service returned no settings for workshop " + tenantId);
        }
        return settings;
    }

    private synchronized String token() {
        if (token != null && clock.instant().isBefore(tokenExpiresAt.minus(TOKEN_REFRESH_MARGIN))) {
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
        tokenExpiresAt = response.expiresAt() != null ? response.expiresAt() : clock.instant().plusSeconds(120);
        return token;
    }

    private synchronized void invalidateToken() {
        token = null;
        tokenExpiresAt = Instant.EPOCH;
    }
}
