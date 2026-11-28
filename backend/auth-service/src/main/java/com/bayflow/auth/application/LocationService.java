package com.bayflow.auth.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Location;
import com.bayflow.auth.domain.LocationBilling;
import com.bayflow.auth.domain.LocationReviews;
import com.bayflow.auth.domain.TenantSettings;
import com.bayflow.auth.infrastructure.LocationRepository;
import com.bayflow.security.TenantContext;

/**
 * The current organization's locations and their effective settings: a location's own time
 * zone and labor rate when it sets them, otherwise the organization's. Currency is always the
 * organization's. Works on the organization in {@link TenantContext}.
 */
@Service
public class LocationService {

    /** A location with the settings that apply to it. */
    public record LocationView(Location location, String timeZone, BigDecimal labourRate, String currency) {

        public boolean inheritsTimeZone() { return location.getTimeZone() == null; }

        public boolean inheritsLabourRate() { return location.getLabourRate() == null; }
    }

    /** What an admin sets on a location; null time zone / labor rate = use the organization's. */
    public record LocationDetails(String code, String name, String phone, String email, String address,
            String timeZone, BigDecimal labourRate) {
    }

    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,18}[a-z0-9]$");

    private final LocationRepository locations;
    private final TenantSettingsService settings;
    private final UpcomingAppointments upcoming;
    private final Clock clock;

    public LocationService(LocationRepository locations, TenantSettingsService settings, UpcomingAppointments upcoming,
            Clock clock) {
        this.locations = locations;
        this.settings = settings;
        this.upcoming = upcoming;
        this.clock = clock;
    }

    /** A new active location of the organization. */
    @Transactional
    public LocationView create(LocationDetails details) {
        TenantSettings org = settings.current().settings();
        ensureMain();
        LocationDetails clean = validate(details, null);
        Location location = Location.create(UUID.randomUUID(), TenantContext.require(), clean.code(), clean.name(),
                clock.instant());
        location.update(clean.code(), clean.name(), clean.phone(), clean.email(), clean.address(), clean.timeZone(),
                clean.labourRate(), clock.instant());
        // Taxes and fees usually match across shops: start from the first location's.
        location.updateBilling(ensureMain().billing(), clock.instant());
        return view(locations.saveAndFlush(location), org);
    }

    /** Changes a location's tax rates, fees and invoice terms (409 if {@code version} is stale). */
    @Transactional
    public LocationView updateBilling(UUID id, LocationBilling billing, long version) {
        TenantSettings org = settings.current().settings();
        Location location = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
        if (location.getVersion() != null && location.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "The location was changed by someone else. Reload and try again.");
        }
        location.updateBilling(billing, clock.instant());
        return view(locations.saveAndFlush(location), org);
    }

    /**
     * Changes a location's Google review requests; {@code version} must match what the caller last
     * read. Asking needs a link (https).
     */
    @Transactional
    public LocationView updateReviews(UUID id, LocationReviews reviews, long version) {
        TenantSettings org = settings.current().settings();
        Location location = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
        if (location.getVersion() != null && location.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "The location was changed by someone else. Reload and try again.");
        }
        String url = reviews.url() == null || reviews.url().isBlank() ? null : reviews.url().strip();
        if (url != null && !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw ApiException.badRequest("invalid-review-url", "The review link must start with https://.");
        }
        if (reviews.enabled() && url == null) {
            throw ApiException.badRequest("review-url-required",
                    "Add the location's Google review link before turning review requests on.");
        }
        location.updateReviews(new LocationReviews(reviews.enabled(), url, reviews.delay()), clock.instant());
        return view(locations.saveAndFlush(location), org);
    }

    /** Changes a location; {@code version} must match what the caller last read (409 otherwise). */
    @Transactional
    public LocationView update(UUID id, LocationDetails details, long version) {
        TenantSettings org = settings.current().settings();
        Location location = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
        if (location.getVersion() != null && location.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "The location was changed by someone else. Reload and try again.");
        }
        LocationDetails clean = validate(details, id);
        location.update(clean.code(), clean.name(), clean.phone(), clean.email(), clean.address(), clean.timeZone(),
                clean.labourRate(), clock.instant());
        return view(locations.saveAndFlush(location), org);
    }

    /**
     * Archives a location: it keeps its history but takes no new bookings or staff. Refused for the
     * organization's last active location (409 last-location) and while it has upcoming
     * appointments (409 location-has-appointments; 503 if that can't be checked).
     */
    @Transactional
    public LocationView archive(UUID id) {
        TenantSettings org = settings.current().settings();
        Location location = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
        if (!location.isActive()) {
            return view(location, org);
        }
        long active = locations.findAllByOrderByNameAsc().stream().filter(Location::isActive).count();
        if (active <= 1) {
            throw ApiException.conflict("last-location", "The organization needs at least one active location.");
        }
        long booked;
        try {
            booked = upcoming.count(TenantContext.require(), id);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "appointments-unavailable",
                    "Service Unavailable", "Upcoming appointments could not be checked. Try again shortly.");
        }
        if (booked > 0) {
            throw ApiException.conflict("location-has-appointments", "This location still has " + booked
                    + " upcoming appointment(s). Cancel or move them first.");
        }
        location.archive(clock.instant());
        return view(locations.saveAndFlush(location), org);
    }

    @Transactional
    public LocationView reactivate(UUID id) {
        TenantSettings org = settings.current().settings();
        Location location = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
        location.reactivate(clock.instant());
        return view(locations.saveAndFlush(location), org);
    }

    private LocationDetails validate(LocationDetails d, UUID self) {
        String code = d.code() == null ? "" : d.code().trim().toLowerCase();
        if (!CODE.matcher(code).matches()) {
            throw ApiException.badRequest("invalid-code",
                    "Use 2-20 lower-case letters, digits and hyphens, starting and ending with a letter or digit.");
        }
        locations.findByCode(code).filter(other -> !other.getId().equals(self)).ifPresent(other -> {
            throw ApiException.conflict("code-taken", "Another location already uses the code '" + code + "'.");
        });
        String zone = blankToNull(d.timeZone());
        return new LocationDetails(code, d.name().trim(), blankToNull(d.phone()), blankToNull(d.email()),
                blankToNull(d.address()), zone == null ? null : TenantSettingsService.timeZone(zone), d.labourRate());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
                .orElseGet(() -> locations.saveAndFlush(Location.mainLocation(tenantId, clock.instant())));
    }

    private static LocationView view(Location location, TenantSettings org) {
        return new LocationView(location,
                location.getTimeZone() != null ? location.getTimeZone() : org.getTimeZone(),
                location.getLabourRate() != null ? location.getLabourRate() : org.getLabourRate(),
                org.getCurrency());
    }
}
