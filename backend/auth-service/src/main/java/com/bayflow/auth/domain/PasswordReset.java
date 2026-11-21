package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/**
 * A "forgot password" link: single use, short-lived. Only the SHA-256 hash of the token in the
 * link is stored, like refresh tokens.
 */
@Entity
@Table(name = "auth_password_resets")
public class PasswordReset {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordReset() {
        // for JPA
    }

    public static PasswordReset create(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        PasswordReset reset = new PasswordReset();
        reset.id = UUID.randomUUID();
        reset.userId = Objects.requireNonNull(userId);
        reset.tokenHash = Objects.requireNonNull(tokenHash);
        reset.expiresAt = Objects.requireNonNull(expiresAt);
        reset.createdAt = now;
        return reset;
    }

    /** Not used yet and not expired. */
    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
