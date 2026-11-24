package com.bayflow.workshop.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.LocationContext;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.cannedjobs.CannedJobService;
import com.bayflow.workshop.reminders.ServiceDueService;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;
import com.bayflow.workshop.domain.Severity;
import com.bayflow.workshop.infrastructure.JobCardRepository;
import com.bayflow.workshop.integration.WorkshopSettingsClient;
import com.bayflow.workshop.integration.WorkshopSettingsClient.WorkshopSettings;

/**
 * Job cards from creation through the customer's decision. Role checks happen on the API;
 * this class enforces who may see which card: staff see all, customers only their own.
 */
@Service
public class JobCardService {

    /**
     * What appointment-service (or an advisor) sends when a vehicle is checked in. locationId
     * null = the organization's first location, "Main" (id = tenant id).
     */
    public record OpenCommand(UUID appointmentId, UUID locationId, UUID vehicleId, String vin, String registration,
            UUID customerUserId, String customerName, int mileage) {
    }

    /** The job card and whether this call created it (false = it already existed). */
    public record OpenResult(JobCard jobCard, boolean created) {
    }

    private final JobCardRepository jobCards;
    private final ServiceHistoryService history;
    private static final Logger log = LoggerFactory.getLogger(JobCardService.class);

    private final WorkshopSettingsClient settings;
    private final OutboxService outbox;
    private final CannedJobService cannedJobs;
    private final ServiceDueService serviceDue;
    private final Clock clock;

