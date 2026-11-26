package com.bayflow.appointment.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bayflow.appointment.config.IntegrationProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Which workshop an address (<slug>.<base domain>) names, from auth-service, for the public booking
 * page. Answers are kept for a minute; an unknown address is remembered as unknown too.
 */
@Component
public class WorkshopDirectory {

    /** The workshop at an address; {@code shownName}: what customers know it as. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Workshop(UUID id, String slug, String name, String displayName, boolean active) {

        public String shownName() {
            return displayName != null && !displayName.isBlank() ? displayName : name;
        }
    }

    private record Cached(Optional<Workshop> workshop, Instant fetchedAt) {
    }

    private static final Duration TTL = Duration.ofMinutes(1);

    private final RestClient auth;
    private final SystemTokenProvider tokens;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public WorkshopDirectory(IntegrationProperties properties, SystemTokenProvider tokens, Clock clock) {
        this.auth = HttpClients.create(properties.getAuthUrl(), properties);
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Empty when no workshop has this address; throws when auth-service can't be reached. */
    public Optional<Workshop> bySlug(String slug) {
        Cached cached = cache.get(slug);
        if (cached != null && clock.instant().isBefore(cached.fetchedAt().plus(TTL))) {
            return cached.workshop();
        }
        Optional<Workshop> found;
        try {
            found = fetch(slug);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokens.invalidate();
            found = fetch(slug);
        }
        cache.put(slug, new Cached(found, clock.instant()));
        return found;
    }

    private Optional<Workshop> fetch(String slug) {
        try {
            return Optional.ofNullable(auth.get()
                    .uri("/api/v1/auth/internal/workshops/{slug}", slug)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                    .retrieve()
                    .body(Workshop.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
