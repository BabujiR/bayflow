package com.bayflow.workshop.reviews;

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

/**
 * Asking one paid invoice's customer for a Google review: scheduled when the invoice is fully
 * paid, then sent (with a stop link), skipped (with the reason) or cancelled by staff.
 */
@Entity
@Table(name = "ws_review_requests")
public class ReviewRequest {

    public enum Status { SCHEDULED, SENT, SKIPPED, CANCELLED }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "vehicle", length = 150)
    private String vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_by", length = 100)
    private String sentBy;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ReviewRequest() {
        // for JPA
    }

    /** Waiting to go out at {@code dueAt}. {@code email}: lower case, or null when the invoice has none. */
    static ReviewRequest scheduled(UUID invoiceId, UUID locationId, String email, String name, String vehicle,
            Instant dueAt, Instant now) {
        ReviewRequest r = new ReviewRequest();
        r.id = UUID.randomUUID();
        r.invoiceId = invoiceId;
        r.locationId = locationId;
        r.customerEmail = email;
        r.customerName = cut(name == null || name.isBlank() ? "Customer" : name.strip(), 100);
        r.vehicle = cut(vehicle, 150);
        r.status = Status.SCHEDULED;
        r.dueAt = dueAt;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    /** Won't go out, and why ("No e-mail address on the invoice"). */
    void skip(String why, Instant now) {
        status = Status.SKIPPED;
        reason = cut(why, 200);
        dueAt = null;
        updatedAt = now;
    }

    /** Staff chose not to send it. */
    void cancel(String by, Instant now) {
        status = Status.CANCELLED;
        reason = cut("Not sent: " + by + " chose not to", 200);
        dueAt = null;
        updatedAt = now;
    }

    /** It went out, with the stop link's token (its SHA-256 here). */
    void sent(String to, String newTokenHash, String by, Instant now) {
        status = Status.SENT;
        customerEmail = to;
        tokenHash = newTokenHash;
        sentAt = now;
        sentBy = cut(by, 100);
        reason = null;
        dueAt = null;
        updatedAt = now;
    }

    /** Couldn't go out now: try again at {@code next}, or give up after {@code maxAttempts}. */
    void retryLater(Instant next, int maxAttempts, Instant now) {
        attempts++;
        if (attempts >= maxAttempts) {
            skip("The e-mail couldn't be sent", now);
            return;
        }
        dueAt = next;
        updatedAt = now;
    }

    boolean isScheduled() { return status == Status.SCHEDULED; }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getLocationId() { return locationId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerName() { return customerName; }
    public String getVehicle() { return vehicle; }
    public Status getStatus() { return status; }
    public Instant getDueAt() { return dueAt; }
    public int getAttempts() { return attempts; }
    public String getReason() { return reason; }
    public Instant getSentAt() { return sentAt; }
    public String getSentBy() { return sentBy; }
    public Instant getCreatedAt() { return createdAt; }
}
