package com.bayflow.workshop.estimates;

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
 * A private link to one estimate, e-mailed to the customer: they see it, approve or decline each
 * item and sign, without an account. Sending again replaces the link; it also runs out.
 */
@Entity
@Table(name = "ws_estimate_links")
public class EstimateLink {

    /** Why a link no longer opens. */
    public enum Closed { NONE, REPLACED, EXPIRED }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "job_card_id", nullable = false, updatable = false)
    private UUID jobCardId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "sent_to", nullable = false, length = 254)
    private String sentTo;

    @Column(name = "sent_by", nullable = false, length = 100)
    private String sentBy;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "first_viewed_at")
    private Instant firstViewedAt;

    @Column(name = "reminders_sent", nullable = false)
    private int remindersSent;

    @Column(name = "next_reminder_at")
    private Instant nextReminderAt;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "signer_name", length = 100)
    private String signerName;

    @Column(name = "signature")
    private byte[] signature;

    @Column(name = "signer_ip", length = 64)
    private String signerIp;

    @Column(name = "signer_agent", length = 300)
    private String signerAgent;

    @Version
    private Long version;

    protected EstimateLink() {
        // for JPA
    }

    public static EstimateLink send(UUID jobCardId, String tokenHash, String to, String by, Instant now,
            Duration lifetime, Duration firstReminderAfter) {
        EstimateLink link = new EstimateLink();
        link.id = UUID.randomUUID();
        link.jobCardId = jobCardId;
        link.tokenHash = tokenHash;
        link.sentTo = to;
        link.sentBy = cut(by, 100);
        link.sentAt = now;
        link.expiresAt = now.plus(lifetime);
        link.nextReminderAt = firstReminderAfter == null ? null : now.plus(firstReminderAfter);
        return link;
    }

    public Closed closed(Instant now) {
        if (replacedAt != null) {
            return Closed.REPLACED;
        }
        return now.isBefore(expiresAt) || decidedAt != null ? Closed.NONE : Closed.EXPIRED;
    }

    public void viewed(Instant now) {
        if (firstViewedAt == null) {
            firstViewedAt = now;
        }
    }

    /** A newer link went out: this one stops working and is never reminded about. */
    public void replace(Instant now) {
        replacedAt = now;
        nextReminderAt = null;
    }

    /**
     * The link of a reminder: only token hashes are stored, so each reminder carries a new token
     * for the same estimate. This link keeps working (until a new one is sent or it runs out);
     * the reminders carry on from the new one.
     */
    public EstimateLink reminder(String tokenHash, Instant now, Duration every, int maxReminders) {
        EstimateLink next = new EstimateLink();
        next.id = UUID.randomUUID();
        next.jobCardId = jobCardId;
        next.tokenHash = tokenHash;
        next.sentTo = sentTo;
        next.sentBy = sentBy;
        next.sentAt = now;
        next.expiresAt = expiresAt;
        next.remindersSent = remindersSent + 1;
        next.nextReminderAt = next.remindersSent >= maxReminders ? null : now.plus(every);
        nextReminderAt = null;
        return next;
    }

    /** Nothing more to remind about (the estimate was decided another way, say). */
    public void stopReminders() {
        nextReminderAt = null;
    }

    public void signed(String name, byte[] png, String ip, String agent, Instant now) {
        decidedAt = now;
        signerName = cut(name, 100);
        signature = png;
        signerIp = cut(ip, 64);
        signerAgent = cut(agent, 300);
        nextReminderAt = null;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getJobCardId() { return jobCardId; }
    public String getSentTo() { return sentTo; }
    public String getSentBy() { return sentBy; }
    public Instant getSentAt() { return sentAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getFirstViewedAt() { return firstViewedAt; }
    public int getRemindersSent() { return remindersSent; }
    public Instant getNextReminderAt() { return nextReminderAt; }
    public Instant getReplacedAt() { return replacedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getSignerName() { return signerName; }
    public byte[] getSignature() { return signature; }
    public boolean isSigned() { return signature != null; }
}
