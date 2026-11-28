package com.bayflow.workshop.reviews;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.integration.AuthServiceClient;
import com.bayflow.workshop.integration.AuthServiceClient.LocationProfile;
import com.bayflow.workshop.integration.AuthServiceClient.Mail;
import com.bayflow.workshop.integration.AuthServiceClient.MailUnavailableException;
import com.bayflow.workshop.integration.AuthServiceClient.ReviewSettings;
import com.bayflow.workshop.integration.AuthServiceClient.WorkshopProfile;
import com.bayflow.workshop.invoicing.Invoice;
import com.bayflow.workshop.invoicing.InvoiceRepository;
import com.bayflow.workshop.invoicing.InvoiceStatus;

/**
 * Google review requests. When an invoice is fully paid at a location that asks for reviews, a
 * request is scheduled (2 hours, the next morning or 2 days later); the sender e-mails the
 * location's review link then, unless the customer was asked in the last {@code askEvery}, asked
 * not to be, or the invoice was voided meanwhile. Everyone is asked, happy or not (Google forbids
 * asking only satisfied customers). Staff can send one at once or stop it.
 */
@Service
public class ReviewRequests {

    private static final Logger log = LoggerFactory.getLogger(ReviewRequests.class);
    private static final int BATCH = 50;
    private static final Duration RETRY_AFTER = Duration.ofHours(1);
    /** Who sends the scheduled ones. */
    static final String AUTOMATIC = "automatic";

    /** What staff see on a paid invoice. {@code status} NONE: nothing scheduled (requests off). */
    public record ReviewRequestStatus(String status, Instant dueAt, Instant sentAt, String sentTo, String sentBy,
            String reason) {

        static final ReviewRequestStatus NONE = new ReviewRequestStatus("NONE", null, null, null, null, null);

        static ReviewRequestStatus of(ReviewRequest r) {
            return new ReviewRequestStatus(r.getStatus().name(), r.getDueAt(), r.getSentAt(),
                    r.getSentAt() == null ? null : r.getCustomerEmail(), r.getSentBy(), r.getReason());
        }
    }

    /** What the stop page shows. */
    public record Stopped(String shopName) {
    }

    private final ReviewRequestRepository requests;
    private final InvoiceRepository invoices;
    private final AuthServiceClient auth;
    private final WorkshopProperties properties;
    private final TransactionTemplate tx;
    /** A transaction of its own: after the payment's has committed, a plain one would join it and not commit. */
    private final TransactionTemplate newTx;
    private final Clock clock;

    public ReviewRequests(ReviewRequestRepository requests, InvoiceRepository invoices, AuthServiceClient auth,
            WorkshopProperties properties, PlatformTransactionManager transactions, Clock clock) {
        this.requests = requests;
        this.invoices = invoices;
        this.auth = auth;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactions);
        this.newTx = new TransactionTemplate(transactions);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ---- when an invoice is paid --------------------------------------------------------------

