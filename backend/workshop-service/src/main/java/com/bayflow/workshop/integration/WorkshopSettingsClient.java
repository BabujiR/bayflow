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

import com.bayflow.ops.RequestIds;
import com.bayflow.workshop.config.WorkshopProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Reads a location's effective labour rate and the organization's currency from auth-service
 * (which owns locations and settings) with a SYSTEM token, keeping each answer for a few minutes.
 */
@Component
public class WorkshopSettingsClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkshopSettings(BigDecimal labourRate, String currency, LocationBilling billing) {

        public WorkshopSettings(BigDecimal labourRate, String currency) {
            this(labourRate, currency, LocationBilling.NONE);
        }

        /** No billing settings (an older auth-service): no tax, no fees, due on receipt. */
        public LocationBilling billingOrNone() {
            return billing == null ? LocationBilling.NONE : billing;
        }
    }

    /**
     * A location's tax rates (percent), fees (null = off) and invoice terms, copied onto each
     * invoice when it is created.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationBilling(BigDecimal partsTaxRate, BigDecimal labourTaxRate, BigDecimal feesTaxRate,
            BigDecimal shopSuppliesPercent, BigDecimal shopSuppliesCap, BigDecimal disposalFee,
            Integer paymentTermsDays, String invoiceFooter) {

        public static final LocationBilling NONE = new LocationBilling(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, 0, null);
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
    /** The location is not one of the organization's (auth-service answered 404). */
    public static class UnknownLocationException extends RuntimeException {
        public UnknownLocationException(UUID tenantId, UUID locationId) {
            super("Location " + locationId + " is not a location of organization " + tenantId);
        }
    }

    private record Key(UUID tenantId, UUID locationId) {
    }

    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public WorkshopSettingsClient(WorkshopProperties properties, Clock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.auth = RestClient.builder()
                .requestInterceptor(RequestIds.propagate())
                .baseUrl(properties.getAuthUrl()).requestFactory(factory).build();
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The settings for one location of an organization; throws {@link UnknownLocationException} when
     * it is not the organization's, or another exception when auth-service can't be reached.
     */
    public WorkshopSettings get(UUID tenantId, UUID locationId) {
        Key key = new Key(tenantId, locationId);
        Cached cached = cache.get(key);
        if (cached != null && clock.instant().isBefore(cached.fetchedAt().plus(properties.getSettingsCacheTtl()))) {
            return cached.settings();
        }
        WorkshopSettings fresh;
        try {
            try {
                fresh = fetch(tenantId, locationId);
            } catch (HttpClientErrorException.Unauthorized e) {
                invalidateToken();
                fresh = fetch(tenantId, locationId);
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new UnknownLocationException(tenantId, locationId);
        }
        cache.put(key, new Cached(fresh, clock.instant()));
        return fresh;
    }

    private WorkshopSettings fetch(UUID tenantId, UUID locationId) {
        WorkshopSettings settings = auth.get()
                .uri("/api/v1/auth/internal/tenants/{id}/locations/{location}/settings", tenantId, locationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .retrieve()
                .body(WorkshopSettings.class);
        if (settings == null || settings.labourRate() == null || settings.currency() == null) {
            throw new IllegalStateException("auth-service returned no settings for location " + locationId);
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
