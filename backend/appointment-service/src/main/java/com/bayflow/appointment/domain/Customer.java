package com.bayflow.appointment.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A workshop customer. {@code userId} links the profile to a Bayflow login (the JWT "sub");
 * it is empty for customers an advisor created who have no account.
 */
@Entity
@Table(name = "apt_customers")
public class Customer {

    @Id
    private UUID id;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Customer() {
        // for JPA
    }

    public static Customer create(UUID userId, String name, String email, String phone, Instant now) {
        Customer customer = new Customer();
        customer.id = UUID.randomUUID();
        customer.userId = userId;
        customer.createdAt = now;
        customer.update(name, email, phone, now);
        return customer;
    }

    public void update(String name, String email, String phone, Instant now) {
        this.name = name.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.updatedAt = now;
    }

    public boolean isOwnedBy(UUID user) {
        return userId != null && userId.equals(user);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
