package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Someone asked for a demo on the public site; Ramplight's team follows up. */
@Entity
@Table(name = "auth_demo_requests")
public class DemoRequest {

    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "shop_name", nullable = false, length = 120)
    private String shopName;

    /** Older requests only (null since the form stopped asking). */
    @Column(name = "shop_type", length = 30)
    private String shopType;

    @Column(nullable = false, length = 10)
    private String locations;

    @Column(length = 120)
    private String city;

    @Column(length = 1000)
    private String message;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(length = 2000)
    private String notes;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    private Long version;

    /** Where the request stands with Ramplight's team. INVITED and ONBOARDED are set by invitations. */
    public enum Status { NEW, CONTACTED, DEMO_SCHEDULED, DEMO_DONE, INVITED, ONBOARDED, DECLINED }

    /** What the form sends. */
    public record Details(String firstName, String lastName, String email, String phone, String shopName,
            String shopType, String locations, String city, String message) {
    }

    protected DemoRequest() {
        // for JPA
    }

    public static DemoRequest create(Details d, String clientIp, Instant now) {
        DemoRequest r = new DemoRequest();
        r.id = UUID.randomUUID();
        r.firstName = d.firstName().strip();
        r.lastName = d.lastName().strip();
        r.email = d.email().strip().toLowerCase();
        r.phone = d.phone().strip();
        r.shopName = d.shopName().strip();
        r.shopType = blankToNull(d.shopType());
        r.locations = d.locations();
        r.city = blankToNull(d.city());
        r.message = blankToNull(d.message());
        r.clientIp = clientIp == null || clientIp.length() > 64 ? null : clientIp;
        r.createdAt = now;
        return r;
    }

    /** The team moves the request on and keeps notes. */
    public void update(Status status, String notes, String by, Instant now) {
        this.status = status;
        this.notes = blankToNull(notes);
        this.updatedBy = by;
        this.updatedAt = now;
    }

    /** A setup invite was sent for this request (an onboarded one stays onboarded). */
    public void invited(String by, Instant now) {
        if (status != Status.ONBOARDED) {
            this.status = Status.INVITED;
            this.updatedBy = by;
            this.updatedAt = now;
        }
    }

    /** The invite was used: the workshop exists ({@code by}: its new admin). */
    public void onboarded(String by, Instant now) {
        this.status = Status.ONBOARDED;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getShopName() { return shopName; }
    public String getShopType() { return shopType; }
    public String getLocations() { return locations; }
    public String getCity() { return city; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public String getClientIp() { return clientIp; }
    public Status getStatus() { return status; }
    public String getNotes() { return notes; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }
}
