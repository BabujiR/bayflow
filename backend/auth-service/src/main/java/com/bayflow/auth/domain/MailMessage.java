package com.bayflow.auth.domain;

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
 * An e-mail waiting to be sent (transactional outbox): saved in the same transaction as the
 * change that caused it, delivered right after, retried with exponential backoff. Its bodies
 * are cleared once it is sent or dropped, so a reset link never stays in the database.
 */
@Entity
@Table(name = "auth_mail_outbox")
public class MailMessage {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(name = "sender_name", nullable = false, length = 200)
    private String senderName;

    @Column(name = "reply_to", length = 254)
    private String replyTo;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(name = "text_body", nullable = false, columnDefinition = "text")
    private String textBody;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

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

    protected MailMessage() {
        // for JPA
    }

    public static MailMessage create(String recipient, String senderName, String replyTo, String subject,
            String textBody, String htmlBody, Instant now) {
        MailMessage message = new MailMessage();
        message.id = UUID.randomUUID();
        message.recipient = recipient;
        message.senderName = truncate(senderName, 200);
        message.replyTo = replyTo;
        message.subject = truncate(subject, 300);
        message.textBody = textBody;
        message.htmlBody = htmlBody;
        message.nextAttemptAt = now;
        message.createdAt = now;
        return message;
    }

    public void markSent(Instant now) {
        attempts++;
        processedAt = now;
        lastError = null;
        clearBodies();
    }

    /** Another try later (initial × 2^(attempts − 1), at most max), or dropped once too old. */
    public void markFailed(String error, Instant now, Duration initialBackoff, Duration maxBackoff,
            Duration giveUpAfter) {
        attempts++;
        lastError = truncate(error, 1000);
        if (!createdAt.plus(giveUpAfter).isAfter(now)) {
            processedAt = now;
            clearBodies();
            return;
        }
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = initialBackoff.multipliedBy(1L << exponent);
        nextAttemptAt = now.plus(delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay);
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    private void clearBodies() {
        textBody = "";
        htmlBody = "";
    }

    private static String truncate(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRecipient() { return recipient; }
    public String getSenderName() { return senderName; }
    public String getReplyTo() { return replyTo; }
    public String getSubject() { return subject; }
    public String getTextBody() { return textBody; }
    public String getHtmlBody() { return htmlBody; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
