package com.bayflow.auth.application;

import java.util.List;
import java.util.UUID;

/**
 * Where a user works: every location of the organization ({@code all}, for ADMIN and CUSTOMER
 * accounts) or the listed ones (MANAGER, SERVICE_ADVISOR, MECHANIC).
 */
public record LocationAccess(boolean all, List<UUID> locationIds) {

    public static final LocationAccess ALL = new LocationAccess(true, List.of());

    public LocationAccess {
        locationIds = all ? List.of() : List.copyOf(locationIds);
    }

    /** The {@code lsc} claim: {@code ["*"]} or the location ids. */
    public List<String> claim() {
        return all ? List.of("*") : locationIds.stream().map(UUID::toString).toList();
    }
}