    public JobCardService(JobCardRepository jobCards, ServiceHistoryService history,
            WorkshopSettingsClient settings, OutboxService outbox, CannedJobService cannedJobs,
            ServiceDueService serviceDue, Clock clock) {
        this.cannedJobs = cannedJobs;
        this.serviceDue = serviceDue;
        this.jobCards = jobCards;
        this.history = history;
        this.settings = settings;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Idempotent on appointmentId: a repeated call returns the existing card. A new card freezes
     * its location's current labour rate and the organization's currency; without them it is not
     * opened (503), so the caller's outbox retries later.
     */
    @Transactional
    public OpenResult open(OpenCommand command) {
        Optional<JobCard> existing = jobCards.findByAppointmentId(command.appointmentId());
        if (existing.isPresent()) {
            return new OpenResult(loaded(existing.get()), false);
        }
        UUID location = command.locationId() != null ? command.locationId() : TenantContext.require();
        if (!LocationContext.current().allows(location)) {
            throw ApiException.notFound("Location");
        }
        WorkshopSettings money = workshopSettings(location);
        JobCard card = JobCard.open(command.appointmentId(), location, command.vehicleId(), command.vin(),
                command.registration(), command.customerUserId(), command.customerName(), command.mileage(),
                money.labourRate(), money.currency(), clock.instant());
        return new OpenResult(jobCards.saveAndFlush(card), true);
    }

    private WorkshopSettings workshopSettings(UUID locationId) {
        UUID tenantId = TenantContext.require();
        try {
            return settings.get(tenantId, locationId);
        } catch (WorkshopSettingsClient.UnknownLocationException e) {
            throw ApiException.badRequest("invalid-location", "That location does not belong to this workshop.");
        } catch (RuntimeException e) {
            log.warn("Workshop settings for {} unavailable; not opening the job card: {}", tenantId, e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "settings-unavailable", "Service Unavailable",
                    "The workshop's labour rate could not be confirmed. Try again shortly.");
        }
    }

    /** Used after a concurrent create lost the race on the unique appointment id. */
    @Transactional(readOnly = true)
    public Optional<JobCard> findByAppointment(UUID appointmentId) {
        return jobCards.findByAppointmentId(appointmentId).map(JobCardService::loaded);
    }

    @Transactional(readOnly = true)
    public JobCard getByAppointment(CurrentUser user, UUID appointmentId) {
        JobCard card = jobCards.findByAppointmentId(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Job card"));
        return visible(user, card);
    }

    @Transactional(readOnly = true)
    public Page<JobCard> list(CurrentUser user, JobCardStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        if (seesAll(user)) {
            return status == null ? jobCards.findAll(pageable) : jobCards.findByStatus(status, pageable);
        }
        UUID userId = userId(user);
        return status == null
                ? jobCards.findByCustomerUserId(userId, pageable)
                : jobCards.findByCustomerUserIdAndStatus(userId, status, pageable);
    }

    @Transactional(readOnly = true)
    public JobCard get(CurrentUser user, UUID id) {
        return visible(user, find(id));
    }

    @Transactional
    public JobCard addFinding(CurrentUser user, UUID id, String description, Severity severity) {
        JobCard card = find(id);
        card.addFinding(description, severity, user.username(), clock.instant());
        return loaded(card);
    }

    @Transactional
    public JobCard addRecommendation(UUID id, String description, BigDecimal estimatedPartsCost,
            BigDecimal estimatedLabourHours) {
        return addRecommendation(id, description, estimatedPartsCost, estimatedLabourHours, null);
    }

    /** cannedJobId: must be an active canned job usable at the card's location (400 otherwise). */
    @Transactional
    public JobCard addRecommendation(UUID id, String description, BigDecimal estimatedPartsCost,
            BigDecimal estimatedLabourHours, UUID cannedJobId) {
        JobCard card = find(id);
        if (cannedJobId != null) {
            cannedJobs.forJobCard(cannedJobId, card.getLocationId());
        }
        card.addRecommendation(description, estimatedPartsCost, estimatedLabourHours, cannedJobId, clock.instant());
        return loaded(card);
    }

    /** One part to record in {@link #addWork}. */
    public record WorkPart(String partNumber, String name, int quantity, BigDecimal unitPrice) {
    }

    /**
     * Several parts and labour for one approved item at once (a canned job's usual parts and
     * hours, as the mechanic ticked them); all or nothing. hours null = no labour.
     */
    @Transactional
    public JobCard addWork(CurrentUser user, UUID id, UUID recommendationId, java.util.List<WorkPart> parts,
            BigDecimal hours) {
        if (parts.isEmpty() && hours == null) {
            throw ApiException.badRequest("nothing-to-add", "Choose at least one part or the labour.");
        }
        JobCard card = find(id);
        java.time.Instant now = clock.instant();
        for (WorkPart part : parts) {
            card.addPart(recommendationId, part.partNumber(), part.name(), part.quantity(), part.unitPrice(),
                    user.username(), now);
        }
        if (hours != null) {
            card.addLabour(recommendationId, user.username(), hours, card.getLabourRate(), now);
        }
        return loaded(card);
    }

    @Transactional
    public JobCard submitForApproval(CurrentUser user, UUID id) {
        JobCard card = find(id);
        card.submitForApproval(user.username(), clock.instant());
        if (card.getStatus() == JobCardStatus.AWAITING_APPROVAL) {
            outbox.enqueue(card, OutboxService.ESTIMATE_READY);
        }
        return loaded(card);
    }

    /**
     * The customer decides on their own card; an advisor can record a decision the customer
     * gave in person or by phone, which is noted in {@code decidedBy}.
     */
    @Transactional
    public JobCard decide(CurrentUser user, UUID id, UUID itemId, boolean approve) {
        JobCard card = get(user, id);
        String decidedBy = user.isCustomer()
                ? user.username()
                : user.username() + " (on behalf of the customer)";
        JobCardStatus before = card.getStatus();
        card.decide(itemId, approve, decidedBy, clock.instant());
        // Once, when the last item is decided.
        if (before == JobCardStatus.AWAITING_APPROVAL && card.getStatus() != JobCardStatus.AWAITING_APPROVAL) {
            outbox.enqueue(card, OutboxService.ESTIMATE_DECIDED);
        }
        return loaded(card);
    }

    // ---- work and completion ---------------------------------------------------------

    @Transactional
    public JobCard start(UUID id) {
        JobCard card = find(id);
        card.start(clock.instant());
        return loaded(card);
    }

    @Transactional
    public JobCard logWork(CurrentUser user, UUID id, UUID recommendationId, String description) {
        JobCard card = find(id);
        card.logWork(recommendationId, user.username(), description, clock.instant());
        return loaded(card);
    }

    @Transactional
    public JobCard addPart(CurrentUser user, UUID id, UUID recommendationId, String partNumber, String name,
            int quantity, BigDecimal unitPrice) {
        JobCard card = find(id);
        card.addPart(recommendationId, partNumber, name, quantity, unitPrice, user.username(), clock.instant());
        return loaded(card);
    }

    /** {@code hourlyRate} defaults to the rate frozen on the card when it opened. */
    @Transactional
    public JobCard addLabour(CurrentUser user, UUID id, UUID recommendationId, BigDecimal hours,
            BigDecimal hourlyRate) {
        JobCard card = find(id);
        BigDecimal rate = hourlyRate != null ? hourlyRate : card.getLabourRate();
        card.addLabour(recommendationId, user.username(), hours, rate, clock.instant());
        return loaded(card);
    }

    /** Completes the card and writes its service-history entry in the same transaction. */
    @Transactional
    public JobCard complete(UUID id) {
        JobCard card = find(id);
        card.complete(clock.instant());
        history.record(card);
        serviceDue.recordCompleted(card);
        outbox.enqueue(card, OutboxService.WORK_COMPLETED);
        return loaded(card);
    }

    /**
     * A job card of a location the caller works at. Row-level security already hides the others;
     * this check makes sure a misconfigured database can't change that (404).
     */
    private JobCard find(UUID id) {
        JobCard card = jobCards.findById(id).orElseThrow(() -> ApiException.notFound("Job card"));
        if (!LocationContext.current().allows(card.getLocationId())) {
            throw ApiException.notFound("Job card");
        }
        return card;
    }

    private static JobCard visible(CurrentUser user, JobCard card) {
        if (seesAll(user) || (user.isCustomer() && card.isOwnedBy(userId(user)))) {
            return loaded(card);
        }
        throw ApiException.notFound("Job card");
    }

    /** Advisors, mechanics and admins see every job card. */
    private static boolean seesAll(CurrentUser user) {
        return user.isServiceAdvisor() || user.isMechanic() || user.isAdmin() || user.isSystem();
    }

    private static UUID userId(CurrentUser user) {
        try {
            return UUID.fromString(user.subject());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Job card");
        }
    }

    /** Loads the child collections while the transaction is open, so the API layer can map them. */
    private static JobCard loaded(JobCard card) {
        card.loadDetails();
        return card;
    }
}
