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

import org.hibernate.annotations.TenantId;

/** One line a workshop shows at the top of its pages (an offer, holiday hours), optionally between two dates. */
@Entity
@Table(name = "auth_tenant_announcements")
public class Announcement {

    public enum Style { INFO, IMPORTANT }

    /** Where an announcement is in its dates, at a given moment. */
    public enum State { LIVE, SCHEDULED, ENDED }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Style style = Style.INFO;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Announcement() {
        // for JPA
    }

    public static Announcement create(String text, Style style, Instant startsAt, Instant endsAt, Instant now) {
        Announcement announcement = new Announcement();
        announcement.id = UUID.randomUUID();
        announcement.createdAt = now;
        announcement.update(text, style, startsAt, endsAt, now);
        return announcement;
    }

    public void update(String text, Style style, Instant startsAt, Instant endsAt, Instant now) {
        this.text = text;
        this.style = style == null ? Style.INFO : style;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = now;
    }

    /** Live from its start (or always) until its end (or forever). */
    public State stateAt(Instant now) {
        if (startsAt != null && now.isBefore(startsAt)) {
            return State.SCHEDULED;
        }
        if (endsAt != null && !now.isBefore(endsAt)) {
            return State.ENDED;
        }
        return State.LIVE;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getText() { return text; }
    public Style getStyle() { return style; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
