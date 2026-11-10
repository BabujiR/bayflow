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

@Entity
@Table(name = "apt_vehicles")
public class Vehicle {

    /** 17 characters, no I, O or Q (ISO 3779). */
    public static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer owner;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Column(nullable = false, length = 20)
    private String registration;

    @Column(nullable = false, length = 50)
    private String make;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "model_year", nullable = false)
    private int year;

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
    public Customer getOwner() { return owner; }
    public String getVin() { return vin; }
    public String getRegistration() { return registration; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public int getMileage() { return mileage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
