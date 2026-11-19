package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** An image of a workshop's branding (logo or sign-in cover), already checked and re-encoded. */
@Entity
@Table(name = "auth_tenant_assets")
public class TenantAsset {

    public enum Kind { LOGO, COVER }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Kind kind;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantAsset() {
        // for JPA
    }

    public static TenantAsset of(Kind kind, String contentType, byte[] content, String sha256, Instant now) {
        TenantAsset asset = new TenantAsset();
        asset.id = UUID.randomUUID();
        asset.kind = kind;
        asset.replace(contentType, content, sha256, now);
        return asset;
    }

    public void replace(String contentType, byte[] content, String sha256, Instant now) {
        this.contentType = contentType;
        this.content = content;
        this.sha256 = sha256;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Kind getKind() { return kind; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
    public String getSha256() { return sha256; }
    public Instant getUpdatedAt() { return updatedAt; }
}
