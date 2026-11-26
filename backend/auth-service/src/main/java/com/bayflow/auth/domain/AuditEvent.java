package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

/** One change to an account, for the workshop's audit trail. Never changed once written. */
@Entity
@Immutable
@Table(name = "auth_audit_events")
public class AuditEvent {

    public enum Action {
        STAFF_CREATED,
        ROLES_CHANGED,
        LOCATIONS_CHANGED,
        ACCOUNT_ENABLED,
        ACCOUNT_DISABLED,
        ACCOUNT_UNLOCKED,
        PASSWORD_RESET,
        AVATAR_RESET
    }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Action action;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "target_name", length = 100)
    private String targetName;

    @Column(length = 500)
    private String details;

    @Column(name = "request_id", length = 64)
    private String requestId;

    protected AuditEvent() {
        // for JPA
    }

    public static AuditEvent of(Action action, UUID actorId, String actorName, UUID targetId, String targetName,
            String details, String requestId, Instant now) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.occurredAt = now;
        event.actorId = actorId;
        event.actorName = cut(actorName, 100);
        event.action = action;
        event.targetId = targetId;
        event.targetName = cut(targetName, 100);
        event.details = cut(details, 500);
        event.requestId = cut(requestId, 64);
        return event;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() { return id; }

    public Instant getOccurredAt() { return occurredAt; }

    public UUID getActorId() { return actorId; }

    public String getActorName() { return actorName; }

    public Action getAction() { return action; }

    public UUID getTargetId() { return targetId; }

    public String getTargetName() { return targetName; }

    public String getDetails() { return details; }

    public String getRequestId() { return requestId; }
}
