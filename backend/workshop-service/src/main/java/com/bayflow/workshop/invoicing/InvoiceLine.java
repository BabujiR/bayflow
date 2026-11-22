package com.bayflow.workshop.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;

/**
 * One line of an invoice. Its total is quantity × unit price, rounded to cents; a DISCOUNT's
 * total is negative and lowers its tax category's taxable amount. unitCost (parts) is what the
 * shop paid, for profit; customers never see it.
 */
@Entity
@Table(name = "ws_invoice_lines")
public class InvoiceLine {

    /** The fee recalculated from labour whenever the draft changes. */
    public static final String SHOP_SUPPLIES = "SHOP_SUPPLIES";
    public static final String DISPOSAL = "DISPOSAL";

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private LineType type;

    @Column(name = "fee_code", length = 20)
    private String feeCode;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "part_number", length = 50)
    private String partNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private boolean taxable;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_category", nullable = false, length = 10)
    private TaxCategory taxCategory;

    @Column(name = "source_part_usage_id", updatable = false)
    private UUID sourcePartUsageId;

    @Column(name = "source_labour_entry_id", updatable = false)
    private UUID sourceLabourEntryId;

    protected InvoiceLine() {
        // for JPA
    }

    static InvoiceLine create(Invoice invoice, LineType type, String description, BigDecimal quantity,
            BigDecimal unitPrice, TaxCategory category, boolean taxable) {
        InvoiceLine line = new InvoiceLine();
        line.id = UUID.randomUUID();
        line.locationId = invoice.getLocationId();
        line.invoice = invoice;
        line.type = type;
        line.taxCategory = category;
        line.set(description, null, quantity, unitPrice, null, taxable);
        return line;
    }

    InvoiceLine fromPart(UUID partUsageId, String number) {
        this.sourcePartUsageId = partUsageId;
        this.partNumber = number;
        return this;
    }

    InvoiceLine fromLabour(UUID labourEntryId) {
        this.sourceLabourEntryId = labourEntryId;
        return this;
    }

    InvoiceLine fee(String code) {
        this.feeCode = code;
        return this;
    }

    void set(String description, String partNumber, BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost,
            boolean taxable) {
        this.description = description.length() > 200 ? description.substring(0, 199) + "…" : description;
        if (partNumber != null) {
            this.partNumber = partNumber;
        }
        this.quantity = quantity.setScale(2, RoundingMode.HALF_UP);
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.unitCost = type == LineType.PART && unitCost != null ? unitCost.setScale(2, RoundingMode.HALF_UP) : null;
        this.taxable = taxable;
        recompute();
    }

    void category(TaxCategory category) {
        this.taxCategory = category;
    }

    void position(int position) {
        this.position = position;
    }

    void price(BigDecimal unitPrice) {
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        recompute();
    }

    private void recompute() {
        BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        this.lineTotal = type == LineType.DISCOUNT ? amount.negate() : amount;
    }

    /** What the parts on this line cost the shop, or null when unknown (or not a part). */
    public BigDecimal costTotal() {
        return unitCost == null ? null : quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public LineType getType() { return type; }
    public String getFeeCode() { return feeCode; }
    public String getDescription() { return description; }
    public String getPartNumber() { return partNumber; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public boolean isTaxable() { return taxable; }
    public TaxCategory getTaxCategory() { return taxCategory; }
    public UUID getSourcePartUsageId() { return sourcePartUsageId; }
    public UUID getSourceLabourEntryId() { return sourceLabourEntryId; }
}
