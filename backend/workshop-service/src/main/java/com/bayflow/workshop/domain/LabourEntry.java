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

/** Time a mechanic spent on an approved recommendation, at an hourly rate. */
@Entity
@Table(name = "ws_labour_entries")
public class LabourEntry {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private RecommendedWorkItem recommendation;

    @Column(nullable = false, length = 100)
    private String mechanic;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal hours;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LabourEntry() {
        // for JPA
    }

    LabourEntry(JobCard jobCard, RecommendedWorkItem recommendation, String mechanic, BigDecimal hours,
            BigDecimal hourlyRate, Instant now) {
        if (hours.signum() <= 0) {
            throw new IllegalArgumentException("Hours must be greater than zero");
        }
        if (hourlyRate.signum() < 0) {
            throw new IllegalArgumentException("Hourly rate cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.jobCard = jobCard;
        this.recommendation = recommendation;
        this.mechanic = mechanic;
        this.hours = hours.setScale(2, RoundingMode.HALF_UP);
        this.hourlyRate = hourlyRate.setScale(2, RoundingMode.HALF_UP);
        this.createdAt = now;
    }

    /** Hours × hourly rate. */
    public BigDecimal cost() {
        return hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public RecommendedWorkItem getRecommendation() { return recommendation; }
    public String getMechanic() { return mechanic; }
    public BigDecimal getHours() { return hours; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public Instant getCreatedAt() { return createdAt; }
}
