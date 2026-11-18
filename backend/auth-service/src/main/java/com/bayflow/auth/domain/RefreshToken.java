package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * A refresh token. Only the SHA-256 hash of the token value is stored. Every refresh
 * rotates the token within the same family; presenting a rotated (revoked) token again
 * is treated as theft and revokes the whole family.
 */
@Entity
@Table(name = "auth_refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    /** Same workshop as the user. Hibernate filters every query by it. */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected RefreshToken() {
        // for JPA
    }

    private RefreshToken(UUID tenantId, UUID userId, UUID familyId, String tokenHash, Instant expiresAt,
            Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** First token of a new login session. */
    public static RefreshToken newFamily(UUID tenantId, UUID userId, String tokenHash, Instant expiresAt,
            Instant now) {
        return new RefreshToken(tenantId, userId, UUID.randomUUID(), tokenHash, expiresAt, now);
    }

    /** Revokes this token and returns its successor in the same family. */
    public RefreshToken rotate(String newTokenHash, Instant newExpiresAt, Instant now) {
        if (revoked) {
            throw new IllegalStateException("A revoked token cannot be rotated");
        }
        RefreshToken next = new RefreshToken(tenantId, userId, familyId, newTokenHash, newExpiresAt, now);
        this.revoked = true;
        this.replacedBy = next.id;
        return next;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke() {
        this.revoked = true;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public UUID getReplacedBy() { return replacedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
