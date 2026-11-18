package com.bayflow.auth.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.domain.TenantSettings;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.auth.infrastructure.TenantSettingsRepository;
import com.bayflow.security.TenantContext;

/** The current workshop and its settings. Always works on the workshop in {@link TenantContext}. */
@Service
public class TenantSettingsService {

    /** A workshop with its settings. */
    public record WorkshopView(Tenant tenant, TenantSettings settings) {
    }

    public record SettingsUpdate(String displayName, String phone, String email, String address,
            BigDecimal labourRate, String currency, String timeZone, long version) {
    }

    private final TenantRepository tenants;
    private final TenantSettingsRepository settings;
    private final AuthProperties properties;
    private final Clock clock;

    public TenantSettingsService(TenantRepository tenants, TenantSettingsRepository settings,
            AuthProperties properties, Clock clock) {
        this.tenants = tenants;
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
    }

    /** The current workshop; settings are created from the configured defaults if it has none yet. */
    @Transactional
    public WorkshopView current() {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("Workshop"));
        TenantSettings own = settings.findByTenantId(tenantId).orElseGet(() -> {
            AuthProperties.TenantDefaults defaults = properties.getTenantDefaults();
            return settings.save(TenantSettings.create(tenantId, tenant.getName(), defaults.getLabourRate(),
                    defaults.getCurrency(), defaults.getTimeZone(), clock.instant()));
        });
        return new WorkshopView(tenant, own);
    }

    /** Changes the current workshop's settings; {@code version} must match what the caller last read. */
    @Transactional
    public WorkshopView update(SettingsUpdate update) {
        String currency = update.currency().trim().toUpperCase();
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid-currency", "'" + update.currency() + "' is not a currency code.");
        }
        String timeZone = update.timeZone().trim();
        if (!ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw ApiException.badRequest("invalid-time-zone", "'" + update.timeZone() + "' is not a time zone.");
        }
        WorkshopView view = current();
        TenantSettings own = view.settings();
        if (own.getVersion() != null && own.getVersion() != update.version()) {
            throw ApiException.conflict("concurrent-update",
                    "The settings were changed by someone else. Reload and try again.");
        }
        own.update(update.displayName().trim(), blankToNull(update.phone()), blankToNull(update.email()),
                blankToNull(update.address()), update.labourRate(), currency, timeZone, clock.instant());
        settings.saveAndFlush(own);
        return new WorkshopView(view.tenant(), own);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
