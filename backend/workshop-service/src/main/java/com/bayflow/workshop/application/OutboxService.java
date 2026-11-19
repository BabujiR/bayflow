package com.bayflow.workshop.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
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

    /** Event types for notification-service (names as it knows them). */
    public static final String ESTIMATE_READY = "ESTIMATE_READY";
    public static final String ESTIMATE_DECIDED = "ESTIMATE_DECIDED";
    public static final String WORK_COMPLETED = "WORK_COMPLETED";

    /** An event was queued; sent right after the transaction commits (see the dispatcher). */
    public record NotificationQueued(UUID tenantId, UUID jobCardId) {
    }

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

    @Transactional(readOnly = true)
    public List<OutboxMessage> pendingFor(UUID jobCardId) {
        return outbox.findByJobCardIdAndProcessedAtIsNull(jobCardId);
    }

    @Transactional(readOnly = true)
    public List<DueMessage> due() {
        return outbox.findDueAcrossTenants(clock.instant(), properties.getNotifications().getBatchSize());
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
        message.markFailed(error, clock.instant(), settings.getInitialBackoff(), settings.getMaxBackoff());
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
