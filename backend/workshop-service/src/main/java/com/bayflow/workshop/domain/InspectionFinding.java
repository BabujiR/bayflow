package com.bayflow.workshop.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Something the inspection found, e.g. "front brake pads at 2 mm". */
@Entity
@Table(name = "ws_inspection_findings")
public class InspectionFinding {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InspectionFinding() {
        // for JPA
    }

    InspectionFinding(JobCard jobCard, String description, Severity severity, String recordedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.jobCard = jobCard;
        this.description = description.trim();
        this.severity = severity;
        this.recordedBy = recordedBy;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
