package com.bayflow.workshop.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * An event for notification-service, saved in the same transaction as the job-card change that
 * caused it (transactional outbox), then delivered and retried with exponential backoff.
 */
@Entity
@Table(name = "ws_outbox")
public class OutboxMessage {

    public static final String NOTIFY = "NOTIFY";

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "job_card_id", nullable = false, updatable = false)
    private UUID jobCardId;

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

    /** Set when retries were given up after too many attempts: no longer tried; an operator looks. */
    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected OutboxMessage() {
        // for JPA
    }

    public static OutboxMessage create(UUID jobCardId, String type, String payload, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.id = UUID.randomUUID();
        message.jobCardId = jobCardId;
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
        markFailed(error, now, initialBackoff, maxBackoff, Integer.MAX_VALUE);
    }

    /**
     * A failed attempt: retried after the backoff, or, from the {@code maxAttempts}th failure on,
     * given up (never tried again). True when it was given up now.
     */
    public boolean markFailed(String error, Instant now, Duration initialBackoff, Duration maxBackoff,
            int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        nextAttemptAt = now.plus(backoff(attempts, initialBackoff, maxBackoff));
        if (attempts >= maxAttempts && failedAt == null) {
            failedAt = now;
            return true;
        }
        return false;
    }

    public boolean isGivenUp() {
        return failedAt != null;
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
    public UUID getTenantId() { return tenantId; }
    public UUID getJobCardId() { return jobCardId; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
