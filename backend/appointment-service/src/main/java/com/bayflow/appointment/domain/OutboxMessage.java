package com.bayflow.appointment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A request to another service, saved in the same transaction as the change that caused it
 * (transactional outbox). It is delivered right away and, if that fails, retried with
 * exponential backoff until it succeeds.
 */
@Entity
@Table(name = "apt_outbox")
public class OutboxMessage {

    public static final String CREATE_JOB_CARD = "CREATE_JOB_CARD";

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected OutboxMessage() {
        // for JPA
    }

    public static OutboxMessage create(UUID appointmentId, String type, String payload, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.id = UUID.randomUUID();
        message.appointmentId = appointmentId;
        message.type = type;
        message.payload = payload;
        message.nextAttemptAt = now;
        message.createdAt = now;
        return message;
    }

    public void markDelivered(Instant now) {
        attempts++;
        processedAt = now;
        lastError = null;
    }

    public void markFailed(String error, Instant now, Duration initialBackoff, Duration maxBackoff) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        nextAttemptAt = now.plus(backoff(attempts, initialBackoff, maxBackoff));
    }

    /** initial × 2^(attempts − 1), capped at max. */
    public static Duration backoff(int attempts, Duration initial, Duration max) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = initial.multipliedBy(1L << exponent);
        return delay.compareTo(max) > 0 ? max : delay;
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
