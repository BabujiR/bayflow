package com.bayflow.auth.application;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Location;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.domain.UserLocation;
import com.bayflow.auth.infrastructure.LocationRepository;
import com.bayflow.auth.infrastructure.UserLocationRepository;
import com.bayflow.auth.infrastructure.UserRepository;
import com.bayflow.security.LocationScope;

/**
 * Which locations staff work at. MANAGER, SERVICE_ADVISOR and MECHANIC accounts need at least
 * one active location of their organization; ADMIN and CUSTOMER accounts cover all of them.
 */
@Service
public class UserLocationService {

    private final UserLocationRepository assignments;
    private final LocationRepository locations;
    private final LocationService locationService;
    private final UserRepository users;
    private final Clock clock;

    public UserLocationService(UserLocationRepository assignments, LocationRepository locations,
            LocationService locationService, UserRepository users, Clock clock) {
        this.assignments = assignments;
        this.locations = locations;
        this.locationService = locationService;
        this.users = users;
        this.clock = clock;
    }

    public static boolean isLocationScoped(User user) {
        return !user.getRoles().contains(Role.ADMIN) && user.getRoles().stream().anyMatch(Role::isLocationScoped);
    }

    /** Where the user works. */
    @Transactional(readOnly = true)
    public LocationAccess accessOf(User user) {
        if (!isLocationScoped(user)) {
            return LocationAccess.ALL;
        }
        return new LocationAccess(false, assignments.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(UserLocation::getLocationId).toList());
    }

    /** Where each of the users works (account lists). */
    @Transactional(readOnly = true)
    public Map<UUID, LocationAccess> accessOf(Collection<User> users) {
        Map<UUID, List<UUID>> byUser = assignments.findByUserIdIn(users.stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(UserLocation::getUserId,
                        Collectors.mapping(UserLocation::getLocationId, Collectors.toList())));
        return users.stream().collect(Collectors.toMap(User::getId, u -> isLocationScoped(u)
                ? new LocationAccess(false, byUser.getOrDefault(u.getId(), List.of()))
                : LocationAccess.ALL));
    }

    /**
     * The access for a new token: every location, or the user's locations. Refused (403) when a
     * location-scoped user has none, or more than a token may carry.
     */
    @Transactional(readOnly = true)
    public LocationAccess forToken(User user) {
        LocationAccess access = accessOf(user);
        if (!access.all() && access.locationIds().isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "no-locations", "Forbidden",
                    "Your account is not assigned to any location yet. Ask your workshop's admin.");
        }
        if (access.locationIds().size() > LocationScope.MAX_LOCATIONS) {
            throw new ApiException(HttpStatus.FORBIDDEN, "too-many-locations", "Forbidden",
                    "Your account is assigned to more than " + LocationScope.MAX_LOCATIONS + " locations.");
        }
        return access;
    }

    /**
     * Sets the locations of a new or existing staff account. {@code requested} null or empty: the
     * organization's only active location, when it has exactly one (400 locations-required
     * otherwise). Users that cover every location take no locations (400 not-location-scoped).
     */
    @Transactional
    public LocationAccess assign(User user, Collection<UUID> requested) {
        boolean none = requested == null || requested.isEmpty();
        if (!isLocationScoped(user)) {
            if (!none) {
                throw ApiException.badRequest("not-location-scoped",
                        "Admins and customers work with every location; they take no location list.");
            }
            return LocationAccess.ALL;
        }
        Set<UUID> wanted = none ? defaultLocation() : new LinkedHashSet<>(requested);
        if (wanted.size() > LocationScope.MAX_LOCATIONS) {
            throw ApiException.badRequest("too-many-locations",
                    "A user can work at most at " + LocationScope.MAX_LOCATIONS + " locations.");
        }
        for (UUID id : wanted) {
            boolean usable = locations.findById(id).map(Location::isActive).orElse(false);
            if (!usable) {
                throw ApiException.badRequest("invalid-location",
                        "That location does not exist or is archived: " + id);
            }
        }
        assignments.deleteByUserId(user.getId());
        var now = clock.instant();
        assignments.saveAllAndFlush(wanted.stream().map(id -> UserLocation.of(user.getId(), id, now)).toList());
        return new LocationAccess(false, List.copyOf(wanted));
    }

    private Set<UUID> defaultLocation() {
        locationService.ensureMain();
        List<Location> active = locations.findAllByOrderByNameAsc().stream().filter(Location::isActive).toList();
        if (active.size() != 1) {
            throw ApiException.badRequest("locations-required",
                    "Choose the locations this person works at.");
        }
        return Set.of(active.get(0).getId());
    }

    /** Ids of the enabled users with one of {@code roles} at the location, in the current workshop. */
    @Transactional(readOnly = true)
    public List<UUID> staffAt(UUID locationId, Collection<Role> roles) {
        return users.findEnabledIdsWithRolesAt(roles, locationId);
    }
}
