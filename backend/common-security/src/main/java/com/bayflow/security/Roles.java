package com.bayflow.security;

/**
 * Role names used across Bayflow. Tokens carry the plain names in the {@code roles}
 * claim; Spring Security authorities carry them with the {@code ROLE_} prefix.
 */
public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String SERVICE_ADVISOR = "SERVICE_ADVISOR";
    public static final String MECHANIC = "MECHANIC";
    /** Manages accounts; read-only access to workshop data. */
    public static final String ADMIN = "ADMIN";
    /** Service-to-service calls (client credentials). */
    public static final String SYSTEM = "SYSTEM";

    public static final String AUTHORITY_PREFIX = "ROLE_";

    private Roles() {
    }

    public static String authority(String role) {
        return role.startsWith(AUTHORITY_PREFIX) ? role : AUTHORITY_PREFIX + role;
    }
}
