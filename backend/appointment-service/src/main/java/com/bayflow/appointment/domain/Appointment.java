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

import org.hibernate.annotations.TenantId;

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

    /** The workshop this appointment belongs to. Hibernate fills it and filters every query by it. */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The location (shop) of the organization where the work is done. Never changes. */
    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

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

    /** Booked by the customer on the public booking page (no sign-in). */
    @Column(name = "booked_online", nullable = false)
    private boolean bookedOnline;

    /** The online booking's service as it was then (name, how the price showed, the amount). */
    @Column(name = "service_name", length = 80)
    private String serviceName;

    @Column(name = "service_price_kind", length = 10)
    private String servicePriceKind;

    @Column(name = "service_price", precision = 10, scale = 2)
    private java.math.BigDecimal servicePrice;

    /** When the customer agreed to texts about this booking (null: no texts). */
    @Column(name = "sms_consent_at")
    private Instant smsConsentAt;

    /** SHA-256 of the private "change or cancel" link's token (online bookings); never the token. */
    @Column(name = "manage_token_hash", length = 64)
    private String manageTokenHash;

    /** Times the customer moved it from the link. */
    @Column(name = "customer_reschedules", nullable = false)
    private int customerReschedules;

    @Column(name = "customer_changed_at")
    private Instant customerChangedAt;

    /** CUSTOMER when they cancelled from the link; STAFF when the shop did. */
    @Column(name = "cancelled_by", length = 10)
    private String cancelledBy;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Version
    private Long version;

    protected Appointment() {
        // for JPA
    }

    public static Appointment book(Customer customer, Vehicle vehicle, UUID locationId, Instant scheduledAt,
            int durationMinutes, ServiceType serviceType, String notes, Instant now) {
        if (!vehicle.getOwner().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("The vehicle does not belong to the customer");
        }
        if (locationId == null) {
            throw new IllegalArgumentException("An appointment needs a location");
        }
        Appointment appointment = new Appointment();
        appointment.id = UUID.randomUUID();
        appointment.customer = customer;
        appointment.vehicle = vehicle;
        appointment.locationId = locationId;
        appointment.serviceType = serviceType;
        appointment.notes = notes == null || notes.isBlank() ? null : notes.trim();
        appointment.status = AppointmentStatus.BOOKED;
        appointment.createdAt = now;
        appointment.setSlot(scheduledAt, durationMinutes, now);
        return appointment;
    }

    /**
     * A quick estimate made at the desk or on the phone: the visit exists (so its job card can) but
     * has no time yet; {@code notes} is what the customer asked for.
     */
    public static Appointment estimate(Customer customer, Vehicle vehicle, UUID locationId, int durationMinutes,
            String notes, Instant now) {
        Appointment appointment = book(customer, vehicle, locationId, now, durationMinutes, ServiceType.REPAIR,
                notes, now);
        appointment.status = AppointmentStatus.ESTIMATE;
        return appointment;
    }

    /** The customer goes ahead with the estimate: the visit is booked for a time. */
    public void schedule(Instant scheduledAt, int durationMinutes, Instant now) {
        require(AppointmentAction.SCHEDULE);
        setSlot(scheduledAt, durationMinutes, now);
        status = AppointmentStatus.BOOKED;
        updatedAt = now;
    }

    /** Marks a booking made on the public booking page, keeping the service as it was offered. */
    public void bookedOnline(String serviceName, String priceKind, java.math.BigDecimal price, Instant smsConsentAt) {
        this.bookedOnline = true;
        this.serviceName = serviceName;
        this.servicePriceKind = priceKind;
        this.servicePrice = price;
        this.smsConsentAt = smsConsentAt;
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
        cancelledBy = "STAFF";
        updatedAt = now;
    }

    /** The customer cancels from the e-mailed link; {@code reason} is shown to the shop. */
    public void cancelByCustomer(String reason, Instant now) {
        cancel(now);
        cancelledBy = "CUSTOMER";
        cancelReason = reason == null || reason.isBlank() ? null : reason.strip();
        customerChangedAt = now;
    }

    /** The customer moves it from the e-mailed link (counted: they may do so a few times). */
    public void rescheduleByCustomer(Instant newScheduledAt, Instant now) {
        reschedule(newScheduledAt, durationMinutes, now);
        customerReschedules++;
        customerChangedAt = now;
    }

    /** A new private link: only the token's hash is kept. */
    public void useManageToken(String tokenHash) {
        this.manageTokenHash = tokenHash;
    }

    /** Moves the booking to the customer's account (claimed online booking, merged profiles). */
    public void moveTo(Customer owner, Instant now) {
        this.customer = owner;
        this.updatedAt = now;
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

    /** A job card is opened at check-in, or straight away for a quick estimate. */
    public boolean canLinkJobCard() {
        return status == AppointmentStatus.CHECKED_IN || status == AppointmentStatus.ESTIMATE;
    }

    public void linkJobCard(UUID jobCardId, Instant now) {
        if (!canLinkJobCard()) {
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
    public UUID getTenantId() { return tenantId; }
    public UUID getLocationId() { return locationId; }
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
    public boolean isBookedOnline() { return bookedOnline; }
    public String getServiceName() { return serviceName; }
    public String getServicePriceKind() { return servicePriceKind; }
    public java.math.BigDecimal getServicePrice() { return servicePrice; }
    public Instant getSmsConsentAt() { return smsConsentAt; }
    public String getManageTokenHash() { return manageTokenHash; }
    public int getCustomerReschedules() { return customerReschedules; }
    public Instant getCustomerChangedAt() { return customerChangedAt; }
    public boolean isCancelledByCustomer() { return "CUSTOMER".equals(cancelledBy); }
    public String getCancelReason() { return cancelReason; }
}
