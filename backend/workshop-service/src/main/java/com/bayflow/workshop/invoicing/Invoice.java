package com.bayflow.workshop.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.Hibernate;
import org.hibernate.annotations.TenantId;

import com.bayflow.workshop.domain.BusinessRuleException;
import com.bayflow.workshop.domain.InvalidTransitionException;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;
import com.bayflow.workshop.domain.LabourEntry;
import com.bayflow.workshop.domain.PartUsage;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;
import com.bayflow.workshop.integration.WorkshopSettingsClient.LocationBilling;

/**
 * The bill for one completed job card. A DRAFT is made from the card's parts and labour plus
 * the location's fees, with the location's tax rates copied onto it; advisors reprice lines,
 * enter part costs and add fees or discounts. Issuing numbers and locks it; payments then move
 * it to PARTIALLY_PAID and PAID. An issued invoice with nothing paid can be voided.
 * <p>
 * Money: every line is rounded to cents; tax is worked out once per category (taxable total ×
 * rate, rounded), so totals don't drift with the number of lines.
 */
@Entity
@Table(name = "ws_invoices")
public class Invoice {

    private static final String ENTITY = "invoice";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "job_card_id", nullable = false, updatable = false)
    private UUID jobCardId;

    @Column(length = 20)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "customer_user_id", updatable = false)
    private UUID customerUserId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    @Column(nullable = false, length = 20)
    private String registration;

    @Column(nullable = false, length = 17)
    private String vin;

    @Column(nullable = false)
    private int mileage;

    @Column(name = "vehicle_description", length = 120)
    private String vehicleDescription;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "parts_tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal partsTaxRate;

    @Column(name = "labour_tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal labourTaxRate;

    @Column(name = "fees_tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal feesTaxRate;

    @Column(name = "shop_supplies_percent", precision = 6, scale = 3)
    private BigDecimal shopSuppliesPercent;

    @Column(name = "shop_supplies_cap", precision = 10, scale = 2)
    private BigDecimal shopSuppliesCap;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(length = 500)
    private String footer;

    @Column(length = 1000)
    private String notes;

    @Column(name = "parts_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsTotal = ZERO;

    @Column(name = "labour_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal labourTotal = ZERO;

    @Column(name = "fees_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal feesTotal = ZERO;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = ZERO;

    @Column(name = "parts_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsTax = ZERO;

    @Column(name = "labour_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal labourTax = ZERO;

    @Column(name = "fees_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal feesTax = ZERO;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal = ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = ZERO;

    @Column(name = "balance_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceDue = ZERO;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by", length = 100)
    private String issuedBy;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    @Column(name = "qbo_realm_id", length = 32)
    private String qboRealmId;

    @Column(name = "qbo_invoice_id", length = 32)
    private String qboInvoiceId;

    @Column(name = "qbo_customer_id", length = 32)
    private String qboCustomerId;

    @Column(name = "qbo_synced_at")
    private Instant qboSyncedAt;

    @Column(name = "qbo_voided_at")
    private Instant qboVoidedAt;

    @Column(name = "qbo_error", length = 500)
    private String qboError;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "emailed_to", length = 254)
    private String emailedTo;

    @Column(name = "emailed_by", length = 100)
    private String emailedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // Not final: JPA forbids final persistent fields, and Hibernate replaces each list on load.
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @SuppressWarnings("FieldMayBeFinal")
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("receivedAt ASC, id ASC")
    @SuppressWarnings("FieldMayBeFinal")
    private List<Payment> payments = new ArrayList<>();

    protected Invoice() {
        // for JPA
    }

    /**
     * A draft for a COMPLETED card: one line per part and labour entry, then the location's shop
     * supplies (share of labour) and disposal fees.
     */
    public static Invoice draft(JobCard card, BillingContact contact, LocationBilling billing, String createdBy,
            Instant now) {
        if (card.getStatus() != JobCardStatus.COMPLETED) {
            throw new BusinessRuleException("job-not-completed", "Only a completed job can be invoiced.");
        }
        Invoice invoice = new Invoice();
        invoice.id = UUID.randomUUID();
        invoice.locationId = card.getLocationId();
        invoice.jobCardId = card.getId();
        invoice.status = InvoiceStatus.DRAFT;
        invoice.customerUserId = card.getCustomerUserId();
        invoice.customerName = card.getCustomerName();
        if (contact != null) {
            invoice.customerId = contact.customerId();
            invoice.customerName = blankTo(contact.name(), card.getCustomerName());
            invoice.customerEmail = contact.email();
            invoice.customerPhone = contact.phone();
            invoice.vehicleDescription = vehicle(contact);
        }
        invoice.registration = card.getRegistration();
        invoice.vin = card.getVin();
        invoice.mileage = card.getMileage();
        invoice.currency = card.getCurrency();
        invoice.partsTaxRate = rate(billing.partsTaxRate());
        invoice.labourTaxRate = rate(billing.labourTaxRate());
        invoice.feesTaxRate = rate(billing.feesTaxRate());
        invoice.shopSuppliesPercent = billing.shopSuppliesPercent();
        invoice.shopSuppliesCap = billing.shopSuppliesCap();
        invoice.paymentTermsDays = billing.paymentTermsDays() == null ? 0 : billing.paymentTermsDays();
        invoice.footer = billing.invoiceFooter();
        invoice.createdAt = now;
        invoice.createdBy = createdBy;
        invoice.updatedAt = now;

        for (PartUsage part : card.getParts()) {
            invoice.add(InvoiceLine.create(invoice, LineType.PART, part.getName(), BigDecimal.valueOf(part.getQuantity()),
                    part.getUnitPrice(), TaxCategory.PARTS, true).fromPart(part.getId(), part.getPartNumber()));
        }
        for (LabourEntry entry : card.getLabour()) {
            String what = entry.getRecommendation() == null ? "Labour" : entry.getRecommendation().getDescription();
            invoice.add(InvoiceLine.create(invoice, LineType.LABOUR, "Labour: " + what, entry.getHours(),
                    entry.getHourlyRate(), TaxCategory.LABOUR, true).fromLabour(entry.getId()));
        }
        if (billing.shopSuppliesPercent() != null && billing.shopSuppliesPercent().signum() > 0) {
            invoice.add(InvoiceLine.create(invoice, LineType.FEE, "Shop supplies", BigDecimal.ONE, BigDecimal.ZERO,
                    TaxCategory.FEES, true).fee(InvoiceLine.SHOP_SUPPLIES));
        }
        if (billing.disposalFee() != null && billing.disposalFee().signum() > 0) {
            invoice.add(InvoiceLine.create(invoice, LineType.FEE, "Disposal fee", BigDecimal.ONE, billing.disposalFee(),
                    TaxCategory.FEES, true).fee(InvoiceLine.DISPOSAL));
        }
        invoice.recalculate();
        return invoice;
    }

    // ---- editing a draft ----------------------------------------------------------------------

    /** One line as the advisor wants it; id null = a new FEE or DISCOUNT line. */
    public record LineEdit(UUID id, LineType type, String description, String partNumber, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal unitCost, Boolean taxable, TaxCategory taxCategory) {
    }

    /**
     * Replaces the draft's lines with {@code edits}, in that order. Lines from the job card must
     * all be there (they can be repriced, not removed); FEE and DISCOUNT lines missing from the
     * list are removed, those without an id are added.
     */
    public void edit(List<LineEdit> edits, String notes, Instant now) {
        requireDraft("edit");
        Map<UUID, InvoiceLine> current = lines.stream().collect(Collectors.toMap(InvoiceLine::getId, Function.identity()));
        List<InvoiceLine> next = new ArrayList<>();
        for (LineEdit edit : edits) {
            InvoiceLine line;
            if (edit.id() != null) {
                line = current.remove(edit.id());
                if (line == null) {
                    throw new BusinessRuleException("line-not-found", "Line " + edit.id() + " is not on this invoice.");
                }
            } else {
                if (edit.type() == null || edit.type().fromJobCard()) {
                    throw new BusinessRuleException("invalid-line",
                            "New lines can only be fees or discounts; parts and labour come from the job card.");
                }
                TaxCategory category = edit.type() == LineType.FEE ? TaxCategory.FEES
                        : Objects.requireNonNullElse(edit.taxCategory(), TaxCategory.LABOUR);
                line = InvoiceLine.create(this, edit.type(), edit.description().strip(), edit.quantity(),
                        edit.unitPrice(), category, edit.taxable() == null || edit.taxable());
            }
            line.set(edit.description().strip(), blankToNull(edit.partNumber()), edit.quantity(), edit.unitPrice(),
                    edit.unitCost(), edit.taxable() == null ? line.isTaxable() : edit.taxable());
            if (line.getType() == LineType.DISCOUNT && edit.taxCategory() != null) {
                line.category(edit.taxCategory());
            }
            next.add(line);
        }
        if (current.values().stream().anyMatch(line -> line.getType().fromJobCard())) {
            throw new BusinessRuleException("job-card-line-removed",
                    "Parts and labour from the job card can be repriced but not removed.");
        }
        lines.clear();
        next.forEach(this::add);
        this.notes = blankToNull(notes);
        this.updatedAt = now;
        recalculate();
    }

    // ---- issuing, payments, voiding --------------------------------------------------------------

    /** Numbers and locks the draft; due after the location's payment terms. Nothing to pay = PAID. */
    public void issue(String number, String by, Instant now) {
        requireDraft("issue");
        recalculate();
        this.number = number;
        this.issuedAt = now;
        this.issuedBy = by;
        this.dueAt = now.plus(Duration.ofDays(paymentTermsDays));
        this.status = total.signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.ISSUED;
        this.updatedAt = now;
    }

    /**
     * Records money taken (or, negative, refunded). A payment can't exceed what is owed; a
     * refund can't exceed what was paid, and only {@code mayRefund} callers may refund.
     */
    public Payment pay(PaymentMethod method, BigDecimal amount, String reference, Instant receivedAt, String by,
            String idempotencyKey, boolean mayRefund, Instant now) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() == 0) {
            throw new BusinessRuleException("invalid-amount", "Enter an amount other than zero.");
        }
        if (value.signum() > 0) {
            if (!status.isOpen()) {
                throw new InvalidTransitionException(ENTITY, status, "take a payment on");
            }
            if (value.compareTo(balanceDue) > 0) {
                throw new BusinessRuleException("overpayment",
                        "That is more than the " + balanceDue.toPlainString() + " still owed.");
            }
        } else {
            if (!mayRefund) {
                throw new BusinessRuleException("refund-not-allowed", "Only a manager or admin can refund.");
            }
            if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.VOID) {
                throw new InvalidTransitionException(ENTITY, status, "refund");
            }
            if (value.negate().compareTo(amountPaid) > 0) {
                throw new BusinessRuleException("refund-too-large",
                        "That is more than the " + amountPaid.toPlainString() + " paid.");
            }
        }
        Payment payment = Payment.create(this, method, value, blankToNull(reference), receivedAt, by, idempotencyKey,
                now);
        payments.add(payment);
        amountPaid = amountPaid.add(value);
        balanceDue = total.subtract(amountPaid);
        status = balanceDue.signum() == 0 ? InvoiceStatus.PAID
                : amountPaid.signum() > 0 ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.ISSUED;
        updatedAt = now;
        return payment;
    }

    /** Cancels an issued invoice (its number stays used). Payments must be refunded first. */
    public void voidInvoice(String reason, String by, Instant now) {
        if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.VOID) {
            throw new InvalidTransitionException(ENTITY, status, "void");
        }
        if (amountPaid.signum() != 0) {
            throw new BusinessRuleException("invoice-has-payments",
                    "Refund the " + amountPaid.toPlainString() + " paid before voiding this invoice.");
        }
        this.status = InvoiceStatus.VOID;
        this.voidReason = reason.strip();
        this.voidedBy = by;
        this.voidedAt = now;
        this.balanceDue = ZERO;
        this.updatedAt = now;
    }

    /** Only issued invoices (not drafts, not voided) go to the customer. */
    public void requireSendable(String action) {
        if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.VOID) {
            throw new InvalidTransitionException(ENTITY, status, action);
        }
    }

    /** Records an e-mail to the customer; an address typed in for a customer without one is kept. */
    public void markEmailed(String to, String by, Instant now) {
        requireSendable("e-mail");
        if (customerEmail == null) {
            customerEmail = to;
        }
        this.emailedAt = now;
        this.emailedTo = to;
        this.emailedBy = by;
        this.updatedAt = now;
    }

    // ---- QuickBooks -----------------------------------------------------------------------------

    /** Sent to this QuickBooks company (connected to another one later, it counts as not sent). */
    public boolean isInQuickBooks(String realmId) {
        return qboInvoiceId != null && realmId != null && realmId.equals(qboRealmId);
    }

    /** Created in QuickBooks; payments sent to an earlier company are sent again. */
    public void sentToQuickBooks(String realmId, String invoiceId, String customerId, Instant now) {
        if (!realmId.equals(qboRealmId)) {
            payments.forEach(Payment::notInQuickBooks);
            qboVoidedAt = null;
        }
        this.qboRealmId = realmId;
        this.qboInvoiceId = invoiceId;
        this.qboCustomerId = customerId;
        this.qboSyncedAt = now;
    }

    public void voidedInQuickBooks(Instant now) {
        this.qboVoidedAt = now;
    }

    public void syncedWithQuickBooks(Instant now) {
        this.qboSyncedAt = now;
        this.qboError = null;
    }

    public void quickBooksFailed(String error) {
        String message = error == null || error.isBlank() ? "QuickBooks refused the invoice." : error.strip();
        this.qboError = message.length() > 500 ? message.substring(0, 500) : message;
    }

    public void requireDraft(String action) {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvalidTransitionException(ENTITY, status, action);
        }
    }

    // ---- totals ------------------------------------------------------------------------------

    /** Shop supplies follow labour; then subtotals, tax per category, total and balance. */
    void recalculate() {
        BigDecimal labour = sum(LineType.LABOUR);
        for (InvoiceLine line : lines) {
            if (InvoiceLine.SHOP_SUPPLIES.equals(line.getFeeCode()) && shopSuppliesPercent != null) {
                BigDecimal fee = labour.multiply(shopSuppliesPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (shopSuppliesCap != null && fee.compareTo(shopSuppliesCap) > 0) {
                    fee = shopSuppliesCap;
                }
                line.price(fee);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).position(i);
        }
        partsTotal = sum(LineType.PART);
        labourTotal = labour;
        feesTotal = sum(LineType.FEE);
        discountTotal = sum(LineType.DISCOUNT).negate();
        partsTax = tax(TaxCategory.PARTS, partsTaxRate);
        labourTax = tax(TaxCategory.LABOUR, labourTaxRate);
        feesTax = tax(TaxCategory.FEES, feesTaxRate);
        taxTotal = partsTax.add(labourTax).add(feesTax);
        total = subtotal().add(taxTotal);
        balanceDue = status == InvoiceStatus.VOID ? ZERO : total.subtract(amountPaid);
    }

    /** Before tax: parts + labour + fees − discounts. */
    public BigDecimal subtotal() {
        return partsTotal.add(labourTotal).add(feesTotal).subtract(discountTotal);
    }

    private BigDecimal tax(TaxCategory category, BigDecimal rate) {
        BigDecimal taxable = lines.stream()
                .filter(line -> line.isTaxable() && line.getTaxCategory() == category)
                .map(InvoiceLine::getLineTotal)
                .reduce(ZERO, BigDecimal::add);
        if (taxable.signum() <= 0 || rate.signum() == 0) {
            return ZERO;
        }
        return taxable.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(LineType type) {
        return lines.stream().filter(line -> line.getType() == type).map(InvoiceLine::getLineTotal)
                .reduce(ZERO, BigDecimal::add);
    }

    /** Parts sold on lines with a cost entered, and what those parts cost: for profit. */
    public BigDecimal costedPartsTotal() {
        return lines.stream().filter(l -> l.getType() == LineType.PART && l.getUnitCost() != null)
                .map(InvoiceLine::getLineTotal).reduce(ZERO, BigDecimal::add);
    }

    public BigDecimal partsCost() {
        return lines.stream().filter(l -> l.getType() == LineType.PART && l.getUnitCost() != null)
                .map(InvoiceLine::costTotal).reduce(ZERO, BigDecimal::add);
    }

    private void add(InvoiceLine line) {
        line.position(lines.size());
        lines.add(line);
    }

    /** Loads the lines and payments while the transaction is open (see JobCard.loadDetails). */
    public void loadDetails() {
        Hibernate.initialize(lines);
        Hibernate.initialize(payments);
    }

    private static BigDecimal rate(BigDecimal rate) {
        return rate == null ? BigDecimal.ZERO : rate;
    }

    private static String vehicle(BillingContact contact) {
        String text = ((contact.vehicleYear() == null ? "" : contact.vehicleYear() + " ")
                + Objects.toString(contact.vehicleMake(), "") + " " + Objects.toString(contact.vehicleModel(), ""))
                .trim().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text.length() > 120 ? text.substring(0, 120) : text;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLocationId() { return locationId; }
    public UUID getJobCardId() { return jobCardId; }
    public String getNumber() { return number; }
    public InvoiceStatus getStatus() { return status; }
    public UUID getCustomerUserId() { return customerUserId; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public String getRegistration() { return registration; }
    public String getVin() { return vin; }
    public int getMileage() { return mileage; }
    public String getVehicleDescription() { return vehicleDescription; }
    public String getCurrency() { return currency; }
    public BigDecimal getPartsTaxRate() { return partsTaxRate; }
    public BigDecimal getLabourTaxRate() { return labourTaxRate; }
    public BigDecimal getFeesTaxRate() { return feesTaxRate; }
    public int getPaymentTermsDays() { return paymentTermsDays; }
    public String getFooter() { return footer; }
    public String getNotes() { return notes; }
    public BigDecimal getPartsTotal() { return partsTotal; }
    public BigDecimal getLabourTotal() { return labourTotal; }
    public BigDecimal getFeesTotal() { return feesTotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getPartsTax() { return partsTax; }
    public BigDecimal getLabourTax() { return labourTax; }
    public BigDecimal getFeesTax() { return feesTax; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getBalanceDue() { return balanceDue; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getIssuedBy() { return issuedBy; }
    public Instant getDueAt() { return dueAt; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidedBy() { return voidedBy; }
    public String getVoidReason() { return voidReason; }
    public Instant getEmailedAt() { return emailedAt; }
    public String getQboRealmId() { return qboRealmId; }
    public String getQboInvoiceId() { return qboInvoiceId; }
    public String getQboCustomerId() { return qboCustomerId; }
    public Instant getQboSyncedAt() { return qboSyncedAt; }
    public Instant getQboVoidedAt() { return qboVoidedAt; }
    public String getQboError() { return qboError; }
    public String getEmailedTo() { return emailedTo; }
    public String getEmailedBy() { return emailedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public List<InvoiceLine> getLines() { return Collections.unmodifiableList(lines); }
    public List<Payment> getPayments() { return Collections.unmodifiableList(payments); }
}
