package com.bayflow.workshop.reminders;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A customer who turned service reminders off. */
@Entity
@Table(name = "ws_reminder_opt_outs")
@IdClass(ReminderOptOut.Key.class)
public class ReminderOptOut {

    public record Key(UUID tenantId, UUID customerUserId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReminderOptOut() {
        // for JPA
    }

    public static ReminderOptOut of(UUID tenantId, UUID customerUserId, Instant now) {
        ReminderOptOut optOut = new ReminderOptOut();
        optOut.tenantId = tenantId;
        optOut.customerUserId = customerUserId;
        optOut.createdAt = now;
        return optOut;
    }
}
