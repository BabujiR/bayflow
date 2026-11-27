package com.bayflow.appointment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    /**
     * A quick estimate: its job card (the estimate) exists, the visit has no time yet. Holds no
     * slot and stays off the day board; "schedule the work" makes it BOOKED.
     */
    ESTIMATE,
    BOOKED,
    CONFIRMED,
    ARRIVED,
    CHECKED_IN,
    CANCELLED,
    NO_SHOW;

    /** Statuses that occupy the vehicle's time slot (used for the double-booking rule). */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(BOOKED, CONFIRMED, ARRIVED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isFinal() {
        return this == CHECKED_IN || this == CANCELLED || this == NO_SHOW;
    }
}
