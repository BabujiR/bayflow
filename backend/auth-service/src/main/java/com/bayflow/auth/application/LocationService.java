package com.bayflow.auth.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Location;
import com.bayflow.auth.domain.TenantSettings;
import com.bayflow.auth.infrastructure.LocationRepository;
import com.bayflow.security.TenantContext;

/**
 * The current organization's locations and their effective settings: a location's own time
 * zone and labour rate when it sets them, otherwise the organization's. Currency is always the
 * organization's. Works on the organization in {@link TenantContext}.
 */
@Service
public class LocationService {

    /** A location with the settings that apply to it. */
    public record LocationView(Location location, String timeZone, BigDecimal labourRate, String currency) {

        public boolean inheritsTimeZone() { return location.getTimeZone() == null; }

        public boolean inheritsLabourRate() { return location.getLabourRate() == null; }
    }

    private final LocationRepository locations;
    private final TenantSettingsService settings;
    private final Clock clock;

    public LocationService(LocationRepository locations, TenantSettingsService settings, Clock clock) {
        this.locations = locations;
        this.settings = settings;
        this.clock = clock;
    }

    /** Every location of the organization, by name; creates "Main" if it has none yet. */
    @Transactional
    public List<LocationView> all() {
        TenantSettings org = settings.current().settings();
        ensureMain();
        return locations.findAllByOrderByNameAsc().stream().map(l -> view(l, org)).toList();
    }

    /** One location of the organization with its effective settings; 404 for any other. */
    @Transactional
    public LocationView get(UUID locationId) {
        TenantSettings org = settings.current().settings();
        ensureMain();
        Location location = locations.findById(locationId).orElseThrow(() -> ApiException.notFound("Location"));
        return view(location, org);
    }

    /**
     * Makes sure the organization has its first location, "Main" (id = tenant id). Sign-up and
     * the dev seeding call it; existing organizations got theirs from the V7 migration.
     */
    @Transactional
    public Location ensureMain() {
        UUID tenantId = TenantContext.require();
        return locations.findById(tenantId)
                .or(() -> locations.findAllByOrderByNameAsc().stream().findFirst())
                .orElseGet(() -> locations.saveAndFlush(Location.main(tenantId, clock.instant())));
    }

    private static LocationView view(Location location, TenantSettings org) {
        return new LocationView(location,
                location.getTimeZone() != null ? location.getTimeZone() : org.getTimeZone(),
                location.getLabourRate() != null ? location.getLabourRate() : org.getLabourRate(),
                org.getCurrency());
    }
}
