package com.bayflow.workshop.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.reports.ReportPeriod;

/**
 * Issued invoices (with their lines) and payments of a period, for the accountant: the web app
 * turns them into CSV files, one of them in QuickBooks Online's invoice-import layout.
 */
@Service
public class InvoiceExportService {

    /** More than this in one period: the export asks for a shorter one. */
    static final int MAX_INVOICES = 5_000;
    static final int MAX_PAYMENTS = 10_000;

    public record ExportLine(LineType type, String description, String partNumber, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal lineTotal, boolean taxable, TaxCategory taxCategory) {
    }

    public record ExportInvoice(UUID id, String number, InvoiceStatus status, UUID locationId, String customerName,
            String customerEmail, String customerPhone, String registration, String vin, String vehicleDescription,
            String currency, Instant issuedAt, Instant dueAt, int paymentTermsDays, List<ExportLine> lines,
            BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total, BigDecimal amountPaid,
            BigDecimal balanceDue) {

        static ExportInvoice from(Invoice i) {
            return new ExportInvoice(i.getId(), i.getNumber(), i.getStatus(), i.getLocationId(), i.getCustomerName(),
                    i.getCustomerEmail(), i.getCustomerPhone(), i.getRegistration(), i.getVin(),
                    i.getVehicleDescription(), i.getCurrency(), i.getIssuedAt(), i.getDueAt(),
                    i.getPaymentTermsDays(),
                    i.getLines().stream().map(l -> new ExportLine(l.getType(), l.getDescription(), l.getPartNumber(),
                            l.getQuantity(), l.getUnitPrice(), l.getLineTotal(), l.isTaxable(), l.getTaxCategory()))
                            .toList(),
                    i.subtotal(), i.getTaxTotal(), i.getTotal(), i.getAmountPaid(), i.getBalanceDue());
        }
    }

    public record ExportPayment(UUID id, UUID invoiceId, String invoiceNumber, UUID locationId, String customerName,
            String currency, PaymentMethod method, BigDecimal amount, String reference, Instant receivedAt,
            String receivedBy) {

        static ExportPayment from(Payment p) {
            Invoice i = p.getInvoice();
            return new ExportPayment(p.getId(), i.getId(), i.getNumber(), i.getLocationId(), i.getCustomerName(),
                    i.getCurrency(), p.getMethod(), p.getAmount(), p.getReference(), p.getReceivedAt(),
                    p.getReceivedBy());
        }
    }

    /** Invoices issued in the period (not voided ones) and payments received in it. */
    public record InvoiceExport(Instant from, Instant to, List<ExportInvoice> invoices, List<ExportPayment> payments) {
    }

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    public InvoiceExportService(InvoiceRepository invoices, PaymentRepository payments) {
        this.invoices = invoices;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public InvoiceExport export(ReportPeriod period) {
        Specification<Invoice> issued = (root, query, cb) -> cb.and(
                root.get("status").in(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID),
                cb.greaterThanOrEqualTo(root.get("issuedAt"), period.from()),
                cb.lessThan(root.get("issuedAt"), period.to()));
        List<Invoice> found = invoices.findAll(issued,
                PageRequest.of(0, MAX_INVOICES + 1, Sort.by("issuedAt", "number"))).getContent();
        List<Payment> received = payments.findReceivedBetween(period.from(), period.to(),
                PageRequest.of(0, MAX_PAYMENTS + 1));
        if (found.size() > MAX_INVOICES || received.size() > MAX_PAYMENTS) {
            throw ApiException.badRequest("export-too-large",
                    "That period has too many invoices for one file. Choose a shorter period.");
        }
        found.forEach(Invoice::loadDetails);
        return new InvoiceExport(period.from(), period.to(), found.stream().map(ExportInvoice::from).toList(),
                received.stream().map(ExportPayment::from).toList());
    }
}
