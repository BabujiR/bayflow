package com.bayflow.appointment.integration;

import java.util.UUID;

/**
 * Body of workshop-service's POST /api/v1/job-cards. appointmentId is the idempotency key.
 * tenantId names the workshop; workshop-service trusts it only from a SYSTEM token.
 */
public record OpenJobCardPayload(UUID appointmentId, UUID vehicleId, String vin, String registration,
        UUID customerUserId, String customerName, int mileage, UUID tenantId) {

    /** Messages queued before multi-tenancy have no tenant in their JSON; it comes from the row. */
    public OpenJobCardPayload withTenantId(UUID tenant) {
        return new OpenJobCardPayload(appointmentId, vehicleId, vin, registration, customerUserId, customerName,
                mileage, tenant);
    }
}
