package com.bayflow.workshop.cannedjobs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/** One usual part of a canned job, at the shop's usual price. */
@Entity
@Table(name = "ws_canned_job_parts")
public class CannedJobPart {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "canned_job_id", nullable = false, updatable = false)
    private CannedJob cannedJob;

    @Column(nullable = false)
    private int position;

    @Column(name = "part_number", nullable = false, length = 50)
    private String partNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    protected CannedJobPart() {
        // for JPA
    }

    CannedJobPart(CannedJob cannedJob, int position, CannedJob.PartDetails details) {
        this.id = UUID.randomUUID();
        this.cannedJob = cannedJob;
        this.position = position;
        this.partNumber = details.partNumber().strip();
        this.name = details.name().strip();
        this.quantity = details.quantity();
        this.unitPrice = details.unitPrice().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public String getPartNumber() { return partNumber; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
