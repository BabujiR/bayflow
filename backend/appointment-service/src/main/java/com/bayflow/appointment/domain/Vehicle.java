package com.bayflow.appointment.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "apt_vehicles")
public class Vehicle {

    /** 17 characters, no I, O or Q (ISO 3779). */
    public static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    @Id
    private UUID id;

    /** The workshop this vehicle belongs to. Hibernate fills it and filters every query by it. */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer owner;

    /** Null only for a car booked online by year, make and model (filled in at check-in). */
    @Column(unique = true, length = 17)
    private String vin;

    /** Null only for a car booked online (filled in at check-in). */
    @Column(length = 20)
    private String registration;

    /** Null only for a car booked online by VIN. */
    @Column(length = 50)
    private String make;

    @Column(length = 50)
    private String model;

    @Column(name = "model_year")
    private Integer year;

    /** Odometer reading in miles. */
    @Column(nullable = false)
    private int mileage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Vehicle() {
        // for JPA
    }

    public static Vehicle register(Customer owner, String vin, String registration, String make, String model,
            int year, int mileage, Instant now) {
        String normalizedVin = normalizeVin(vin);
        if (!VIN.matcher(normalizedVin).matches()) {
            throw new IllegalArgumentException("Invalid VIN");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.id = UUID.randomUUID();
        vehicle.owner = owner;
        vehicle.vin = normalizedVin;
        vehicle.createdAt = now;
        vehicle.update(registration, make, model, year, mileage, now);
        return vehicle;
    }

    /**
     * A car from online booking: its VIN, or its year, make and model (at least one of the two).
     * The rest (and the plate) is filled in at check-in.
     */
    public static Vehicle fromOnlineBooking(Customer owner, String vin, String make, String model, Integer year,
            Instant now) {
        String normalizedVin = vin == null || vin.isBlank() ? null : normalizeVin(vin);
        if (normalizedVin != null && !VIN.matcher(normalizedVin).matches()) {
            throw new IllegalArgumentException("Invalid VIN");
        }
        boolean described = make != null && !make.isBlank() && model != null && !model.isBlank() && year != null;
        if (normalizedVin == null && !described) {
            throw new IllegalArgumentException("A VIN, or the year, make and model, is needed");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.id = UUID.randomUUID();
        vehicle.owner = owner;
        vehicle.vin = normalizedVin;
        vehicle.make = make == null || make.isBlank() ? null : make.trim();
        vehicle.model = model == null || model.isBlank() ? null : model.trim();
        vehicle.year = year;
        vehicle.mileage = 0;
        vehicle.createdAt = now;
        vehicle.updatedAt = now;
        return vehicle;
    }

    /** Whether the VIN and the plate are known (a job card needs both). */
    public boolean isIdentified() {
        return vin != null && registration != null;
    }

    /** Fills in what an online booking didn't give (at check-in); what is known stays. */
    public void complete(String vin, String registration, Instant now) {
        if (this.vin == null) {
            String normalizedVin = normalizeVin(vin);
            if (!VIN.matcher(normalizedVin).matches()) {
                throw new IllegalArgumentException("Invalid VIN");
            }
            this.vin = normalizedVin;
        }
        if (this.registration == null) {
            if (registration == null || registration.isBlank()) {
                throw new IllegalArgumentException("A plate is needed");
            }
            this.registration = registration.trim().toUpperCase(Locale.ROOT);
        }
        this.updatedAt = now;
    }

    public static String normalizeVin(String vin) {
        return vin == null ? "" : vin.trim().toUpperCase(Locale.ROOT);
    }

    public void update(String registration, String make, String model, int year, int mileage, Instant now) {
        if (mileage < 0) {
            throw new IllegalArgumentException("Mileage cannot be negative");
        }
        this.registration = registration.trim().toUpperCase(Locale.ROOT);
        this.make = make.trim();
        this.model = model.trim();
        this.year = year;
        this.mileage = mileage;
        this.updatedAt = now;
    }

    /** Records an odometer reading taken at check-in; it may never go down. */
    public void recordMileage(int reading, Instant now) {
        if (reading < mileage) {
            throw new IllegalArgumentException("Mileage cannot go down");
        }
        this.mileage = reading;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Customer getOwner() { return owner; }
    public String getVin() { return vin; }
    public String getRegistration() { return registration; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    /** 0 when unknown (a car booked online by VIN). */
    public int getYear() { return year == null ? 0 : year; }
    public int getMileage() { return mileage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
