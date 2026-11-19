package com.bayflow.auth.domain;

/** User roles. SYSTEM is not a user role; it is only granted to service clients. */
public enum Role {
    CUSTOMER,
    SERVICE_ADVISOR,
    MECHANIC,
    /** A location's general manager: everything an advisor may do, at their locations. */
    MANAGER,
    /** Manages user accounts; the organization's owner, at every location. */
    ADMIN;

    /** Staff accounts are created by an ADMIN, never by self-registration. */
    public boolean isStaff() {
        return this != CUSTOMER;
    }

    /** Staff who work at particular locations (ADMIN and CUSTOMER cover every location). */
    public boolean isLocationScoped() {
        return this == MANAGER || this == SERVICE_ADVISOR || this == MECHANIC;
    }
}
