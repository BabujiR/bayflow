package com.bayflow.workshop.reminders;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** An organization's reminder settings; the id is the organization's (row-level security checks it). */
@Entity
@Table(name = "ws_reminder_settings")
public class ReminderSettings {

    public static final int DEFAULT_REMIND_DAYS_BEFORE = 14;
    public static final int DEFAULT_MILES_PER_DAY = 30;

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "remind_days_before", nullable = false)
    private int remindDaysBefore = DEFAULT_REMIND_DAYS_BEFORE;

    @Column(name = "miles_per_day", nullable = false)
    private int milesPerDay = DEFAULT_MILES_PER_DAY;

    @Column(name = "defaults_added", nullable = false)
    private boolean defaultsAdded;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ReminderSettings() {
        // for JPA
    }

    public static ReminderSettings create(UUID tenantId, Instant now) {
        ReminderSettings settings = new ReminderSettings();
        settings.tenantId = tenantId;
        settings.updatedAt = now;
        return settings;
    }

    public void update(boolean enabled, int remindDaysBefore, int milesPerDay, Instant now) {
        this.enabled = enabled;
        this.remindDaysBefore = remindDaysBefore;
        this.milesPerDay = milesPerDay;
        this.updatedAt = now;
    }

    public void defaultsAdded(Instant now) {
        this.defaultsAdded = true;
        this.updatedAt = now;
    }

    public UUID getTenantId() { return tenantId; }
    public boolean isEnabled() { return enabled; }
    public int getRemindDaysBefore() { return remindDaysBefore; }
    public int getMilesPerDay() { return milesPerDay; }
    public boolean isDefaultsAdded() { return defaultsAdded; }
    public Long getVersion() { return version; }
}
