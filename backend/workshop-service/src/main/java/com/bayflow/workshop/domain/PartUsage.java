package com.bayflow.workshop.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A part fitted for an approved recommendation. */
@Entity
@Table(name = "ws_part_usages")
public class PartUsage {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private RecommendedWorkItem recommendation;

    @Column(name = "part_number", nullable = false, length = 50)
    private String partNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PartUsage() {
        // for JPA
    }

    PartUsage(JobCard jobCard, RecommendedWorkItem recommendation, String partNumber, String name, int quantity,
            BigDecimal unitPrice, String recordedBy, Instant now) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.jobCard = jobCard;
        this.recommendation = recommendation;
        this.partNumber = partNumber.trim();
        this.name = name.trim();
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.recordedBy = recordedBy;
        this.createdAt = now;
    }

    /** Quantity × unit price. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public RecommendedWorkItem getRecommendation() { return recommendation; }
    public String getPartNumber() { return partNumber; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
