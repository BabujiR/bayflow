package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** A user's profile photo, already checked, cut square and re-encoded (256 px JPEG). */
@Entity
@Table(name = "auth_user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {
        // for JPA
    }

    public static UserAvatar of(UUID userId, String contentType, byte[] content, String sha256, Instant now) {
        UserAvatar avatar = new UserAvatar();
        avatar.userId = userId;
        avatar.replace(contentType, content, sha256, now);
        return avatar;
    }

    public void replace(String contentType, byte[] content, String sha256, Instant now) {
        this.contentType = contentType;
        this.content = content;
        this.sha256 = sha256;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
    public String getSha256() { return sha256; }
}
