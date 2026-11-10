package com.bayflow.auth.domain;

/** User roles. SYSTEM is not a user role; it is only granted to service clients. */
public enum Role {
    CUSTOMER,
    SERVICE_ADVISOR,
    MECHANIC;

    /** Staff accounts are created by a service advisor, never by self-registration. */
    public boolean isStaff() {
        return this == SERVICE_ADVISOR || this == MECHANIC;
    }
}
