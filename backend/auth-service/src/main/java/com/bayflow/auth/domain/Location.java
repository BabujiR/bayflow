package com.bayflow.auth.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * One shop of an organization (tenant). An independent workshop has exactly one, "Main", whose
 * id is the tenant id. A location may override the organization's time zone and labour rate;
 * {@code null} means it uses the organization's. Currency is organization-wide.
 */
@Entity
@Table(name = "auth_locations")
public class Location {

    public enum Status { ACTIVE, ARCHIVED }

    /** Code of the first location every organization gets. */
    public static final String MAIN_CODE = "main";

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "labour_rate", precision = 10, scale = 2)
    private BigDecimal labourRate;

    // ---- invoice settings (R2a) ----

    @Column(name = "parts_tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal partsTaxRate = BigDecimal.ZERO;

    @Column(name = "labour_tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal labourTaxRate = BigDecimal.ZERO;

    @Column(name = "fees_tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal feesTaxRate = BigDecimal.ZERO;

    @Column(name = "shop_supplies_percent", precision = 6, scale = 3)
    private BigDecimal shopSuppliesPercent;

    @Column(name = "shop_supplies_cap", precision = 10, scale = 2)
    private BigDecimal shopSuppliesCap;

    @Column(name = "disposal_fee", precision = 10, scale = 2)
    private BigDecimal disposalFee;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "invoice_footer", length = 500)
    private String invoiceFooter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Location() {
        // for JPA
    }

    /** The organization's first location: id = tenant id, no overrides. */
    public static Location main(UUID tenantId, Instant now) {
        return create(tenantId, tenantId, MAIN_CODE, "Main", now);
    }

    public static Location create(UUID id, UUID tenantId, String code, String name, Instant now) {
        Location location = new Location();
        location.id = id;
        location.tenantId = tenantId;
        location.code = code;
        location.name = name;
        location.status = Status.ACTIVE;
        location.createdAt = now;
        location.updatedAt = now;
        return location;
    }

    /** Changes the details and overrides; null time zone / labour rate = use the organization's. */
    public void update(String code, String name, String phone, String email, String address, String timeZone,
            BigDecimal labourRate, Instant now) {
        this.code = code;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.timeZone = timeZone;
        this.labourRate = labourRate;
        this.updatedAt = now;
    }

    /** Changes the tax rates, fees and invoice terms. */
    public void updateBilling(LocationBilling billing, Instant now) {
        this.partsTaxRate = billing.partsTaxRate();
        this.labourTaxRate = billing.labourTaxRate();
        this.feesTaxRate = billing.feesTaxRate();
        this.shopSuppliesPercent = billing.shopSuppliesPercent();
        this.shopSuppliesCap = billing.shopSuppliesCap();
        this.disposalFee = billing.disposalFee();
        this.paymentTermsDays = billing.paymentTermsDays();
        this.invoiceFooter = billing.invoiceFooter();
        this.updatedAt = now;
    }

    public LocationBilling billing() {
        return new LocationBilling(partsTaxRate, labourTaxRate, feesTaxRate, shopSuppliesPercent, shopSuppliesCap,
                disposalFee, paymentTermsDays, invoiceFooter);
    }

    public void archive(Instant now) {
        this.status = Status.ARCHIVED;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        this.status = Status.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isActive() { return status == Status.ACTIVE; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    /** The override, or null to use the organization's. */
    public String getTimeZone() { return timeZone; }
    /** The override, or null to use the organization's. */
    public BigDecimal getLabourRate() { return labourRate; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
