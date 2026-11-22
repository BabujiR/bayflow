package com.bayflow.workshop.invoicing;

import java.math.BigDecimal;
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

import org.hibernate.annotations.TenantId;

/** Money taken against an invoice (negative: a refund). Never changed once recorded. */
@Entity
@Table(name = "ws_payments")
public class Payment {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 100, updatable = false)
    private String reference;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "received_by", nullable = false, length = 100, updatable = false)
    private String receivedBy;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
        // for JPA
    }

    static Payment create(Invoice invoice, PaymentMethod method, BigDecimal amount, String reference,
            Instant receivedAt, String receivedBy, String idempotencyKey, Instant now) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.locationId = invoice.getLocationId();
        payment.invoice = invoice;
        payment.method = method;
        payment.amount = amount;
        payment.reference = reference;
        payment.receivedAt = receivedAt;
        payment.receivedBy = receivedBy;
        payment.idempotencyKey = idempotencyKey;
        payment.createdAt = now;
        return payment;
    }

    public boolean isRefund() {
        return amount.signum() < 0;
    }

    public UUID getId() { return id; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getReceivedBy() { return receivedBy; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
