package com.bayflow.workshop.reminders;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** For one vehicle and one interval: when it was last done, and when it is next due. */
@Entity
@Table(name = "ws_vehicle_services")
public class VehicleService {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "interval_id", nullable = false, updatable = false)
    private UUID intervalId;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false, length = 20)
    private String registration;

    @Column(name = "job_card_id", nullable = false)
    private UUID jobCardId;

    @Column(name = "last_done_at", nullable = false)
    private Instant lastDoneAt;

    @Column(name = "last_mileage", nullable = false)
    private int lastMileage;

    @Column(name = "due_mileage")
    private Integer dueMileage;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** What a finished job says about the vehicle and when the service is next due. */
    public record Done(UUID locationId, UUID customerUserId, String customerName, String registration,
            UUID jobCardId, Instant doneAt, int mileage, Integer dueMileage, Instant dueAt) {
    }

    protected VehicleService() {
        // for JPA
    }

    public static VehicleService create(UUID vehicleId, UUID intervalId, Done done, Instant now) {
        VehicleService service = new VehicleService();
        service.id = UUID.randomUUID();
        service.vehicleId = vehicleId;
        service.intervalId = intervalId;
        service.record(done, now);
        return service;
    }

    /** Done again: a new due date, and the next reminder is for it. */
    public void record(Done done, Instant now) {
        this.locationId = done.locationId();
        this.customerUserId = done.customerUserId();
        this.customerName = done.customerName();
        this.registration = done.registration();
        this.jobCardId = done.jobCardId();
        this.lastDoneAt = done.doneAt();
        this.lastMileage = done.mileage();
        this.dueMileage = done.dueMileage();
        this.dueAt = done.dueAt();
        this.remindedAt = null;
        this.updatedAt = now;
    }

    public void reminded(Instant now) {
        this.remindedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getLocationId() { return locationId; }
    public UUID getVehicleId() { return vehicleId; }
    public UUID getIntervalId() { return intervalId; }
    public UUID getCustomerUserId() { return customerUserId; }
    public String getCustomerName() { return customerName; }
    public String getRegistration() { return registration; }
    public UUID getJobCardId() { return jobCardId; }
    public Instant getLastDoneAt() { return lastDoneAt; }
    public int getLastMileage() { return lastMileage; }
    public Integer getDueMileage() { return dueMileage; }
    public Instant getDueAt() { return dueAt; }
    public Instant getRemindedAt() { return remindedAt; }
}
