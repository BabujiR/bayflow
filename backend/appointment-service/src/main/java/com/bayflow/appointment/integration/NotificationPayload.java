package com.bayflow.appointment.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * Body of notification-service's POST /api/v1/internal/notification-events. eventId (the
 * outbox message id) is the idempotency key, so a retried delivery is stored once there. Only
 * ids and short facts: never notes or contact details.
 */
public record NotificationPayload(UUID eventId, UUID tenantId, UUID locationId, String type, UUID actorUserId,
        boolean actorCustomer, UUID customerUserId, UUID subjectId, String registration, Instant scheduledAt,
        Instant previousScheduledAt) {

    /** What is stored in the outbox; the event id is added when it is sent (the message id). */
    public NotificationPayload withEventId(UUID eventId) {
        return new NotificationPayload(eventId, tenantId, locationId, type, actorUserId, actorCustomer,
                customerUserId, subjectId, registration, scheduledAt, previousScheduledAt);
    }
}
