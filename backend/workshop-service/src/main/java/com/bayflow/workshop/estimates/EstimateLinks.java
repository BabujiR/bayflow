package com.bayflow.workshop.estimates;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.application.JobCardService;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.domain.ApprovalStatus;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;
import com.bayflow.workshop.domain.RecommendedWorkItem;
import com.bayflow.workshop.domain.Severity;
import com.bayflow.workshop.infrastructure.JobCardRepository;
import com.bayflow.workshop.integration.AuthServiceClient;
import com.bayflow.workshop.integration.AuthServiceClient.LocationProfile;
import com.bayflow.workshop.integration.AuthServiceClient.Mail;
import com.bayflow.workshop.integration.AuthServiceClient.MailUnavailableException;
import com.bayflow.workshop.integration.AuthServiceClient.WorkshopProfile;
import com.bayflow.workshop.integration.BillingContactClient;
import com.bayflow.workshop.integration.BillingContactClient.BillingContact;

/**
 * Estimate approval links: an estimate submitted for approval is e-mailed to the customer as a
 * private link. They see each recommended item with its price, approve or decline each one and
 * sign, without an account; the job card moves on as if they had answered in the app. Up to
 * {@code maxReminders} reminders go out while nothing is decided.
 */
@Service
public class EstimateLinks {

    private static final Logger log = LoggerFactory.getLogger(EstimateLinks.class);
    private static final int REMINDER_BATCH = 50;

    /** What staff see on the job card. {@code open}: the customer can still answer from the link. */
    public record EstimateLinkStatus(String sentTo, String sentBy, Instant sentAt, Instant lastSentAt,
            Instant expiresAt, Instant firstViewedAt, int remindersSent, Instant nextReminderAt, Instant decidedAt,
            String signerName, boolean signed, boolean open) {
    }

    public record CustomerEstimateFinding(String description, Severity severity) {
    }

    public record CustomerEstimateItem(UUID id, String description, BigDecimal price, ApprovalStatus status) {
    }

    /** What the customer sees. {@code canDecide}: they can still approve or decline here. */
    public record CustomerEstimate(String shopName, String locationName, String shopPhone, String firstName,
            String vehicle, JobCardStatus status, boolean canDecide, List<CustomerEstimateFinding> findings,
            List<CustomerEstimateItem> items, BigDecimal total, String currency, Instant expiresAt,
            Instant decidedAt, String signerName) {
    }

