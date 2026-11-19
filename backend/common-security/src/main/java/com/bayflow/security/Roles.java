package com.bayflow.security;

/**
 * Role names used across Bayflow. Tokens carry the plain names in the {@code roles}
 * claim; Spring Security authorities carry them with the {@code ROLE_} prefix.
 */
public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String SERVICE_ADVISOR = "SERVICE_ADVISOR";
    public static final String MECHANIC = "MECHANIC";
    /**
     * A location's general manager (a regional manager is a MANAGER of several locations).
     * Everything a SERVICE_ADVISOR may do, within their locations.
     */
    public static final String MANAGER = "MANAGER";
    /** Manages accounts; read-only access to workshop data. */
    public static final String ADMIN = "ADMIN";
    /** Service-to-service calls (client credentials). */
    public static final String SYSTEM = "SYSTEM";

    public static final String AUTHORITY_PREFIX = "ROLE_";

    /** Roles that work at particular locations (their tokens list them in the lsc claim). */
    public static final java.util.Set<String> LOCATION_SCOPED = java.util.Set.of(MANAGER, SERVICE_ADVISOR, MECHANIC);

    private Roles() {
    }

    public static String authority(String role) {
        return role.startsWith(AUTHORITY_PREFIX) ? role : AUTHORITY_PREFIX + role;
    }
}
