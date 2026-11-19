package com.bayflow.appointment.infrastructure;

import java.util.Locale;

/** How an appointment list is ordered ({@code sort} on GET /api/v1/appointments). */
public enum AppointmentSort {
    /** Not yet finished, soonest first; then past ones, most recent first. */
    UPCOMING,
    /** Appointment time, newest first. */
    LATEST,
    /** Appointment time, oldest first. */
    EARLIEST,
    /** When the booking was made, newest first. */
    RECENT;

    /** A day's list reads in time order; anything wider starts with what is coming up. */
    public static AppointmentSort defaultFor(boolean oneDay) {
        return oneDay ? EARLIEST : UPCOMING;
    }

    /** "upcoming", "latest", "earliest" or "recent" (any case); null for the default. */
    public static AppointmentSort parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("sort must be one of upcoming, latest, earliest, recent.");
        }
    }
}
