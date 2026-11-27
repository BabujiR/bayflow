package com.bayflow.appointment.integration;

import java.util.UUID;

/**
 * Body of workshop-service's POST /api/v1/job-cards. appointmentId is the idempotency key.
 * tenantId names the organization and locationId its location (shop); workshop-service trusts
 * both only from a SYSTEM token.
 */
public record OpenJobCardPayload(UUID appointmentId, UUID vehicleId, String vin, String registration,
        UUID customerUserId, String customerName, int mileage, UUID tenantId, UUID locationId, Boolean estimate,
        String request, String requestedBy) {

    /** A job card opened at check-in. */
    public OpenJobCardPayload(UUID appointmentId, UUID vehicleId, String vin, String registration,
            UUID customerUserId, String customerName, int mileage, UUID tenantId, UUID locationId) {
        this(appointmentId, vehicleId, vin, registration, customerUserId, customerName, mileage, tenantId,
                locationId, null, null, null);
    }

    /**
     * Fills in what older queued messages lack: the tenant (before multi-tenancy, it comes from the
     * row) and the location (before locations: the organization's "Main", whose id is the tenant id).
     */
    public OpenJobCardPayload withDefaults(UUID rowTenant) {
        UUID tenant = tenantId != null ? tenantId : rowTenant;
        UUID location = locationId != null ? locationId : tenant;
        return new OpenJobCardPayload(appointmentId, vehicleId, vin, registration, customerUserId, customerName,
                mileage, tenant, location, estimate, request, requestedBy);
    }
}
