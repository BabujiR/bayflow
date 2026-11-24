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

    /** Made from the console for internal testing, not a customer. */
    @Column(name = "is_test", nullable = false)
    private boolean test;

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

    /** A test workshop from the console (labelled as such everywhere Ramplight's team sees it). */
    public static Tenant createTest(UUID id, String slug, String name, Instant now) {
        Tenant tenant = create(id, slug, name, now);
        tenant.test = true;
        return tenant;
    }

    /** Nobody can sign in while it is suspended; its data is kept. */
    public void suspend(Instant now) {
        this.status = Status.SUSPENDED;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        this.status = Status.ACTIVE;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public boolean isTest() { return test; }
    public Long getVersion() { return version; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public Status getStatus() { return status; }
    public boolean isActive() { return status == Status.ACTIVE; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
