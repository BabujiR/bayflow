package com.bayflow.workshop.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.OutboxMessage;
import com.bayflow.workshop.domain.RecommendedWorkItem;
import com.bayflow.workshop.infrastructure.OutboxRepository;
import com.bayflow.workshop.infrastructure.OutboxRepository.DueMessage;
import com.bayflow.workshop.integration.NotificationPayload;

import tools.jackson.databind.json.JsonMapper;

/** Database side of the outbox: queueing, finding and settling notification events. */
@Service
public class OutboxService {

    /** How long a claimed message is left to its sender (longer than any HTTP timeout here). */
    static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

    /** Event types for notification-service (names as it knows them). */
    public static final String ESTIMATE_READY = "ESTIMATE_READY";
    public static final String ESTIMATE_DECIDED = "ESTIMATE_DECIDED";
    public static final String WORK_COMPLETED = "WORK_COMPLETED";
    public static final String INVOICE_ISSUED = "INVOICE_ISSUED";
    public static final String SERVICE_DUE = "SERVICE_DUE";

    /** An event was queued; sent right after the transaction commits (see the dispatcher). */
    public record NotificationQueued(UUID tenantId, UUID jobCardId) {
    }

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outbox;
    private final WorkshopProperties properties;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OutboxService(OutboxRepository outbox, WorkshopProperties properties, JsonMapper json,
            ApplicationEventPublisher events, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.json = json;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Queues an event about the card in the caller's transaction: it is sent if (and only if) the
     * change commits. The actor is whoever is signed in.
     */
    @Transactional
    public void enqueue(JobCard card, String type) {
        if (!properties.getNotifications().isEnabled()) {
            return;
        }
        UUID tenant = TenantContext.require();
        CurrentUser actor = currentActor();
        int approved = (int) card.getRecommendations().stream().filter(RecommendedWorkItem::isApproved).count();
        int declined = (int) card.getRecommendations().stream()
                .filter(r -> !r.isApproved() && !r.isPending()).count();
        NotificationPayload payload = new NotificationPayload(null, tenant, card.getLocationId(), type,
                actor == null ? null : parseId(actor.subject()), actor != null && actor.isCustomer(),
                card.getCustomerUserId(), card.getId(), card.getRegistration(), approved, declined);
        outbox.save(OutboxMessage.create(card.getId(), OutboxMessage.NOTIFY, json.writeValueAsString(payload),
                clock.instant()));
        events.publishEvent(new NotificationQueued(tenant, card.getId()));
    }

    /**
     * A maintenance reminder for the customer: the vehicle's services due (names, joined), about
     * the job card where they were last done. Sent by the system (no actor).
     */
    @Transactional
    public void enqueueServiceDue(UUID locationId, UUID customerUserId, UUID jobCardId, String registration,
            String services) {
        if (!properties.getNotifications().isEnabled()) {
            return;
        }
        UUID tenant = TenantContext.require();
        String detail = services.length() > 300 ? services.substring(0, 297) + "..." : services;
        NotificationPayload payload = new NotificationPayload(null, tenant, locationId, SERVICE_DUE, null, false,
                customerUserId, jobCardId, registration, 0, 0, detail);
        outbox.save(OutboxMessage.create(jobCardId, OutboxMessage.NOTIFY, json.writeValueAsString(payload),
                clock.instant()));
        events.publishEvent(new NotificationQueued(tenant, jobCardId));
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> pendingFor(UUID jobCardId) {
        return outbox.findByJobCardIdAndProcessedAtIsNullAndFailedAtIsNull(jobCardId);
    }

    @Transactional(readOnly = true)
    public List<DueMessage> due() {
        return outbox.findDueAcrossTenants(clock.instant(), properties.getNotifications().getBatchSize());
    }

    /**
     * Claims the message before sending it, so it goes out once even when the immediate send and
     * the dispatcher (or two instances) reach it together. A claim lasts {@link #CLAIM_LEASE}: a
     * sender that dies mid-send leaves the message to be retried after that. True when the caller
     * may send it now.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID messageId) {
        Instant now = clock.instant();
        return outbox.claim(messageId, now, now.plus(CLAIM_LEASE)) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<OutboxMessage> find(UUID messageId) {
        return outbox.findById(messageId);
    }

    public NotificationPayload payload(OutboxMessage message) {
        return json.readValue(message.getPayload(), NotificationPayload.class).withEventId(message.getId());
    }

    @Transactional
    public void markSent(UUID messageId) {
        OutboxMessage message = outbox.findById(messageId).orElseThrow();
        if (!message.isProcessed()) {
            message.markDelivered(clock.instant());
        }
    }

    @Transactional
    public void markFailed(UUID messageId, String error) {
        OutboxMessage message = outbox.findById(messageId).orElseThrow();
        if (message.isProcessed()) {
            return;
        }
        WorkshopProperties.Notifications settings = properties.getNotifications();
        if (message.markFailed(error, clock.instant(), settings.getInitialBackoff(), settings.getMaxBackoff(),
                settings.getMaxAttempts())) {
            log.error("Gave up on outbox message {} ({}) after {} attempts: {}", message.getId(), message.getType(),
                    message.getAttempts(), message.getLastError());
        }
    }

    private static CurrentUser currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken ? CurrentUser.from(authentication) : null;
    }

    private static UUID parseId(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
