package com.bayflow.appointment.bookingservices;

import java.math.BigDecimal;
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
 * A service customers can book online, from the shop's own list (Settings → Online booking).
 * {@code locationId} null: offered at every location.
 */
@Entity
@Table(name = "apt_booking_services")
public class BookingService {

    /** How the price shows on the booking page. */
    public enum PriceKind {
        /** No price shown. */
        NONE,
        /** "from $54" */
        FROM,
        /** "$79" */
        FIXED,
        FREE,
        /** "Ask for a quote" */
        QUOTE;

        public boolean hasAmount() {
            return this == FROM || this == FIXED;
        }
    }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_kind", nullable = false, length = 10)
    private PriceKind priceKind;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected BookingService() {
        // for JPA
    }

    /** What the admin fills in; the price only for FROM and FIXED. */
    public record Details(String name, String description, PriceKind priceKind, BigDecimal price,
            Integer durationMinutes, UUID locationId, boolean active) {
    }

    public static BookingService create(String code, Details details, int sortOrder, Instant now) {
        BookingService s = new BookingService();
        s.id = UUID.randomUUID();
        s.code = code;
        s.sortOrder = sortOrder;
        s.createdAt = now;
        s.update(details, now);
        return s;
    }

    public void update(Details d, Instant now) {
        this.name = d.name().strip();
        this.description = d.description() == null || d.description().isBlank() ? null : d.description().strip();
        this.priceKind = d.priceKind();
        this.price = d.priceKind().hasAmount() ? d.price() : null;
        this.durationMinutes = d.durationMinutes();
        this.locationId = d.locationId();
        this.active = d.active();
        this.updatedAt = now;
    }

    public void moveTo(int sortOrder, Instant now) {
        if (this.sortOrder != sortOrder) {
            this.sortOrder = sortOrder;
            this.updatedAt = now;
        }
    }

    /** Offered at this location (every location when it has none of its own). */
    public boolean offeredAt(UUID location) {
        return locationId == null || locationId.equals(location);
    }

    public UUID getId() { return id; }
    public UUID getLocationId() { return locationId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public PriceKind getPriceKind() { return priceKind; }
    public BigDecimal getPrice() { return price; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
