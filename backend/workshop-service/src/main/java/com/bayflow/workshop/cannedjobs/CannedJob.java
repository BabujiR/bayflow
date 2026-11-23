package com.bayflow.workshop.cannedjobs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.Hibernate;
import org.hibernate.annotations.TenantId;

/**
 * A ready-made package of work the shop does often: what the customer reads, the labour hours
 * (charged at the job card's rate) and the usual parts with their prices. locationId null = every
 * location of the organization.
 */
@Entity
@Table(name = "ws_canned_jobs")
public class CannedJob {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "labour_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal labourHours;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // Not final: JPA forbids final persistent fields, and Hibernate replaces the list on load.
    @OneToMany(mappedBy = "cannedJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @SuppressWarnings("FieldMayBeFinal")
    private List<CannedJobPart> parts = new ArrayList<>();

    /** One usual part as entered. */
    public record PartDetails(String partNumber, String name, int quantity, BigDecimal unitPrice) {
    }

    /** Everything an admin or manager edits. */
    public record Details(UUID locationId, String name, String category, String description, BigDecimal labourHours,
            List<PartDetails> parts) {
    }

    protected CannedJob() {
        // for JPA
    }

    public static CannedJob create(Details details, String by, Instant now) {
        CannedJob job = new CannedJob();
        job.id = UUID.randomUUID();
        job.createdAt = now;
        job.createdBy = by;
        job.update(details, now);
        return job;
    }

    public void update(Details details, Instant now) {
        this.locationId = details.locationId();
        this.name = details.name().strip();
        this.category = details.category() == null || details.category().isBlank() ? null
                : details.category().strip();
        this.description = details.description().strip();
        this.labourHours = details.labourHours().setScale(2, RoundingMode.HALF_UP);
        parts.clear();
        int position = 0;
        for (PartDetails part : details.parts()) {
            parts.add(new CannedJobPart(this, position++, part));
        }
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    /** What the usual parts cost the customer: the parts estimate of a recommendation. */
    public BigDecimal partsTotal() {
        return parts.stream().map(CannedJobPart::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Usable on a job card of this location. */
    public boolean appliesTo(UUID location) {
        return locationId == null || locationId.equals(location);
    }

    public void loadDetails() {
        Hibernate.initialize(parts);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLocationId() { return locationId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getLabourHours() { return labourHours; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public List<CannedJobPart> getParts() { return Collections.unmodifiableList(parts); }
}
