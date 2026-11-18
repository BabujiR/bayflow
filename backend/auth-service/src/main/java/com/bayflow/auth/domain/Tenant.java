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
 * A workshop using Bayflow. Platform-level: not itself tenant-scoped. Its id is the
 * {@code tid} claim in its users' tokens and the {@code tenant_id} on all of its data.
 */
@Entity
@Table(name = "auth_tenants")
public class Tenant {

    public enum Status { ACTIVE, SUSPENDED }

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Tenant() {
        // for JPA
    }

    /** A new, active workshop. The slug is its address and never changes. */
    public static Tenant create(UUID id, String slug, String name, Instant now) {
        Tenant tenant = new Tenant();
        tenant.id = id;
        tenant.slug = slug;
        tenant.name = name;
        tenant.status = Status.ACTIVE;
        tenant.createdAt = now;
        tenant.updatedAt = now;
        return tenant;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public Status getStatus() { return status; }
    public boolean isActive() { return status == Status.ACTIVE; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
