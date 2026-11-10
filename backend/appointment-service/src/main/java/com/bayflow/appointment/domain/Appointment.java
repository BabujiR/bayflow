package com.bayflow.appointment.domain;

import java.time.Duration;
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
import jakarta.persistence.Version;

/**
 * A service booking for one vehicle. Status changes go through the methods below, which
 * enforce the state machine in {@link AppointmentAction}.
 */
@Entity
@Table(name = "apt_appointments")
public class Appointment {

    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 480;
    private static final String ENTITY = "appointment";

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    /** scheduledAt + duration; stored so overlaps can be checked in the database. */
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private ServiceType serviceType;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "arrival_mileage")
    private Integer arrivalMileage;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Set once workshop-service has created the job card (milestone 5). */
    @Column(name = "job_card_id")
    private UUID jobCardId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Appointment() {
        // for JPA
    }

    public static Appointment book(Customer customer, Vehicle vehicle, Instant scheduledAt, int durationMinutes,
            ServiceType serviceType, String notes, Instant now) {
        if (!vehicle.getOwner().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("The vehicle does not belong to the customer");
        }
        Appointment appointment = new Appointment();
        appointment.id = UUID.randomUUID();
        appointment.customer = customer;
        appointment.vehicle = vehicle;
        appointment.serviceType = serviceType;
        appointment.notes = notes == null || notes.isBlank() ? null : notes.trim();
        appointment.status = AppointmentStatus.BOOKED;
        appointment.createdAt = now;
        appointment.setSlot(scheduledAt, durationMinutes, now);
        return appointment;
    }

    // ---- state machine --------------------------------------------------------

    public void confirm(Instant now) {
        require(AppointmentAction.CONFIRM);
        status = AppointmentStatus.CONFIRMED;
        confirmedAt = now;
        updatedAt = now;
    }

    public void arrive(Instant now) {
        require(AppointmentAction.ARRIVE);
        status = AppointmentStatus.ARRIVED;
        arrivedAt = now;
        updatedAt = now;
    }

    /** Records the odometer reading and hands the vehicle over to the workshop. */
    public void checkIn(int mileage, Instant now) {
        require(AppointmentAction.CHECK_IN);
        vehicle.recordMileage(mileage, now);
        status = AppointmentStatus.CHECKED_IN;
        arrivalMileage = mileage;
        checkedInAt = now;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        require(AppointmentAction.CANCEL);
        status = AppointmentStatus.CANCELLED;
        cancelledAt = now;
        updatedAt = now;
    }

    /** Only allowed once the booked time has passed. */
    public void markNoShow(Instant now) {
        require(AppointmentAction.NO_SHOW);
        if (!now.isAfter(scheduledAt)) {
            throw new BusinessRuleException("too-early-for-no-show",
                    "An appointment can only be marked as a no-show after its scheduled time.");
        }
        status = AppointmentStatus.NO_SHOW;
        updatedAt = now;
    }

    /** Moves the booking to a new time; the status does not change. */
    public void reschedule(Instant newScheduledAt, int newDurationMinutes, Instant now) {
        require(AppointmentAction.RESCHEDULE);
        setSlot(newScheduledAt, newDurationMinutes, now);
    }

    public void linkJobCard(UUID jobCardId, Instant now) {
        if (status != AppointmentStatus.CHECKED_IN) {
            throw new InvalidTransitionException(ENTITY, status, "link a job card to");
        }
        this.jobCardId = jobCardId;
        this.updatedAt = now;
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return scheduledAt.isBefore(otherEnd) && otherStart.isBefore(endsAt);
    }

    private void require(AppointmentAction action) {
        if (!action.isAllowedFrom(status)) {
            throw new InvalidTransitionException(ENTITY, status, action.description());
        }
    }

    private void setSlot(Instant start, int minutes, Instant now) {
        if (minutes < MIN_DURATION_MINUTES || minutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("Duration must be between " + MIN_DURATION_MINUTES + " and "
                    + MAX_DURATION_MINUTES + " minutes");
        }
        this.scheduledAt = start;
        this.durationMinutes = minutes;
        this.endsAt = start.plus(Duration.ofMinutes(minutes));
        this.updatedAt = now;
    }

    // ---- getters ----------------------------------------------------------------

    public UUID getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Vehicle getVehicle() { return vehicle; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public ServiceType getServiceType() { return serviceType; }
    public String getNotes() { return notes; }
    public AppointmentStatus getStatus() { return status; }
    public Integer getArrivalMileage() { return arrivalMileage; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getJobCardId() { return jobCardId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
