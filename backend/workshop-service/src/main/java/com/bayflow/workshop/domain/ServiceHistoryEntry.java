package com.bayflow.workshop.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * A permanent record of one completed job in a vehicle's service history. It is written
 * once, when the job card completes, and never changed ({@link Immutable}).
 */
@Entity
@Immutable
@Table(name = "ws_service_history")
public class ServiceHistoryEntry {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "job_card_id", nullable = false, unique = true)
    private UUID jobCardId;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(nullable = false, length = 17)
    private String vin;

    @Column(nullable = false, length = 20)
    private String registration;

    @Column(name = "service_date", nullable = false)
    private Instant serviceDate;

    @Column(nullable = false)
    private int mileage;

    /** Snapshot of findings, work, parts and labour as JSON. */
    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "parts_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsTotal;

    @Column(name = "labour_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal labourTotal;

    @Column(name = "total_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServiceHistoryEntry() {
        // for JPA
    }

    /** Records a completed job card. */
    public static ServiceHistoryEntry record(JobCard card, String details) {
        if (card.getStatus() != JobCardStatus.COMPLETED) {
            throw new IllegalStateException("Only completed job cards go into the service history");
        }
        ServiceHistoryEntry entry = new ServiceHistoryEntry();
        entry.id = UUID.randomUUID();
        entry.vehicleId = card.getVehicleId();
        entry.jobCardId = card.getId();
        entry.appointmentId = card.getAppointmentId();
        entry.customerUserId = card.getCustomerUserId();
        entry.vin = card.getVin();
        entry.registration = card.getRegistration();
        entry.serviceDate = card.getCompletedAt();
        entry.mileage = card.getMileage();
        entry.details = details;
        entry.partsTotal = card.getPartsTotal();
        entry.labourTotal = card.getLabourTotal();
        entry.totalCost = card.getGrandTotal();
        entry.createdAt = card.getCompletedAt();
        return entry;
    }

    public boolean isOwnedBy(UUID userId) {
        return customerUserId != null && customerUserId.equals(userId);
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public UUID getJobCardId() { return jobCardId; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getCustomerUserId() { return customerUserId; }
    public String getVin() { return vin; }
    public String getRegistration() { return registration; }
    public Instant getServiceDate() { return serviceDate; }
    public int getMileage() { return mileage; }
    public String getDetails() { return details; }
    public BigDecimal getPartsTotal() { return partsTotal; }
    public BigDecimal getLabourTotal() { return labourTotal; }
    public BigDecimal getTotalCost() { return totalCost; }
    public Instant getCreatedAt() { return createdAt; }
}
