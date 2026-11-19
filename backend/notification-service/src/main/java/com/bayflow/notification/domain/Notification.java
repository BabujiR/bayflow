package com.bayflow.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** One message to one user about one event, until they read it (reading deletes it). */
@Entity
@Table(name = "ntf_notifications")
public class Notification {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id", updatable = false)
    private UUID locationId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private EventType type;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, length = 500, updatable = false)
    private String body;

    @Column(length = 300, updatable = false)
    private String link;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public static Notification create(UUID eventId, EventType type, UUID locationId, UUID recipientId,
            String title, String body, String link, Instant now) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.eventId = eventId;
        notification.type = type;
        notification.locationId = locationId;
        notification.recipientId = recipientId;
        notification.title = truncate(title, 200);
        notification.body = truncate(body, 500);
        notification.link = link;
        notification.createdAt = now;
        return notification;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLocationId() { return locationId; }
    public UUID getRecipientId() { return recipientId; }
    public EventType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public UUID getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
}
