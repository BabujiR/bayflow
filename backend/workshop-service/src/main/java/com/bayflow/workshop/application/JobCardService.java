package com.bayflow.workshop.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;
import com.bayflow.workshop.domain.Severity;
import com.bayflow.workshop.infrastructure.JobCardRepository;

/**
 * Job cards from creation through the customer's decision. Role checks happen on the API;
 * this class enforces who may see which card: staff see all, customers only their own.
 */
@Service
public class JobCardService {

    /** What appointment-service (or an advisor) sends when a vehicle is checked in. */
    public record OpenCommand(UUID appointmentId, UUID vehicleId, String vin, String registration,
            UUID customerUserId, String customerName, int mileage) {
    }

    /** The job card and whether this call created it (false = it already existed). */
    public record OpenResult(JobCard jobCard, boolean created) {
    }

    private final JobCardRepository jobCards;
    private final ServiceHistoryService history;
    private final WorkshopProperties properties;
    private final Clock clock;

    public JobCardService(JobCardRepository jobCards, ServiceHistoryService history, WorkshopProperties properties,
            Clock clock) {
        this.jobCards = jobCards;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** Idempotent on appointmentId: a repeated call returns the existing card. */
    @Transactional
    public OpenResult open(OpenCommand command) {
        Optional<JobCard> existing = jobCards.findByAppointmentId(command.appointmentId());
        if (existing.isPresent()) {
            return new OpenResult(loaded(existing.get()), false);
        }
        JobCard card = JobCard.open(command.appointmentId(), command.vehicleId(), command.vin(),
                command.registration(), command.customerUserId(), command.customerName(), command.mileage(),
                clock.instant());
        return new OpenResult(jobCards.saveAndFlush(card), true);
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
        JobCard card = find(id);
        card.addRecommendation(description, estimatedPartsCost, estimatedLabourHours, clock.instant());
        return loaded(card);
    }

    @Transactional
    public JobCard submitForApproval(UUID id) {
        JobCard card = find(id);
        card.submitForApproval(clock.instant());
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
        card.decide(itemId, approve, decidedBy, clock.instant());
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

    /** {@code hourlyRate} defaults to the workshop rate when not given. */
    @Transactional
    public JobCard addLabour(CurrentUser user, UUID id, UUID recommendationId, BigDecimal hours,
            BigDecimal hourlyRate) {
        JobCard card = find(id);
        BigDecimal rate = hourlyRate != null ? hourlyRate : properties.getLabourRate();
        card.addLabour(recommendationId, user.username(), hours, rate, clock.instant());
        return loaded(card);
    }

    /** Completes the card and writes its service-history entry in the same transaction. */
    @Transactional
    public JobCard complete(UUID id) {
        JobCard card = find(id);
        card.complete(clock.instant());
        history.record(card);
        return loaded(card);
    }

    private JobCard find(UUID id) {
        return jobCards.findById(id).orElseThrow(() -> ApiException.notFound("Job card"));
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
        card.getFindings().size();
        card.getRecommendations().size();
        card.getWorkLogs().size();
        card.getParts().size();
        card.getLabour().size();
        return card;
    }
}
