package com.bayflow.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** An event already handled, so a repeated delivery is ignored. */
@Entity
@Table(name = "ntf_received_events")
public class ReceivedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private EventType type;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected ReceivedEvent() {
        // for JPA
    }

    public static ReceivedEvent of(UUID eventId, EventType type, Instant now) {
        ReceivedEvent event = new ReceivedEvent();
        event.eventId = eventId;
        event.type = type;
        event.receivedAt = now;
        return event;
    }

    public UUID getEventId() { return eventId; }
    public UUID getTenantId() { return tenantId; }
    public EventType getType() { return type; }
    public Instant getReceivedAt() { return receivedAt; }
}
