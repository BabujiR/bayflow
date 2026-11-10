package com.bayflow.appointment.integration;

import java.util.UUID;

/** Body of workshop-service's POST /api/v1/job-cards. appointmentId is the idempotency key. */
public record OpenJobCardPayload(UUID appointmentId, UUID vehicleId, String vin, String registration,
        UUID customerUserId, String customerName, int mileage) {
}
