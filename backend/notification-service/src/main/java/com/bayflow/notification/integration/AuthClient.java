package com.bayflow.notification.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.http.ServiceHttp;
import com.bayflow.http.SystemTokens;
import com.bayflow.notification.config.NotificationProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Asks auth-service (with a SYSTEM token) who works at a location and what an organization's
 * locations are called and which time zone they are in. Answers are kept for a short while.
 */
@Component
public class AuthClient {

    /** One location of an organization. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationInfo(UUID id, String name, boolean active, String timeZone) {
    }

    private record Cached<T>(T value, Instant fetchedAt) {
    }

    private record StaffKey(UUID tenantId, UUID locationId, String roles) {
    }

    private final RestClient auth;
    private final SystemTokens tokens;
    private final NotificationProperties properties;
    private final Clock clock;
    private final Map<StaffKey, Cached<List<UUID>>> staff = new ConcurrentHashMap<>();
    private final Map<UUID, Cached<List<LocationInfo>>> locations = new ConcurrentHashMap<>();

    public AuthClient(NotificationProperties properties, Clock clock) {
        this.auth = ServiceHttp.internal(properties.getAuthUrl(), properties.getConnectTimeout(),
                properties.getReadTimeout());
        this.tokens = new SystemTokens(auth, properties.getClientId(), properties.getClientSecret(), clock);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The enabled users with one of {@code roles} who work at the location (never ADMIN or
     * CUSTOMER: those have no location). Throws when auth-service can't be reached.
     */
    public List<UUID> staffAt(UUID tenantId, UUID locationId, Collection<String> roles) {
        String roleList = String.join(",", roles.stream().sorted().toList());
        StaffKey key = new StaffKey(tenantId, locationId, roleList);
        return cached(staff, key, properties.getStaffCacheTtl(), () -> {
            UUID[] ids = withToken(() -> auth.get()
                    .uri(b -> b.path("/api/v1/auth/internal/tenants/{tenant}/locations/{location}/staff")
                            .queryParam("roles", roleList)
                            .build(tenantId, locationId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .retrieve()
                    .body(UUID[].class));
            return ids == null ? List.of() : List.copyOf(Arrays.asList(ids));
        });
    }

    /** The organization's locations (archived ones too). Throws when auth-service can't be reached. */
    public List<LocationInfo> locations(UUID tenantId) {
        return cached(locations, tenantId, properties.getLocationsCacheTtl(), () -> {
            LocationInfo[] list = withToken(() -> auth.get()
                    .uri("/api/v1/auth/internal/tenants/{tenant}/locations", tenantId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .retrieve()
                    .body(LocationInfo[].class));
            return list == null ? List.of() : List.copyOf(Arrays.asList(list));
        });
    }

    /* Forgets every cached answer (tests). */
    public void evictAll() {
        staff.clear();
        locations.clear();
    }

    private <K, T> T cached(Map<K, Cached<T>> cache, K key, Duration ttl, Supplier<T> fetch) {
        Cached<T> hit = cache.get(key);
        Instant now = clock.instant();
        if (hit != null && now.isBefore(hit.fetchedAt().plus(ttl))) {
            return hit.value();
        }
        T fresh = fetch.get();
        cache.put(key, new Cached<>(fresh, now));
        return fresh;
    }

    /** A 401 usually means the cached token is stale: get a new one and try once more. */
    private <T> T withToken(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            return call.get();
        }
    }

    private String token() {
        return tokens.token();
    }
}