    private final EstimateLinkRepository links;
    private final JobCardRepository jobCards;
    private final JobCardService jobCardService;
    private final AuthServiceClient auth;
    private final BillingContactClient contacts;
    private final WorkshopProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EstimateLinks(EstimateLinkRepository links, JobCardRepository jobCards, JobCardService jobCardService,
            AuthServiceClient auth, BillingContactClient contacts, WorkshopProperties properties,
            PlatformTransactionManager transactions, Clock clock) {
        this.links = links;
        this.jobCards = jobCards;
        this.jobCardService = jobCardService;
        this.auth = auth;
        this.contacts = contacts;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    // ---- staff ----------------------------------------------------------------------------

    /**
     * E-mails the link (to {@code to}, or the customer's address), replacing any earlier one.
     * Only for an estimate awaiting the customer's answer.
     */
    public EstimateLinkStatus send(CurrentUser user, UUID jobCardId, String to) {
        UUID tenant = TenantContext.require();
        JobCard card = jobCardService.get(user, jobCardId);
        if (card.getStatus() != JobCardStatus.AWAITING_APPROVAL) {
            throw ApiException.conflict("not-awaiting-approval",
                    "Submit the estimate for approval first. A link can only be sent while it waits for the customer.");
        }
        BillingContact contact = contact(tenant, card);
        String address = to != null && !to.isBlank() ? to.strip()
                : contact == null ? null : contact.email();
        if (address == null || address.isBlank()) {
            throw ApiException.badRequest("no-email-address",
                    "This customer has no e-mail address. Enter one to send the link to.");
        }
        Shop shop = shop(tenant, card.getLocationId());
        String token = EstimateTokens.newToken();
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            links.findByJobCardIdAndReplacedAtIsNullOrderBySentAtAsc(jobCardId).forEach(old -> old.replace(now));
            EstimateLink link = links.saveAndFlush(EstimateLink.send(jobCardId, EstimateTokens.hash(token), address,
                    user.username(), now, lifetime(), remindEvery()));
            // Inside the transaction: if the mail can't be queued, no link is left behind.
            mail(tenant, shop, card, contact, link, token, false);
        });
        log.info("Estimate link for job card {} e-mailed by {}", jobCardId, user.username());
        return status(user, jobCardId).orElseThrow();
    }

    /** Right after an estimate is submitted: e-mails the link if the customer has an address. Never throws. */
    public void sendAfterSubmit(CurrentUser user, UUID jobCardId) {
        if (!properties.getEstimates().isSendOnSubmit()) {
            return;
        }
        try {
            send(user, jobCardId, null);
        } catch (ApiException e) {
            log.info("Estimate link for job card {} not sent: {}", jobCardId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Estimate link for job card {} not sent: {}", jobCardId, e.toString());
        }
    }

    /** The last link sent for this job card, with its reminders. */
    public Optional<EstimateLinkStatus> status(CurrentUser user, UUID jobCardId) {
        jobCardService.get(user, jobCardId);
        return tx.execute(s -> {
            List<EstimateLink> sent = links.findByJobCardIdAndReplacedAtIsNullOrderBySentAtAsc(jobCardId);
            if (sent.isEmpty()) {
                return Optional.empty();
            }
            Instant now = clock.instant();
            EstimateLink first = sent.getFirst();
            EstimateLink last = sent.getLast();
            Optional<EstimateLink> signed = sent.stream().filter(EstimateLink::isSigned).findFirst();
            Instant viewed = sent.stream().map(EstimateLink::getFirstViewedAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            boolean open = signed.isEmpty() && now.isBefore(last.getExpiresAt());
            return Optional.of(new EstimateLinkStatus(first.getSentTo(), first.getSentBy(), first.getSentAt(),
                    last.getSentAt(), last.getExpiresAt(), viewed, last.getRemindersSent(),
                    last.getNextReminderAt(), signed.map(EstimateLink::getDecidedAt).orElse(null),
                    signed.map(EstimateLink::getSignerName).orElse(null), signed.isPresent(), open));
        });
    }

    /** The customer's signature (PNG), once they signed. */
    public Optional<byte[]> signature(CurrentUser user, UUID jobCardId) {
        jobCardService.get(user, jobCardId);
        return tx.execute(s -> links.findFirstByJobCardIdAndDecidedAtIsNotNull(jobCardId)
                .map(EstimateLink::getSignature));
    }

    // ---- the customer, from the link -------------------------------------------------------

    public CustomerEstimate view(String token) {
        Opened opened = open(token, true);
        return TenantContext.call(opened.tenant(), () -> view(opened.tenant(), opened.linkId()));
    }

    /**
     * The customer's answer: every pending item approved or declined, their name and signature.
     * The job card moves on exactly as when they answer in the app.
     */
    public CustomerEstimate decide(String token, Map<UUID, Boolean> decisions, String signerName, String signature,
            String ip, String agent) {
        String name = signerName == null ? "" : signerName.strip();
        if (name.length() < 2 || name.length() > 80) {
            throw ApiException.badRequest("name-required", "Type your full name to sign.");
        }
        byte[] png = Signatures.png(signature);
        Opened opened = open(token, false);
        return TenantContext.call(opened.tenant(), () -> {
            tx.executeWithoutResult(status -> {
                EstimateLink link = links.findById(opened.linkId()).orElseThrow();
                requireOpen(link);
                jobCardService.decideAll(link.getJobCardId(), decisions, name + " (signed online)");
                link.signed(name, png, ip, agent, clock.instant());
            });
            log.info("Estimate for job card link {} answered online", opened.linkId());
            return view(opened.tenant(), opened.linkId());
        });
    }

    // ---- reminders ---------------------------------------------------------------------------

    /** One pass: a reminder for each link due one, in every workshop. Returns how many went out. */
    public int sendDueReminders() {
        List<EstimateLinkRepository.Due> due = TenantContext.callAcrossTenants(
                () -> links.dueReminders(clock.instant(), REMINDER_BATCH));
        int sent = 0;
        for (EstimateLinkRepository.Due next : due) {
            try {
                if (Boolean.TRUE.equals(TenantContext.call(next.getTenantId(), () -> remind(next.getTenantId(),
                        next.getId())))) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Estimate reminder for link {} failed: {}", next.getId(), e.toString());
            }
        }
        return sent;
    }

    private boolean remind(UUID tenant, UUID linkId) {
        EstimateLink link = tx.execute(s -> links.findById(linkId).orElseThrow());
        JobCard card = tx.execute(s -> jobCards.findById(link.getJobCardId()).map(c -> {
            c.loadDetails();
            return c;
        }).orElseThrow());
        if (card.getStatus() != JobCardStatus.AWAITING_APPROVAL) {
            // Answered another way (in the app, at the desk): nothing to remind about.
            tx.executeWithoutResult(s -> links.findById(linkId).ifPresent(EstimateLink::stopReminders));
            return false;
        }
        BillingContact contact = contact(tenant, card);
        Shop shop = shop(tenant, card.getLocationId());
        String token = EstimateTokens.newToken();
        tx.executeWithoutResult(s -> {
            EstimateLink current = links.findById(linkId).orElseThrow();
            EstimateLink next = links.saveAndFlush(current.reminder(EstimateTokens.hash(token), clock.instant(),
                    remindEvery(), properties.getEstimates().getMaxReminders()));
            mail(tenant, shop, card, contact, next, token, true);
        });
        log.info("Estimate reminder for job card {} e-mailed", card.getId());
        return true;
    }

    // ---- helpers ---------------------------------------------------------------------------

    private record Opened(UUID tenant, UUID linkId) {
    }

    private record Shop(String name, String slug, String locationName, String phone, ZoneId zone) {
    }

    /** The link of a token, in its workshop; 404 for an unknown one, 410 for one that no longer works. */
    private Opened open(String token, boolean markViewed) {
        if (!EstimateTokens.looksValid(token)) {
            throw notFound();
        }
        String hash = EstimateTokens.hash(token);
        // Nobody is signed in: the token itself says which workshop's estimate this is.
        UUID tenant = TenantContext.callAcrossTenants(() -> links.tenantOf(hash)).orElseThrow(EstimateLinks::notFound);
        UUID linkId = TenantContext.call(tenant, () -> tx.execute(s -> {
            EstimateLink link = links.findByTokenHash(hash).orElseThrow(EstimateLinks::notFound);
            if (link.isSigned() || link.closed(clock.instant()) == EstimateLink.Closed.NONE) {
                if (markViewed) {
                    link.viewed(clock.instant());
                }
                return link.getId();
            }
            requireOpen(link);
            return link.getId();
        }));
        return new Opened(tenant, linkId);
    }

    private void requireOpen(EstimateLink link) {
        switch (link.closed(clock.instant())) {
            case REPLACED -> throw new ApiException(HttpStatus.GONE, "link-replaced", "Gone",
                    "A newer link was sent for this estimate. Open the latest e-mail from the shop.");
            case EXPIRED -> throw new ApiException(HttpStatus.GONE, "link-expired", "Gone",
                    "This link has expired. Call the shop and they'll send you a new one.");
            case NONE -> {
                // still works
            }
        }
    }

    private CustomerEstimate view(UUID tenant, UUID linkId) {
        EstimateLink link = tx.execute(s -> links.findById(linkId).orElseThrow());
        JobCard card = tx.execute(s -> jobCards.findById(link.getJobCardId()).map(c -> {
            c.loadDetails();
            return c;
        }).orElseThrow(EstimateLinks::notFound));
        Shop shop = shopIfAvailable(tenant, card.getLocationId());
        BillingContact contact = contact(tenant, card);
        BigDecimal rate = card.getLabourRate();
        List<CustomerEstimateItem> items = card.getRecommendations().stream()
                .map(i -> new CustomerEstimateItem(i.getId(), i.getDescription(), i.estimatedTotal(rate),
                        i.getApprovalStatus()))
                .toList();
        BigDecimal total = card.getRecommendations().stream().map(i -> i.estimatedTotal(rate))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CustomerEstimateFinding> findings = card.getFindings().stream()
                .map(f -> new CustomerEstimateFinding(f.getDescription(), f.getSeverity())).toList();
        boolean canDecide = card.getStatus() == JobCardStatus.AWAITING_APPROVAL && !link.isSigned()
                && link.closed(clock.instant()) == EstimateLink.Closed.NONE
                && card.getRecommendations().stream().anyMatch(RecommendedWorkItem::isPending);
        // Who signed this estimate, from whichever of its links they used.
        Optional<EstimateLink> signed = link.isSigned() ? Optional.of(link)
                : tx.execute(s -> links.findFirstByJobCardIdAndDecidedAtIsNotNull(card.getId()));
        return new CustomerEstimate(shop == null ? null : shop.name(), shop == null ? null : shop.locationName(),
                shop == null ? null : shop.phone(), firstName(card.getCustomerName()), vehicle(card, contact),
                card.getStatus(), canDecide, findings, items, total, card.getCurrency(), link.getExpiresAt(),
                signed.map(EstimateLink::getDecidedAt).orElse(null),
                signed.map(EstimateLink::getSignerName).orElse(null));
    }

    private void mail(UUID tenant, Shop shop, JobCard card, BillingContact contact, EstimateLink link, String token,
            boolean reminder) {
        BigDecimal total = card.getRecommendations().stream().map(i -> i.estimatedTotal(card.getLabourRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        EstimateEmail.Rendered email = EstimateEmail.render(new EstimateEmail.Content(shop.name(), shop.phone(),
                firstName(card.getCustomerName()), vehicle(card, contact), card.getRecommendations().size(), total,
                card.getCurrency(), site(shop.slug()) + "/estimate?token=" + token, link.getExpiresAt(),
                shop.zone()), reminder);
        try {
            auth.send(tenant, new Mail(link.getSentTo(), shop.name(), null, email.subject(), email.text(),
                    email.html()));
        } catch (MailUnavailableException e) {
            throw unavailable("mail-unavailable",
                    "E-mail is not set up on this server. Ask the customer to approve at the desk instead.");
        } catch (RuntimeException e) {
            log.warn("Could not queue the estimate e-mail for job card {}: {}", card.getId(), e.toString());
            throw unavailable("mail-failed", "The e-mail could not be sent. Try again in a moment.");
        }
    }

    /** The shop's name and contact (for an e-mail: 503 if auth-service can't be reached). */
    private Shop shop(UUID tenant, UUID locationId) {
        try {
            return readShop(tenant, locationId);
        } catch (RuntimeException e) {
            log.warn("Could not read the workshop details: {}", e.toString());
            throw unavailable("settings-unavailable",
                    "The workshop's details could not be read. Try again in a moment.");
        }
    }

    /** The shop's name and contact for the customer's page; null if auth-service can't be reached. */
    private Shop shopIfAvailable(UUID tenant, UUID locationId) {
        try {
            return readShop(tenant, locationId);
        } catch (RuntimeException e) {
            log.warn("Could not read the workshop details: {}", e.toString());
            return null;
        }
    }

    private Shop readShop(UUID tenant, UUID locationId) {
        WorkshopProfile workshop = auth.workshop(tenant);
        if (workshop == null || workshop.slug() == null) {
            throw new IllegalStateException("auth-service returned no workshop");
        }
        LocationProfile location = locationId == null ? null : auth.location(tenant, locationId);
        String phone = location != null && location.phone() != null && !location.phone().isBlank()
                ? location.phone() : workshop.phone();
        return new Shop(workshop.shownName(), workshop.slug(), location == null ? null : location.name(), phone,
                zone(location == null ? null : location.timeZone()));
    }

    private BillingContact contact(UUID tenant, JobCard card) {
        try {
            return contacts.get(tenant, card.getAppointmentId());
        } catch (RuntimeException e) {
            log.warn("Could not read the customer of job card {}: {}", card.getId(), e.toString());
            return null;
        }
    }

    /** "2019 Honda Civic (ABC 123)", or what is known of it. */
    private static String vehicle(JobCard card, BillingContact contact) {
        StringBuilder text = new StringBuilder();
        if (contact != null && contact.vehicleMake() != null) {
            if (contact.vehicleYear() != null && contact.vehicleYear() > 0) {
                text.append(contact.vehicleYear()).append(' ');
            }
            text.append(contact.vehicleMake());
            if (contact.vehicleModel() != null) {
                text.append(' ').append(contact.vehicleModel());
            }
        }
        String plate = card.getRegistration() != null && !card.getRegistration().isBlank() ? card.getRegistration()
                : card.getVin();
        if (text.isEmpty()) {
            return plate == null ? "car" : plate;
        }
        return plate == null ? text.toString() : text + " (" + plate + ")";
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? null : name.strip().split("\\s+")[0];
    }

    private String site(String slug) {
        URI site = URI.create(properties.getSiteUrl());
        String port = site.getPort() == -1 ? "" : ":" + site.getPort();
        return site.getScheme() + "://" + slug + "." + site.getHost() + port;
    }

    private java.time.Duration lifetime() {
        return properties.getEstimates().getLinkLifetime();
    }

    private java.time.Duration remindEvery() {
        return properties.getEstimates().getMaxReminders() > 0 ? properties.getEstimates().getRemindEvery() : null;
    }

    private static ZoneId zone(String id) {
        try {
            return id == null || id.isBlank() ? ZoneOffset.UTC : ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "link-not-found", "Not Found",
                "This link doesn't work. Check you copied all of it, or call the shop.");
    }

    private static ApiException unavailable(String type, String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, type, "Service Unavailable", detail);
    }
}
