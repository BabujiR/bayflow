package com.bayflow.auth.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * A workshop's own settings. Other services read them through auth-service's API and use the
 * labour rate and currency when a job card opens, and the time zone for "today".
 */
@Entity
@Table(name = "auth_tenant_settings")
public class TenantSettings {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(name = "labour_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal labourRate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected TenantSettings() {
        // for JPA
    }

    public static TenantSettings create(UUID tenantId, String displayName, BigDecimal labourRate, String currency,
            String timeZone, Instant now) {
        TenantSettings settings = new TenantSettings();
        settings.id = UUID.randomUUID();
        settings.tenantId = tenantId;
        settings.displayName = displayName;
        settings.labourRate = labourRate;
        settings.currency = currency;
        settings.timeZone = timeZone;
        settings.updatedAt = now;
        return settings;
    }

    public void update(String displayName, String phone, String email, String address, BigDecimal labourRate,
            String currency, String timeZone, Instant now) {
        this.displayName = displayName;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.labourRate = labourRate;
        this.currency = currency;
        this.timeZone = timeZone;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getDisplayName() { return displayName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public BigDecimal getLabourRate() { return labourRate; }
    public String getCurrency() { return currency; }
    public String getTimeZone() { return timeZone; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
