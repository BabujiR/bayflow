package com.bayflow.appointment.integration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
 * Reads an organization's locations and their effective settings (time zone, labour rate,
 * currency) from auth-service, which owns them, with a SYSTEM token. Each organization's answer
 * is kept for a few minutes, so most requests need no call at all.
 */
@Component
public class LocationsClient {

    /** One location with the settings that apply to it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationSettings(UUID id, String code, String name, boolean active, String timeZone,
            BigDecimal labourRate, String currency) {
    }

    private record Cached(List<LocationSettings> locations, Instant fetchedAt) {
    }

    private final RestClient auth;
    private final SystemTokenProvider tokens;
    private final Duration ttl;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public LocationsClient(IntegrationProperties properties, SystemTokenProvider tokens, Clock clock) {
        this.auth = HttpClients.create(properties.getAuthUrl(), properties);
        this.tokens = tokens;
        this.ttl = properties.getSettingsCacheTtl();
        this.clock = clock;
    }

    /** The organization's locations; throws when auth-service can't be reached and nothing is cached. */
    public List<LocationSettings> locations(UUID tenantId) {
        Cached cached = cache.get(tenantId);
        if (cached != null && clock.instant().isBefore(cached.fetchedAt().plus(ttl))) {
            return cached.locations();
        }
        List<LocationSettings> fresh;
        try {
            fresh = fetch(tenantId);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            fresh = fetch(tenantId);
        }
        cache.put(tenantId, new Cached(fresh, clock.instant()));
        return fresh;
    }

    /** Forgets one organization's cached locations, so the next call asks auth-service again. */
    public void evict(UUID tenantId) {
        cache.remove(tenantId);
    }

    /** Forgets cached answers (tests, or after a change is known). */
    public void evictAll() {
        cache.clear();
    }

    private List<LocationSettings> fetch(UUID tenantId) {
        LocationSettings[] body = auth.get()
                .uri("/api/v1/auth/internal/tenants/{id}/locations", tenantId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .retrieve()
                .body(LocationSettings[].class);
        if (body == null || body.length == 0) {
            throw new IllegalStateException("auth-service returned no locations for organization " + tenantId);
        }
        return List.copyOf(Arrays.asList(body));
    }
}
