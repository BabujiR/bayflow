package com.bayflow.appointment.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.config.AppointmentProperties;
import com.bayflow.appointment.integration.LocationsClient;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.security.LocationContext;
import com.bayflow.security.LocationScope;
import com.bayflow.security.TenantContext;

/**
 * The current organization's locations (shops), from auth-service. Decides which location a
 * booking is for and which time zone defines a location's "today".
 * <p>
 * If auth-service can't be reached, lists keep working with the configured fallback zone and
 * bookings fall back to the organization's first location ("Main", id = tenant id); a booking
 * for any other location then waits until the locations can be checked (503).
 */
@Component
public class Locations {

    private static final Logger log = LoggerFactory.getLogger(Locations.class);

    private final LocationsClient client;
    private final AppointmentProperties properties;

    public Locations(LocationsClient client, AppointmentProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * The location a new booking is for: the one asked for (an active location of this
     * organization that the caller works at, else 404 / 400), or, when none is given, the only one
     * the caller works at or the organization's only active location.
     */
    public UUID forBooking(UUID requested) {
        UUID tenant = TenantContext.require();
        LocationScope scope = LocationContext.current();
        if (requested != null && !scope.allows(requested)) {
            throw ApiException.notFound("Location");
        }
        UUID wanted = requested != null ? requested : scope.single().orElse(null);
        Optional<List<LocationSettings>> known = all(tenant, wanted);
        if (known.isEmpty()) {
            if (wanted == null || wanted.equals(tenant)) {
                return tenant;
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "locations-unavailable", "Service Unavailable",
                    "The location could not be checked. Try again shortly.");
        }
        List<LocationSettings> active = known.get().stream().filter(LocationSettings::active).toList();
        if (wanted == null) {
            List<LocationSettings> mine = mineOf(active, scope);
            if (mine.size() != 1) {
                // The cached list may be stale (a location archived or added since): ask again
                // once before making the caller choose.
                mine = refreshed(tenant).map(list -> mineOf(list, scope)).orElse(mine);
            }
            if (mine.size() == 1) {
                return mine.getFirst().id();
            }
            throw ApiException.badRequest("location-required", "Choose the location for this appointment.");
        }
        return active.stream().filter(l -> l.id().equals(wanted)).findFirst()
                .map(LocationSettings::id)
                .orElseThrow(() -> ApiException.badRequest("invalid-location",
                        "That location does not exist or no longer takes bookings."));
    }

    /**
     * The organization's active locations (the public booking page); 503 when auth-service can't
     * be asked.
     */
    public List<LocationSettings> active() {
        UUID tenant = TenantContext.require();
        return all(tenant).orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "locations-unavailable", "Service Unavailable", "The locations could not be read. Try again shortly."))
                .stream().filter(LocationSettings::active).toList();
    }

    /** A location's time zone as auth-service gives it (the fallback zone when it is invalid). */
    public ZoneId zoneOf(LocationSettings location) {
        return zone(location);
    }

    /**
     * Checks that {@code locationId} is an active location of this organization (400 otherwise;
     * 503 when auth-service can't be asked). For settings that name a location, not bookings.
     */
    public UUID requireActive(UUID locationId) {
        UUID tenant = TenantContext.require();
        List<LocationSettings> known = all(tenant, locationId).orElseThrow(() -> new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "locations-unavailable", "Service Unavailable",
                "The location could not be checked. Try again shortly."));
        return known.stream().filter(LocationSettings::active).filter(l -> l.id().equals(locationId)).findFirst()
                .map(LocationSettings::id)
                .orElseThrow(() -> ApiException.badRequest("invalid-location",
                        "That location does not exist or is archived."));
    }

    /**
     * The location whose "today" a list uses: the one asked for, otherwise the organization's
     * default ("Main" if it still exists, else its first active location).
     */
    public UUID forList(UUID requested) {
        if (requested != null) {
            return requested;
        }
        Optional<UUID> single = LocationContext.current().single();
        if (single.isPresent()) {
            return single.get();
        }
        UUID tenant = TenantContext.require();
        return all(tenant)
                .flatMap(list -> list.stream().filter(l -> l.id().equals(tenant)).findFirst()
                        .or(() -> list.stream().filter(LocationSettings::active).findFirst()))
                .map(LocationSettings::id)
                .orElse(tenant);
    }

    /** The time zone of one of this organization's locations, or the fallback zone. */
    public ZoneId zoneOf(UUID locationId) {
        UUID tenant = TenantContext.require();
        return all(tenant, locationId)
                .flatMap(list -> list.stream().filter(l -> l.id().equals(locationId)).findFirst())
                .map(this::zone)
                .orElse(properties.getWorkshopZone());
    }

    /**
     * Every location's time zone (archived ones too: they keep their bookings), for "a day"
     * across all locations. Empty when the locations can't be read.
     */
    public Map<UUID, ZoneId> zones() {
        UUID tenant = TenantContext.require();
        return all(tenant)
                .map(list -> list.stream().collect(Collectors.toMap(LocationSettings::id, this::zone,
                        (a, b) -> a, LinkedHashMap::new)))
                .<Map<UUID, ZoneId>>map(map -> map)
                .orElse(Map.of());
    }

    private ZoneId zone(LocationSettings location) {
        try {
            return ZoneId.of(location.timeZone());
        } catch (DateTimeException | NullPointerException e) {
            log.warn("Location {} has an invalid time zone; using {}", location.id(), properties.getWorkshopZone());
            return properties.getWorkshopZone();
        }
    }

    private static List<LocationSettings> mineOf(List<LocationSettings> locations, LocationScope scope) {
        return locations.stream().filter(LocationSettings::active).filter(l -> scope.allows(l.id())).toList();
    }

    /** The locations fetched again from auth-service, past the cache. */
    private Optional<List<LocationSettings>> refreshed(UUID tenant) {
        try {
            client.evict(tenant);
            return Optional.of(client.locations(tenant));
        } catch (RuntimeException e) {
            log.warn("Could not read the locations of organization {} ({})", tenant, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<LocationSettings>> all(UUID tenant) {
        return all(tenant, null);
    }

    /**
     * The locations, fetched again once if {@code expected} is not among the cached ones (a location
     * added since): new locations work at once, not only after the cache expires.
     */
    private Optional<List<LocationSettings>> all(UUID tenant, UUID expected) {
        try {
            List<LocationSettings> known = client.locations(tenant);
            if (expected != null && known.stream().noneMatch(l -> l.id().equals(expected))) {
                client.evict(tenant);
                known = client.locations(tenant);
            }
            return Optional.of(known);
        } catch (RuntimeException e) {
            log.warn("Could not read the locations of organization {} ({}); using fallbacks", tenant, e.getMessage());
            return Optional.empty();
        }
    }
}
