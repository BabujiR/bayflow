package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** A location a MANAGER, SERVICE_ADVISOR or MECHANIC works at. */
@Entity
@Table(name = "auth_user_locations")
public class UserLocation {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserLocation() {
        // for JPA
    }

    public static UserLocation of(UUID userId, UUID locationId, Instant now) {
        UserLocation assignment = new UserLocation();
        assignment.id = UUID.randomUUID();
        assignment.userId = userId;
        assignment.locationId = locationId;
        assignment.createdAt = now;
        return assignment;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getLocationId() { return locationId; }
    public Instant getCreatedAt() { return createdAt; }
}
