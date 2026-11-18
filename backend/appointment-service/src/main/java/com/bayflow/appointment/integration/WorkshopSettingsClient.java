package com.bayflow.appointment.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Reads a workshop's settings from auth-service (which owns them) with a SYSTEM token, and
 * keeps each workshop's answer for a few minutes so most requests need no call at all.
 */
@Component
public class WorkshopSettingsClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkshopSettings(String timeZone, String currency) {
    }

    private record Cached(WorkshopSettings settings, Instant fetchedAt) {
    }

    private final RestClient auth;
    private final SystemTokenProvider tokens;
    private final Duration ttl;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public WorkshopSettingsClient(IntegrationProperties properties, SystemTokenProvider tokens, Clock clock) {
        this.auth = HttpClients.create(properties.getAuthUrl(), properties);
        this.tokens = tokens;
        this.ttl = properties.getSettingsCacheTtl();
        this.clock = clock;
    }

    /** The workshop's settings; throws when auth-service can't be reached and nothing is cached. */
    public WorkshopSettings get(UUID tenantId) {
        Cached cached = cache.get(tenantId);
        if (cached != null && clock.instant().isBefore(cached.fetchedAt().plus(ttl))) {
            return cached.settings();
        }
        WorkshopSettings fresh;
        try {
            fresh = fetch(tenantId);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            fresh = fetch(tenantId);
        }
        cache.put(tenantId, new Cached(fresh, clock.instant()));
        return fresh;
    }

    /** Forgets cached settings (tests, or after a change is known). */
    public void evictAll() {
        cache.clear();
    }

    private WorkshopSettings fetch(UUID tenantId) {
        WorkshopSettings settings = auth.get()
                .uri("/api/v1/auth/internal/tenants/{id}/settings", tenantId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .retrieve()
                .body(WorkshopSettings.class);
        if (settings == null || settings.timeZone() == null) {
            throw new IllegalStateException("auth-service returned no settings for workshop " + tenantId);
        }
        return settings;
    }
}
