package com.bayflow.workshop.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** A note of work a mechanic completed on an approved recommendation. */
@Entity
@Table(name = "ws_work_logs")
public class WorkLog {

    @Id
    private UUID id;

    /** The workshop this entry belongs to. Hibernate fills it and filters every query by it. */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The job card's location, copied so row-level security can check this table on its own. */
    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private RecommendedWorkItem recommendation;

    @Column(nullable = false, length = 100)
    private String mechanic;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected WorkLog() {
        // for JPA
    }

    WorkLog(JobCard jobCard, RecommendedWorkItem recommendation, String mechanic, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.jobCard = jobCard;
        this.locationId = jobCard.getLocationId();
        this.recommendation = recommendation;
        this.mechanic = mechanic;
        this.description = description.trim();
        this.completedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLocationId() { return locationId; }
    public RecommendedWorkItem getRecommendation() { return recommendation; }
    public String getMechanic() { return mechanic; }
    public String getDescription() { return description; }
    public Instant getCompletedAt() { return completedAt; }
}
