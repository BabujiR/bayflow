package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A setup invite from Ramplight's team: a single-use link that lets one workshop owner create
 * their workshop. Only a hash of the link's token is kept; resending issues a new token.
 */
@Entity
@Table(name = "auth_invitations")
public class Invitation {

    /** Stored status. A PENDING invite past its expiry shows as {@link State#EXPIRED}. */
    public enum Status { PENDING, ACCEPTED, REVOKED }

    /** What the console shows. */
    public enum State { PENDING, EXPIRED, ACCEPTED, REVOKED }

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "shop_name", length = 120)
    private String shopName;

    @Column(name = "demo_request_id")
    private UUID demoRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /* The workshop created with it (not a tenant scope: invites are platform-level). */
    @Column(name = "workshop_id")
    private UUID workshopId;

    @Column(name = "workshop_slug", length = 30)
    private String workshopSlug;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    @Version
    private Long version;

    protected Invitation() {
        // for JPA
    }

    public static Invitation create(String email, String contactName, String shopName, UUID demoRequestId,
            String tokenHash, Instant expiresAt, String by, Instant now) {
        Invitation i = new Invitation();
        i.id = UUID.randomUUID();
        i.email = email.strip().toLowerCase();
        i.contactName = blankToNull(contactName);
        i.shopName = blankToNull(shopName);
        i.demoRequestId = demoRequestId;
        i.tokenHash = tokenHash;
        i.expiresAt = expiresAt;
        i.sentCount = 1;
        i.lastSentAt = now;
        i.createdAt = now;
        i.createdBy = by;
        return i;
    }

    /** A new link (the old one stops working) and a fresh expiry. Pending invites only. */
    public void resend(String newTokenHash, Instant newExpiresAt, Instant now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending invites can be resent");
        }
        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiresAt;
        this.sentCount++;
        this.lastSentAt = now;
    }

    public void revoke(String by, Instant now) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending invites can be revoked");
        }
        this.status = Status.REVOKED;
        this.revokedBy = by;
        this.revokedAt = now;
    }

    /** The owner created workshop {@code slug}: the link stops working for good. */
    public void accept(UUID tenant, String slug, Instant now) {
        if (state(now) != State.PENDING) {
            throw new IllegalStateException("Only valid pending invites can be accepted");
        }
        this.status = Status.ACCEPTED;
        this.workshopId = tenant;
        this.workshopSlug = slug;
        this.acceptedAt = now;
    }

    public State state(Instant now) {
        return switch (status) {
            case PENDING -> now.isBefore(expiresAt) ? State.PENDING : State.EXPIRED;
            case ACCEPTED -> State.ACCEPTED;
            case REVOKED -> State.REVOKED;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getContactName() { return contactName; }
    public String getShopName() { return shopName; }
    public UUID getDemoRequestId() { return demoRequestId; }
    public Status getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getSentCount() { return sentCount; }
    public Instant getLastSentAt() { return lastSentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public UUID getWorkshopId() { return workshopId; }
    public String getWorkshopSlug() { return workshopSlug; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public Long getVersion() { return version; }
}
