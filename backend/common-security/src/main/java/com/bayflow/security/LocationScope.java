package com.bayflow.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The locations (shops) of the current organization that a caller may work with: all of them,
 * or a set. Row-level security gets it as {@code app.locations} ({@code *} or a comma-separated
 * list of ids); an empty set means nothing, so a missing scope fails closed.
 */
public record LocationScope(boolean all, Set<UUID> ids) {

    /** Every location of the organization. */
    public static final LocationScope ALL = new LocationScope(true, Set.of());
    /** No location at all: reads find nothing on location-bound tables, writes fail. */
    public static final LocationScope NONE = new LocationScope(false, Set.of());

    /** Most locations a token may list; more is refused when the token is issued. */
    public static final int MAX_LOCATIONS = 200;

    public LocationScope {
        ids = all ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    public static LocationScope of(Collection<UUID> ids) {
        return new LocationScope(false, Set.copyOf(ids));
    }

    public static LocationScope of(UUID id) {
        return new LocationScope(false, Set.of(id));
    }

    /**
     * Reads the {@value Tenants#LOCATIONS_CLAIM} claim: {@code ["*"]} for all, otherwise ids.
     * Empty when the claim is missing or holds anything that is not a location id.
     */
    public static Optional<LocationScope> fromClaim(Object claim) {
        if (!(claim instanceof Collection<?> values) || values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() == 1 && "*".equals(String.valueOf(values.iterator().next()))) {
            return Optional.of(ALL);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object value : values) {
            Optional<UUID> id = Tenants.parse(value);
            if (id.isEmpty()) {
                return Optional.empty();
            }
            ids.add(id.get());
        }
        return Optional.of(of(ids));
    }

    public boolean allows(UUID locationId) {
        return locationId != null && (all || ids.contains(locationId));
    }

    /** One location of this scope (the location switcher); empty if it is outside. */
    public Optional<LocationScope> narrowTo(UUID locationId) {
        return allows(locationId) ? Optional.of(of(locationId)) : Optional.empty();
    }

    /** The only location in a one-location scope; empty for "all" or several. */
    public Optional<UUID> single() {
        return !all && ids.size() == 1 ? Optional.of(ids.iterator().next()) : Optional.empty();
    }

    /** The value for the {@code app.locations} setting. */
    public String asSetting() {
        return all ? "*" : ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }
}
