package com.bayflow.workshop.quickbooks;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.domain.InvalidTransitionException;
import com.bayflow.workshop.integration.AuthServiceClient;
import com.bayflow.workshop.invoicing.Invoice;
import com.bayflow.workshop.invoicing.InvoiceLine;
import com.bayflow.workshop.invoicing.InvoiceRepository;
import com.bayflow.workshop.invoicing.InvoiceStatus;
import com.bayflow.workshop.invoicing.LineType;
import com.bayflow.workshop.invoicing.Payment;
import com.bayflow.workshop.invoicing.TaxCategory;
import com.bayflow.workshop.quickbooks.QuickBooksApi.QuickBooksException;
import com.bayflow.workshop.quickbooks.QuickBooksService.Access;

import tools.jackson.databind.JsonNode;

/**
 * "Send to QuickBooks" on an issued invoice: finds or creates the customer, creates the invoice
 * (Ramplight's number, lines on the mapped products/services, discounts as one discount line,
 * and Ramplight's tax total, which QuickBooks keeps), then the payments not sent yet, and voids
 * it there once it is voided here. Sending again only sends what is new. Refunds are left for
 * the bookkeeper, since QuickBooks books them as refund receipts against a bank account.
 */
@Service
public class QuickBooksSync {

    private static final Logger log = LoggerFactory.getLogger(QuickBooksSync.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final InvoiceRepository invoices;
    private final QuickBooksService quickbooks;
    private final QuickBooksApi api;
    private final AuthServiceClient auth;
    private final Clock clock;

    public QuickBooksSync(InvoiceRepository invoices, QuickBooksService quickbooks, QuickBooksApi api,
            AuthServiceClient auth, Clock clock) {
        this.invoices = invoices;
        this.quickbooks = quickbooks;
        this.api = api;
        this.auth = auth;
        this.clock = clock;
    }

    /**
     * Sends what QuickBooks doesn't have yet. A QuickBooks error is kept on the invoice (and
     * returned with it), together with whatever was sent before the error.
     */
    @Transactional
    public Invoice send(CurrentUser user, UUID id) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
        invoice.loadDetails();
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new InvalidTransitionException("invoice", invoice.getStatus(), "send to QuickBooks");
        }
        QboConnection connection = quickbooks.current();
        if (!connection.isMapped()) {
            throw ApiException.conflict("quickbooks-not-ready",
                    "Choose the QuickBooks products/services for parts, labour and fees first (Settings → QuickBooks).");
        }
        boolean sent = invoice.isInQuickBooks(connection.getRealmId());
        if (invoice.getStatus() == InvoiceStatus.VOID && !sent) {
            throw ApiException.conflict("quickbooks-nothing-to-send",
                    "This invoice was voided before it was sent to QuickBooks, so there is nothing to send.");
        }
        ZoneId zone = zone(invoice);
        Instant now = clock.instant();
        try {
            quickbooks.call(access -> {
                if (!invoice.isInQuickBooks(access.realmId())) {
                    String customerId = customer(access, invoice);
                    JsonNode created = api.post(access.realmId(), access.token(), "invoice", null,
                            invoiceBody(invoice, connection, customerId, zone)).path("Invoice");
                    invoice.sentToQuickBooks(access.realmId(), QuickBooksService.text(created.path("Id")), customerId,
                            now);
                }
                for (Payment payment : invoice.getPayments()) {
                    if (payment.getQboPaymentId() == null && payment.getAmount().signum() > 0) {
                        JsonNode created = api.post(access.realmId(), access.token(), "payment", null,
                                paymentBody(invoice, payment, connection, zone)).path("Payment");
                        payment.sentToQuickBooks(QuickBooksService.text(created.path("Id")));
                    }
                }
                if (invoice.getStatus() == InvoiceStatus.VOID && invoice.getQboVoidedAt() == null) {
                    JsonNode current = api.get(access.realmId(), access.token(), "invoice/" + invoice.getQboInvoiceId())
                            .path("Invoice");
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("Id", invoice.getQboInvoiceId());
                    body.put("SyncToken", QuickBooksService.text(current.path("SyncToken")));
                    api.post(access.realmId(), access.token(), "invoice", "void", body);
                    invoice.voidedInQuickBooks(now);
                }
                return null;
            });
            invoice.syncedWithQuickBooks(now);
            log.info("Invoice {} sent to QuickBooks by {}", invoice.getNumber(), user.username());
        } catch (QuickBooksException e) {
            log.warn("Sending invoice {} to QuickBooks failed: {}", invoice.getNumber(), e.getMessage());
            invoice.quickBooksFailed(e.getMessage());
        }
        Invoice saved = invoices.saveAndFlush(invoice);
        saved.loadDetails();
        return saved;
    }

    // ---- request bodies ----------------------------------------------------------------------------

    /** The customer with the invoice's name, created (with e-mail and phone) when QuickBooks has none. */
    private String customer(Access access, Invoice invoice) {
        String name = displayName(invoice.getCustomerName());
        JsonNode found = api.query(access.realmId(), access.token(),
                "select * from Customer where DisplayName = '" + name.replace("\\", "\\\\").replace("'", "\\'") + "'")
                .path("Customer").path(0);
        if (!found.isMissingNode()) {
            return QuickBooksService.text(found.path("Id"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("DisplayName", name);
        if (invoice.getCustomerEmail() != null) {
            body.put("PrimaryEmailAddr", Map.of("Address", invoice.getCustomerEmail()));
        }
        if (invoice.getCustomerPhone() != null) {
            body.put("PrimaryPhone", Map.of("FreeFormNumber", invoice.getCustomerPhone()));
        }
        return QuickBooksService.text(api.post(access.realmId(), access.token(), "customer", null, body)
                .path("Customer").path("Id"));
    }

    static Map<String, Object> invoiceBody(Invoice invoice, QboConnection mapping, String customerId, ZoneId zone) {
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal discounts = BigDecimal.ZERO;
        for (InvoiceLine line : invoice.getLines()) {
            if (line.getType() == LineType.DISCOUNT) {
                discounts = discounts.add(line.getLineTotal().abs());
                continue;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ItemRef", Map.of("value", itemFor(line.getType(), mapping)));
            detail.put("Qty", line.getQuantity());
            detail.put("UnitPrice", line.getUnitPrice());
            detail.put("TaxCodeRef", Map.of("value", taxed(invoice, line) ? "TAX" : "NON"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DetailType", "SalesItemLineDetail");
            row.put("Amount", line.getLineTotal());
            row.put("Description", limit(line.getPartNumber() == null ? line.getDescription()
                    : line.getDescription() + " (" + line.getPartNumber() + ")", 4000));
            row.put("SalesItemLineDetail", detail);
            lines.add(row);
        }
        if (discounts.signum() > 0) {
            lines.add(Map.of("DetailType", "DiscountLineDetail", "Amount", discounts,
                    "DiscountLineDetail", Map.of("PercentBased", false)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("DocNumber", invoice.getNumber());
        body.put("TxnDate", date(invoice.getIssuedAt(), zone));
        if (invoice.getDueAt() != null) {
            body.put("DueDate", date(invoice.getDueAt(), zone));
        }
        body.put("CustomerRef", Map.of("value", customerId));
        if (invoice.getCustomerEmail() != null) {
            body.put("BillEmail", Map.of("Address", invoice.getCustomerEmail()));
        }
        body.put("Line", lines);
        // Ramplight worked out the tax; QuickBooks keeps a given total (and spreads it over its tax code).
        body.put("TxnTaxDetail", Map.of("TotalTax", invoice.getTaxTotal()));
        if (invoice.getNotes() != null) {
            body.put("CustomerMemo", Map.of("value", limit(invoice.getNotes(), 1000)));
        }
        body.put("PrivateNote", limit("From Ramplight. Vehicle: " + invoice.getRegistration()
                + (invoice.getVehicleDescription() == null ? "" : ", " + invoice.getVehicleDescription())
                + ", VIN " + invoice.getVin() + ".", 4000));
        return body;
    }

    private static Map<String, Object> paymentBody(Invoice invoice, Payment payment, QboConnection mapping,
            ZoneId zone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("CustomerRef", Map.of("value", invoice.getQboCustomerId()));
        body.put("TotalAmt", payment.getAmount());
        body.put("TxnDate", date(payment.getReceivedAt(), zone));
        if (payment.getReference() != null) {
            body.put("PaymentRefNum", limit(payment.getReference(), 21));
        }
        body.put("PrivateNote", limit("Ramplight " + invoice.getNumber() + ": " + payment.getMethod()
                + " recorded by " + payment.getReceivedBy(), 4000));
        if (mapping.getDepositAccountId() != null) {
            body.put("DepositToAccountRef", Map.of("value", mapping.getDepositAccountId()));
        }
        body.put("Line", List.of(Map.of("Amount", payment.getAmount(),
                "LinkedTxn", List.of(Map.of("TxnId", invoice.getQboInvoiceId(), "TxnType", "Invoice")))));
        return body;
    }

    private static String itemFor(LineType type, QboConnection mapping) {
        return switch (type) {
            case PART -> mapping.getPartsItemId();
            case LABOUR -> mapping.getLabourItemId();
            default -> mapping.getFeesItemId();
        };
    }

    /** Taxed when the line is taxable and its category has a rate on this invoice. */
    private static boolean taxed(Invoice invoice, InvoiceLine line) {
        if (!line.isTaxable()) {
            return false;
        }
        BigDecimal rate = line.getTaxCategory() == TaxCategory.PARTS ? invoice.getPartsTaxRate()
                : line.getTaxCategory() == TaxCategory.LABOUR ? invoice.getLabourTaxRate() : invoice.getFeesTaxRate();
        return rate != null && rate.signum() > 0;
    }

    /** QuickBooks display names: no colons (they mean sub-customer), at most 100 characters. */
    private static String displayName(String name) {
        String clean = name.replace(':', '-').strip();
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }

    private ZoneId zone(Invoice invoice) {
        try {
            String id = auth.location(TenantContext.require(), invoice.getLocationId()).timeZone();
            return id == null || id.isBlank() ? ZoneOffset.UTC : ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static String date(Instant instant, ZoneId zone) {
        return DATE.format(instant.atZone(zone).toLocalDate());
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
