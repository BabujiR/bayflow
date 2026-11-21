package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bayflow.appointment.domain.AppointmentStatus;

/**
 * Optional filters for listing appointments; null means "any". {@code to} is exclusive.
 * {@code days}, when not empty, is a day for each location in its own time zone (lists over
 * all locations): an appointment matches when it falls in its location's window. {@code now}
 * splits "upcoming" from past appointments for {@link AppointmentSort#UPCOMING}.
 */
public record AppointmentFilter(UUID customerId, UUID vehicleId, AppointmentStatus status, Instant from,
        Instant to, UUID locationId, List<LocationDay> days, AppointmentSort sort, Instant now) {

    /** One location's day: [from, to) in its own time zone. */
    public record LocationDay(UUID locationId, Instant from, Instant to) {
    }

    public AppointmentFilter {
        days = days == null ? List.of() : List.copyOf(days);
        sort = sort == null ? AppointmentSort.EARLIEST : sort;
    }
}
