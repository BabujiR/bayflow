package com.bayflow.notification.application;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.bayflow.notification.domain.EventType;

/**
 * Something that happened in appointment-service or workshop-service, as sent from their
 * outboxes. Only ids and short facts: never prices, notes or contact details.
 *
 * @param eventId        the sender's outbox message id; a repeated delivery is stored once
 * @param actorUserId    who caused it (never notified about their own action); null for the system
 * @param actorCustomer  whether the actor is the customer (else staff or the system)
 * @param customerUserId the customer's login, if they have one (walk-ins may not)
 * @param subjectId      the appointment or job card
 * @param scheduledAt    the appointment time (appointment events)
 * @param previousScheduledAt the time before a reschedule
 */
public record NotificationEvent(
        @NotNull UUID eventId,
        @NotNull UUID tenantId,
        UUID locationId,
        @NotNull EventType type,
        UUID actorUserId,
        Boolean actorCustomer,
        UUID customerUserId,
        @NotNull UUID subjectId,
        @Size(max = 20) String registration,
        Instant scheduledAt,
        Instant previousScheduledAt,
        @Min(0) Integer approvedCount,
        @Min(0) Integer declinedCount) {

    /** True when the customer caused it (else staff or the system). */
    public boolean byCustomer() {
        return Boolean.TRUE.equals(actorCustomer);
    }

    public int approved() {
        return approvedCount == null ? 0 : approvedCount;
    }

    public int declined() {
        return declinedCount == null ? 0 : declinedCount;
    }
}
