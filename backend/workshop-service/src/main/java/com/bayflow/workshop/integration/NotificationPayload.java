package com.bayflow.workshop.integration;

import java.util.UUID;

/**
 * Body of notification-service's POST /api/v1/internal/notification-events. eventId (the outbox
 * message id) is the idempotency key. Only ids, the registration and counts: never prices.
 */
public record NotificationPayload(UUID eventId, UUID tenantId, UUID locationId, String type, UUID actorUserId,
        boolean actorCustomer, UUID customerUserId, UUID subjectId, String registration, int approvedCount,
        int declinedCount) {

    /** What is stored in the outbox; the event id is added when it is sent (the message id). */
    public NotificationPayload withEventId(UUID eventId) {
        return new NotificationPayload(eventId, tenantId, locationId, type, actorUserId, actorCustomer,
                customerUserId, subjectId, registration, approvedCount, declinedCount);
    }
}