    /**
     * Once the payment that settled {@code invoiceId} has committed, schedule its review request (in
     * its own transaction, so nothing here can undo the payment).
     */
    public void afterPaid(UUID invoiceId) {
        UUID tenant = TenantContext.require();
        Runnable schedule = () -> {
            try {
                TenantContext.run(tenant, () -> newTx.executeWithoutResult(s -> invoices.findById(invoiceId)
                        .ifPresent(this::paid)));
            } catch (RuntimeException e) {
                log.warn("Could not schedule the review request for invoice {}: {}", invoiceId, e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule.run();
                }
            });
        } else {
            schedule.run();
        }
    }

    /**
     * The invoice is fully paid: schedule its request if the location asks for reviews (once per
     * invoice). If the settings can't be read, it is scheduled for the next morning and the sender
     * checks them again.
     */
    void paid(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.PAID || requests.findByInvoiceId(invoice.getId()).isPresent()) {
            return;
        }
        LocationProfile location = locationOrNull(TenantContext.require(), invoice.getLocationId());
        ReviewSettings settings = location == null ? null : location.reviewsOrOff();
        if (settings != null && settings.notAsking()) {
            return;
        }
        Instant now = clock.instant();
        String delay = settings == null ? "NEXT_MORNING" : settings.delay();
        ReviewRequest request = ReviewRequest.scheduled(invoice.getId(), invoice.getLocationId(),
                email(invoice.getCustomerEmail()), invoice.getCustomerName(), invoice.getVehicleDescription(),
                dueAt(delay, zone(location), now), now);
        if (request.getCustomerEmail() == null) {
            request.skip("No e-mail address on the invoice", now);
        }
        requests.save(request);
    }

    /** When "after {@code delay}" is, from {@code now}, in the location's time zone. */
    Instant dueAt(String delay, ZoneId zone, Instant now) {
        return switch (delay == null ? "NEXT_MORNING" : delay) {
            case "TWO_HOURS" -> now.plus(Duration.ofHours(2));
            case "TWO_DAYS" -> now.plus(Duration.ofDays(2));
            default -> LocalDate.ofInstant(now, zone).plusDays(1).atTime(properties.getReviews().getMorning())
                    .atZone(zone).toInstant();
        };
    }

    // ---- staff, on an invoice -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReviewRequestStatus status(UUID invoiceId) {
        staffInvoice(invoiceId);
        return requests.findByInvoiceId(invoiceId).map(ReviewRequestStatus::of).orElse(ReviewRequestStatus.NONE);
    }

    /**
     * Sends it now (also when it was skipped, cancelled, or requests are off, as long as the
     * location has a link). The 90-day rule doesn't apply: staff chose to ask; a customer who
     * asked to stop is still not asked.
     */
    @Transactional
    public ReviewRequestStatus sendNow(CurrentUser user, UUID invoiceId) {
        Invoice invoice = staffInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw ApiException.conflict("invoice-not-paid", "Ask for a review once the invoice is fully paid.");
        }
        Optional<ReviewRequest> existing = requests.findByInvoiceId(invoiceId);
        if (existing.isPresent() && existing.get().getStatus() == ReviewRequest.Status.SENT) {
            throw ApiException.conflict("review-already-requested", "This customer was already asked for a review.");
        }
        String email = email(invoice.getCustomerEmail());
        if (email == null) {
            throw ApiException.badRequest("no-email-address", "This customer has no e-mail address on the invoice.");
        }
        if (requests.optedOut(email)) {
            throw ApiException.conflict("review-opted-out", "This customer asked not to get review requests.");
        }
        UUID tenant = TenantContext.require();
        LocationProfile location = location(tenant, invoice.getLocationId());
        ReviewSettings settings = location.reviewsOrOff();
        if (settings.url() == null || settings.url().isBlank()) {
            throw ApiException.conflict("no-review-link",
                    "Add this location's Google review link in Settings → Locations first.");
        }
        Instant now = clock.instant();
        ReviewRequest request = existing.orElseGet(() -> ReviewRequest.scheduled(invoice.getId(),
                invoice.getLocationId(), email, invoice.getCustomerName(), invoice.getVehicleDescription(), now, now));
        send(tenant, request, email, location, settings.url(), user.username());
        return ReviewRequestStatus.of(requests.saveAndFlush(request));
    }

    /** Don't send the scheduled request. */
    @Transactional
    public ReviewRequestStatus cancel(CurrentUser user, UUID invoiceId) {
        staffInvoice(invoiceId);
        ReviewRequest request = requests.findByInvoiceId(invoiceId)
                .filter(ReviewRequest::isScheduled)
                .orElseThrow(() -> ApiException.conflict("review-not-scheduled",
                        "No review request is waiting to go out."));
        request.cancel(user.username(), clock.instant());
        return ReviewRequestStatus.of(requests.saveAndFlush(request));
    }

    // ---- the scheduled sender -----------------------------------------------------------------

    /** Sends (or skips) the requests that are due, in every workshop. Returns how many went out. */
    public int sendDue() {
        List<ReviewRequestRepository.Due> due = TenantContext.callAcrossTenants(
                () -> requests.due(clock.instant(), BATCH));
        int sent = 0;
        for (ReviewRequestRepository.Due next : due) {
            UUID tenant = next.getTenantId();
            try {
                if (Boolean.TRUE.equals(TenantContext.call(tenant, () -> tx.execute(s -> process(tenant,
                        next.getId()))))) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Review request {} could not be sent: {}", next.getId(), e.toString());
                TenantContext.run(tenant, () -> tx.executeWithoutResult(s -> requests.findById(next.getId())
                        .filter(ReviewRequest::isScheduled)
                        .ifPresent(r -> r.retryLater(clock.instant().plus(RETRY_AFTER),
                                properties.getReviews().getMaxAttempts(), clock.instant()))));
            }
        }
        return sent;
    }

    private boolean process(UUID tenant, UUID id) {
        ReviewRequest request = requests.findById(id).filter(ReviewRequest::isScheduled).orElse(null);
        if (request == null) {
            return false;
        }
        Instant now = clock.instant();
        Invoice invoice = invoices.findById(request.getInvoiceId()).orElse(null);
        if (invoice == null || invoice.getStatus() == InvoiceStatus.VOID) {
            request.skip("The invoice was voided", now);
            return false;
        }
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            request.skip("The invoice is no longer fully paid", now);
            return false;
        }
        String email = email(invoice.getCustomerEmail());
        if (email == null) {
            request.skip("No e-mail address on the invoice", now);
            return false;
        }
        LocationProfile location = location(tenant, request.getLocationId());
        ReviewSettings settings = location.reviewsOrOff();
        if (settings.notAsking()) {
            request.skip("Review requests are off at this location", now);
            return false;
        }
        if (requests.optedOut(email)) {
            request.skip("The customer asked not to get review requests", now);
            return false;
        }
        Optional<ReviewRequest> last = requests.findFirstByCustomerEmailAndStatusOrderBySentAtDesc(email,
                ReviewRequest.Status.SENT);
        if (last.isPresent() && last.get().getSentAt().isAfter(now.minus(properties.getReviews().getAskEvery()))) {
            request.skip("Asked on " + DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(zone(location))
                    .format(last.get().getSentAt()), now);
            return false;
        }
        send(tenant, request, email, location, settings.url(), AUTOMATIC);
        return true;
    }

    // ---- the customer's stop link -------------------------------------------------------------

    /** The customer clicked "stop": no more review requests from this workshop to that address. */
    public Stopped stop(String token) {
        if (!ReviewTokens.looksValid(token)) {
            throw notFound();
        }
        String hash = ReviewTokens.hash(token);
        UUID tenant = TenantContext.callAcrossTenants(() -> requests.tenantOf(hash))
                .orElseThrow(ReviewRequests::notFound);
        return TenantContext.call(tenant, () -> {
            String email = tx.execute(s -> requests.findByTokenHash(hash).map(ReviewRequest::getCustomerEmail)
                    .orElseThrow(ReviewRequests::notFound));
            requests.optOut(tenant, email, clock.instant());
            log.info("A customer stopped review requests (workshop {})", tenant);
            String shop = null;
            try {
                WorkshopProfile workshop = auth.workshop(tenant);
                shop = workshop == null ? null : workshop.shownName();
            } catch (RuntimeException e) {
                log.warn("Could not read the workshop's name: {}", e.toString());
            }
            return new Stopped(shop);
        });
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void send(UUID tenant, ReviewRequest request, String email, LocationProfile location, String reviewUrl,
            String by) {
        WorkshopProfile workshop;
        try {
            workshop = auth.workshop(tenant);
        } catch (RuntimeException e) {
            log.warn("Could not read the workshop details: {}", e.toString());
            throw unavailable("settings-unavailable", "The workshop's details could not be read. Try again shortly.");
        }
        if (workshop == null || workshop.slug() == null) {
            throw unavailable("settings-unavailable", "The workshop's details could not be read. Try again shortly.");
        }
        String token = ReviewTokens.newToken();
        ReviewEmail.Rendered mail = ReviewEmail.render(new ReviewEmail.Content(workshop.shownName(),
                firstName(request.getCustomerName()), request.getVehicle(), reviewUrl,
                site(workshop.slug()) + "/reviews/stop?token=" + token));
        String replyTo = location.email() != null && !location.email().isBlank() ? location.email() : workshop.email();
        try {
            auth.send(tenant, new Mail(email, workshop.shownName(), replyTo, mail.subject(), mail.text(), mail.html()));
        } catch (MailUnavailableException e) {
            throw unavailable("mail-unavailable", "E-mail is not set up on this server.");
        } catch (RuntimeException e) {
            log.warn("Could not queue the review request for invoice {}: {}", request.getInvoiceId(), e.toString());
            throw unavailable("mail-failed", "The e-mail could not be sent. Try again in a moment.");
        }
        request.sent(email, ReviewTokens.hash(token), by, clock.instant());
        log.info("Review request for invoice {} e-mailed ({})", request.getInvoiceId(), by);
    }

    private Invoice staffInvoice(UUID id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    private LocationProfile location(UUID tenant, UUID locationId) {
        LocationProfile location = locationOrNull(tenant, locationId);
        if (location == null) {
            throw unavailable("settings-unavailable", "The location's settings could not be read. Try again shortly.");
        }
        return location;
    }

    private LocationProfile locationOrNull(UUID tenant, UUID locationId) {
        try {
            return auth.location(tenant, locationId);
        } catch (RuntimeException e) {
            log.warn("Could not read the settings of location {}: {}", locationId, e.toString());
            return null;
        }
    }

    /** https://<slug>.<site host>[:port], like the other links in e-mails. */
    private String site(String slug) {
        URI site = URI.create(properties.getSiteUrl());
        String port = site.getPort() == -1 ? "" : ":" + site.getPort();
        return site.getScheme() + "://" + slug + "." + site.getHost() + port;
    }

    private static ZoneId zone(LocationProfile location) {
        try {
            return location == null || location.timeZone() == null || location.timeZone().isBlank() ? ZoneOffset.UTC
                    : ZoneId.of(location.timeZone());
        } catch (RuntimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static String email(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.strip().split("\\s+")[0];
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "review-link-invalid", "Not Found",
                "This link isn't valid. Use the link in your latest e-mail from the shop.");
    }

    private static ApiException unavailable(String type, String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, type, "Service Unavailable", detail);
    }
}
