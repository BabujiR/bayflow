package com.bayflow.workshop.integration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

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

    private record Cached(WorkshopSettings settings, Instant fetchedAt) {
    }

    private final RestClient auth;
    private final SystemTokenProvider tokens;
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

    public WorkshopSettingsClient(WorkshopProperties properties, SystemTokenProvider tokens, Clock clock) {
        this.auth = HttpClients.create(properties.getAuthUrl(), properties);
        this.tokens = tokens;
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
                tokens.invalidate();
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
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                .retrieve()
                .body(WorkshopSettings.class);
        if (settings == null || settings.labourRate() == null || settings.currency() == null) {
            throw new IllegalStateException("auth-service returned no settings for location " + locationId);
        }
        return settings;
    }
}
