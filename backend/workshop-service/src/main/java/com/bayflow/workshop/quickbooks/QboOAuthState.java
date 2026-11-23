package com.bayflow.workshop.quickbooks;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** A "Connect to QuickBooks" in progress: only the hash of the state sent to Intuit is kept. */
@Entity
@Table(name = "ws_qbo_oauth_states")
public class QboOAuthState {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "state_hash", nullable = false, length = 64, updatable = false)
    private String stateHash;

    @Column(name = "return_url", nullable = false, length = 500, updatable = false)
    private String returnUrl;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QboOAuthState() {
        // for JPA
    }

    public static QboOAuthState create(String stateHash, String returnUrl, String createdBy, Instant expiresAt,
            Instant now) {
        QboOAuthState state = new QboOAuthState();
        state.id = UUID.randomUUID();
        state.stateHash = stateHash;
        state.returnUrl = returnUrl;
        state.createdBy = createdBy;
        state.expiresAt = expiresAt;
        state.createdAt = now;
        return state;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public UUID getId() { return id; }
    public String getStateHash() { return stateHash; }
    public String getReturnUrl() { return returnUrl; }
    public String getCreatedBy() { return createdBy; }
    public Instant getExpiresAt() { return expiresAt; }
}
