package com.bayflow.appointment.domain;

import static com.bayflow.appointment.domain.AppointmentStatus.ARRIVED;
import static com.bayflow.appointment.domain.AppointmentStatus.BOOKED;
import static com.bayflow.appointment.domain.AppointmentStatus.CONFIRMED;

import java.util.EnumSet;
import java.util.Set;

/**
 * Everything that can happen to an appointment, with the statuses it is allowed from.
 * This table is the appointment state machine; anything not listed is rejected.
 */
public enum AppointmentAction {
    CONFIRM("confirm", EnumSet.of(BOOKED)),
    ARRIVE("mark as arrived", EnumSet.of(CONFIRMED)),
    CHECK_IN("check in", EnumSet.of(ARRIVED)),
    CANCEL("cancel", EnumSet.of(BOOKED, CONFIRMED)),
    NO_SHOW("mark as no-show", EnumSet.of(BOOKED, CONFIRMED)),
    RESCHEDULE("reschedule", EnumSet.of(BOOKED, CONFIRMED));

    private final String description;
    private final Set<AppointmentStatus> allowedFrom;

    AppointmentAction(String description, Set<AppointmentStatus> allowedFrom) {
        this.description = description;
        this.allowedFrom = allowedFrom;
    }

    public boolean isAllowedFrom(AppointmentStatus status) {
        return allowedFrom.contains(status);
    }

    public String description() {
        return description;
    }
}
