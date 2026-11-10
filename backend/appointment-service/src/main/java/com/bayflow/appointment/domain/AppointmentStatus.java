package com.bayflow.appointment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
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
