package com.bayflow.workshop.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.bayflow.workshop.invoicing.Invoice.LineEdit;

/** Request and response bodies of the invoice API. */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    // ---- requests ---------------------------------------------------------------------------

    /** One line of a draft as edited; id null = a new FEE or DISCOUNT. */
    public record LineRequest(
            UUID id,
            LineType type,
            @NotBlank @Size(max = 200) String description,
            @Size(max = 50) String partNumber,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
            Boolean taxable,
            TaxCategory taxCategory) {

        LineEdit toEdit() {
            return new LineEdit(id, type, description, partNumber, quantity, unitPrice, unitCost, taxable, taxCategory);
        }
    }

    public record UpdateInvoiceRequest(
            @NotNull @Size(max = 200) List<@Valid LineRequest> lines,
            @Size(max = 1000) String notes,
            @NotNull Long version) {
    }

    public record IssueRequest(@NotNull Long version) {
    }

    /** Negative amount = a refund (managers and admins). receivedAt null = now. */
    public record PaymentRequest(
            @NotNull PaymentMethod method,
            @NotNull @DecimalMin("-9999999999.99") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 100) String reference,
            Instant receivedAt) {
    }

    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {
    }

    // ---- responses --------------------------------------------------------------------------

    /** unitCost: staff only (null for customers). */
    public record LineResponse(UUID id, LineType type, String feeCode, String description, String partNumber,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost, BigDecimal lineTotal, boolean taxable,
            TaxCategory taxCategory, boolean fromJobCard) {

        static LineResponse from(InvoiceLine l, boolean staff) {
            return new LineResponse(l.getId(), l.getType(), l.getFeeCode(), l.getDescription(), l.getPartNumber(),
                    l.getQuantity(), l.getUnitPrice(), staff ? l.getUnitCost() : null, l.getLineTotal(), l.isTaxable(),
                    l.getTaxCategory(), l.getType().fromJobCard());
        }
    }

    public record PaymentResponse(UUID id, PaymentMethod method, BigDecimal amount, String reference,
            Instant receivedAt, String receivedBy) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getMethod(), p.getAmount(), p.getReference(), p.getReceivedAt(),
                    p.getReceivedBy());
        }
    }

    /** Tax of one category: its rate in percent (copied from the location) and the tax. */
    public record TaxResponse(TaxCategory category, BigDecimal rate, BigDecimal tax) {
    }

    /** Profit on the parts that have a cost entered (staff only). */
    public record PartsProfit(BigDecimal partsWithCost, BigDecimal partsCost, BigDecimal profit) {
    }

    public record InvoiceResponse(UUID id, String number, InvoiceStatus status, UUID jobCardId, UUID locationId,
            UUID customerUserId, String customerName, String customerEmail, String customerPhone,
            String registration, String vin, int mileage, String vehicleDescription, String currency,
            List<LineResponse> lines, BigDecimal partsTotal, BigDecimal labourTotal, BigDecimal feesTotal,
            BigDecimal discountTotal, BigDecimal subtotal, List<TaxResponse> taxes, BigDecimal taxTotal,
            BigDecimal total, BigDecimal amountPaid, BigDecimal balanceDue, List<PaymentResponse> payments,
            String notes, String footer, int paymentTermsDays, Instant issuedAt, String issuedBy, Instant dueAt,
            Instant voidedAt, String voidedBy, String voidReason, Instant createdAt, String createdBy,
            PartsProfit partsProfit, long version) {

        static InvoiceResponse from(Invoice i, boolean staff) {
            List<TaxResponse> taxes = List.of(
                    new TaxResponse(TaxCategory.PARTS, i.getPartsTaxRate(), i.getPartsTax()),
                    new TaxResponse(TaxCategory.LABOUR, i.getLabourTaxRate(), i.getLabourTax()),
                    new TaxResponse(TaxCategory.FEES, i.getFeesTaxRate(), i.getFeesTax()));
            PartsProfit profit = staff ? new PartsProfit(i.costedPartsTotal(), i.partsCost(),
                    i.costedPartsTotal().subtract(i.partsCost())) : null;
            return new InvoiceResponse(i.getId(), i.getNumber(), i.getStatus(), i.getJobCardId(), i.getLocationId(),
                    i.getCustomerUserId(), i.getCustomerName(), i.getCustomerEmail(), i.getCustomerPhone(),
                    i.getRegistration(), i.getVin(), i.getMileage(), i.getVehicleDescription(), i.getCurrency(),
                    i.getLines().stream().map(l -> LineResponse.from(l, staff)).toList(), i.getPartsTotal(),
                    i.getLabourTotal(), i.getFeesTotal(), i.getDiscountTotal(), i.subtotal(), taxes, i.getTaxTotal(),
                    i.getTotal(), i.getAmountPaid(), i.getBalanceDue(),
                    i.getPayments().stream().map(PaymentResponse::from).toList(), i.getNotes(), i.getFooter(),
                    i.getPaymentTermsDays(), i.getIssuedAt(), i.getIssuedBy(), i.getDueAt(), i.getVoidedAt(),
                    i.getVoidedBy(), i.getVoidReason(), i.getCreatedAt(), i.getCreatedBy(), profit,
                    i.getVersion() == null ? 0 : i.getVersion());
        }
    }

    /** A row of the invoice list. */
    public record InvoiceSummary(UUID id, String number, InvoiceStatus status, UUID jobCardId, UUID locationId,
            String customerName, String registration, String currency, BigDecimal total, BigDecimal balanceDue,
            Instant issuedAt, Instant dueAt, Instant createdAt) {

        static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getNumber(), i.getStatus(), i.getJobCardId(), i.getLocationId(),
                    i.getCustomerName(), i.getRegistration(), i.getCurrency(), i.getTotal(), i.getBalanceDue(),
                    i.getIssuedAt(), i.getDueAt(), i.getCreatedAt());
        }
    }
}
