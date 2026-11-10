package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.UUID;

import com.bayflow.appointment.domain.AppointmentStatus;

/** Optional filters for listing appointments; null means "any". {@code to} is exclusive. */
public record AppointmentFilter(UUID customerId, UUID vehicleId, AppointmentStatus status, Instant from,
        Instant to) {
}
