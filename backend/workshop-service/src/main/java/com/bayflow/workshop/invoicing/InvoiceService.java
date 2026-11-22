package com.bayflow.workshop.invoicing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.application.OutboxService;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.infrastructure.JobCardRepository;
import com.bayflow.workshop.integration.BillingContactClient;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;
import com.bayflow.workshop.integration.WorkshopSettingsClient;
import com.bayflow.workshop.integration.WorkshopSettingsClient.LocationBilling;
import com.bayflow.workshop.invoicing.Invoice.LineEdit;

/**
 * Invoices for completed job cards. Role checks are on the API; here: staff see the invoices of
 * their locations (row-level security), customers only their own issued ones, and who may
 * refund or void.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    /** Filters for the invoice list. status UNPAID = issued or partly paid. */
    public record InvoiceFilter(String status, Instant from, Instant to, String query) {
    }

    /** A payment as recorded at the counter; receivedAt null = now. */
    public record PaymentCommand(PaymentMethod method, BigDecimal amount, String reference, Instant receivedAt) {
    }

    private final InvoiceRepository invoices;
    private final JobCardRepository jobCards;
    private final InvoiceNumbers numbers;
    private final WorkshopSettingsClient settings;
    private final BillingContactClient contacts;
    private final OutboxService outbox;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, JobCardRepository jobCards, InvoiceNumbers numbers,
            WorkshopSettingsClient settings, BillingContactClient contacts, OutboxService outbox, Clock clock) {
        this.invoices = invoices;
        this.jobCards = jobCards;
        this.numbers = numbers;
        this.settings = settings;
        this.contacts = contacts;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** A draft for a completed job card; 409 if the card already has an invoice that isn't void. */
    @Transactional
    public Invoice createDraft(CurrentUser user, UUID jobCardId) {
        JobCard card = jobCards.findById(jobCardId).orElseThrow(() -> ApiException.notFound("Job card"));
        invoices.findFirstByJobCardIdAndStatusNot(jobCardId, InvoiceStatus.VOID).ifPresent(existing -> {
            throw ApiException.conflict("invoice-exists", "This job already has invoice "
                    + (existing.getNumber() == null ? "(draft)" : existing.getNumber()) + ".");
        });
        UUID tenant = TenantContext.require();
        LocationBilling billing;
        try {
            billing = settings.get(tenant, card.getLocationId()).billingOrNone();
        } catch (WorkshopSettingsClient.UnknownLocationException e) {
            throw ApiException.conflict("unknown-location", "The job's location is no longer one of the organization's.");
        } catch (RuntimeException e) {
            log.warn("Could not read the tax settings of location {}: {}", card.getLocationId(), e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "settings-unavailable", "Service Unavailable",
                    "The location's tax settings could not be read. Try again in a moment.");
        }
        BillingContact contact = null;
        try {
            contact = contacts.get(tenant, card.getAppointmentId());
        } catch (RuntimeException e) {
            // The card has the customer's name; email, phone and vehicle model are a bonus.
            log.warn("Could not read the billing contact of appointment {}: {}", card.getAppointmentId(), e.toString());
        }
        Invoice invoice = Invoice.draft(card, contact, billing, user.username(), clock.instant());
        return loaded(invoices.save(invoice));
    }

    @Transactional(readOnly = true)
    public Invoice get(CurrentUser user, UUID id) {
        return loaded(visible(user, invoices.findById(id)));
    }

    /** The job card's invoice (not voided), as the caller may see it. */
    @Transactional(readOnly = true)
    public Invoice forJobCard(CurrentUser user, UUID jobCardId) {
        return loaded(visible(user, invoices.findFirstByJobCardIdAndStatusNot(jobCardId, InvoiceStatus.VOID)));
    }

    @Transactional(readOnly = true)
    public Page<Invoice> list(CurrentUser user, InvoiceFilter filter, int page, int size) {
        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (user.isCustomer()) {
                where.add(cb.equal(root.get("customerUserId"), customerId(user)));
                where.add(cb.notEqual(root.get("status"), InvoiceStatus.DRAFT));
            }
            if (filter.status() != null && !filter.status().isBlank()) {
                if ("UNPAID".equalsIgnoreCase(filter.status())) {
                    where.add(root.get("status").in(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID));
                } else {
                    where.add(cb.equal(root.get("status"), status(filter.status())));
                }
            }
            // Drafts by when they were made, the rest by when they were issued.
            var when = cb.coalesce(root.<Instant>get("issuedAt"), root.<Instant>get("createdAt"));
            if (filter.from() != null) {
                where.add(cb.greaterThanOrEqualTo(when, filter.from()));
            }
            if (filter.to() != null) {
                where.add(cb.lessThan(when, filter.to()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String like = "%" + filter.query().strip().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("number"), "")), like),
                        cb.like(cb.lower(root.<String>get("customerName")), like),
                        cb.like(cb.lower(root.<String>get("registration")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Sort newest = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        return invoices.findAll(spec, PageRequest.of(page, size, newest));
    }

    /** Replaces a draft's lines and notes; {@code version} must be the one read (409 otherwise). */
    @Transactional
    public Invoice edit(UUID id, long version, List<LineEdit> lines, String notes) {
        Invoice invoice = staffInvoice(id);
        checkVersion(invoice, version);
        invoice.edit(lines, notes, clock.instant());
        return loaded(invoices.saveAndFlush(invoice));
    }

    /** Deletes a draft (it never had a number); the job can be invoiced again. */
    @Transactional
    public void discard(UUID id) {
        Invoice invoice = staffInvoice(id);
        invoice.requireDraft("discard");
        invoices.delete(invoice);
    }

    /** Numbers, locks and sends the invoice to the customer (in-app notification). */
    @Transactional
    public Invoice issue(CurrentUser user, UUID id, long version) {
        Invoice invoice = staffInvoice(id);
        checkVersion(invoice, version);
        invoice.requireDraft("issue");
        invoice.issue(numbers.next(TenantContext.require()), user.username(), clock.instant());
        Invoice saved = invoices.saveAndFlush(invoice);
        jobCards.findById(saved.getJobCardId()).ifPresent(card -> outbox.enqueue(card, OutboxService.INVOICE_ISSUED));
        return loaded(saved);
    }

    /**
     * Records a payment (or, negative, a refund: managers and admins). The same idempotency key
     * twice records it once, so a double click or a retry is harmless.
     */
    @Transactional
    public Invoice pay(CurrentUser user, UUID id, PaymentCommand command, String idempotencyKey) {
        Invoice invoice = staffInvoice(id);
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.strip();
        if (key != null && invoice.getPayments().stream().anyMatch(p -> key.equals(p.getIdempotencyKey()))) {
            return loaded(invoice);
        }
        Instant now = clock.instant();
        invoice.pay(command.method(), command.amount(), command.reference(),
                command.receivedAt() == null ? now : command.receivedAt(), user.username(), key,
                user.isAdmin() || user.isManager(), now);
        return loaded(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public Invoice voidInvoice(CurrentUser user, UUID id, String reason) {
        Invoice invoice = staffInvoice(id);
        invoice.voidInvoice(reason, user.username(), clock.instant());
        return loaded(invoices.saveAndFlush(invoice));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private Invoice staffInvoice(UUID id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    /** Customers see only their own invoices, and never drafts; staff see what RLS lets through. */
    private static Invoice visible(CurrentUser user, Optional<Invoice> found) {
        Invoice invoice = found.orElseThrow(() -> ApiException.notFound("Invoice"));
        if (user.isCustomer() && (invoice.getStatus() == InvoiceStatus.DRAFT
                || !customerId(user).equals(invoice.getCustomerUserId()))) {
            throw ApiException.notFound("Invoice");
        }
        return invoice;
    }

    private static UUID customerId(CurrentUser user) {
        try {
            return UUID.fromString(user.subject());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Invoice");
        }
    }

    private static InvoiceStatus status(String value) {
        try {
            return InvoiceStatus.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid-status", "Unknown invoice status: " + value);
        }
    }

    private static void checkVersion(Invoice invoice, long version) {
        if (invoice.getVersion() != null && invoice.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "The invoice was changed by someone else. Reload and try again.");
        }
    }

    private static Invoice loaded(Invoice invoice) {
        invoice.loadDetails();
        return invoice;
    }
}
