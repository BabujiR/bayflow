package com.bayflow.workshop.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/** Work the advisor recommends, with an estimate, which the customer approves or declines. */
@Entity
@Table(name = "ws_recommended_items")
public class RecommendedWorkItem {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "estimated_parts_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedPartsCost;

    @Column(name = "estimated_labour_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal estimatedLabourHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    /** Who decided, e.g. "customer1" or "advisor1 (on behalf of the customer)". */
    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecommendedWorkItem() {
        // for JPA
    }

    RecommendedWorkItem(JobCard jobCard, String description, BigDecimal estimatedPartsCost,
            BigDecimal estimatedLabourHours, Instant now) {
        if (estimatedPartsCost.signum() < 0 || estimatedLabourHours.signum() < 0) {
            throw new IllegalArgumentException("Estimates cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.jobCard = jobCard;
        this.description = description.trim();
        this.estimatedPartsCost = estimatedPartsCost.setScale(2, RoundingMode.HALF_UP);
        this.estimatedLabourHours = estimatedLabourHours.setScale(2, RoundingMode.HALF_UP);
        this.approvalStatus = ApprovalStatus.PENDING;
        this.createdAt = now;
    }

    void decide(boolean approve, String decidedBy, Instant now) {
        if (approvalStatus != ApprovalStatus.PENDING) {
            throw new BusinessRuleException("item-already-decided",
                    "This recommendation was already " + approvalStatus.name().toLowerCase() + ".");
        }
        this.approvalStatus = approve ? ApprovalStatus.APPROVED : ApprovalStatus.DECLINED;
        this.decidedBy = decidedBy;
        this.decidedAt = now;
    }

    /** Labour hours × hourly rate, rounded to cents. */
    public BigDecimal estimatedLabourCost(BigDecimal hourlyRate) {
        return estimatedLabourHours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Parts + labour. */
    public BigDecimal estimatedTotal(BigDecimal hourlyRate) {
        return estimatedPartsCost.add(estimatedLabourCost(hourlyRate));
    }

    public boolean isPending() {
        return approvalStatus == ApprovalStatus.PENDING;
    }

    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getEstimatedPartsCost() { return estimatedPartsCost; }
    public BigDecimal getEstimatedLabourHours() { return estimatedLabourHours; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
