package com.bayflow.appointment.application;

import java.time.Clock;
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
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.config.IntegrationProperties;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.OutboxMessage;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.infrastructure.OutboxRepository;
import com.bayflow.appointment.infrastructure.OutboxRepository.DueMessage;
import com.bayflow.appointment.integration.NotificationPayload;
import com.bayflow.appointment.integration.OpenJobCardPayload;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;

import tools.jackson.databind.json.JsonMapper;

/** Database side of the outbox: storing, finding and settling messages. */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outbox;
    private final AppointmentRepository appointments;
    private final IntegrationProperties properties;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OutboxService(OutboxRepository outbox, AppointmentRepository appointments,
            IntegrationProperties properties, JsonMapper json, ApplicationEventPublisher events, Clock clock) {
        this.outbox = outbox;
        this.appointments = appointments;
        this.properties = properties;
        this.json = json;
        this.events = events;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so the message commits together with the check-in. */
    @Transactional
    public void enqueueJobCard(Appointment appointment) {
        OpenJobCardPayload payload = new OpenJobCardPayload(appointment.getId(), appointment.getVehicle().getId(),
                appointment.getVehicle().getVin(), appointment.getVehicle().getRegistration(),
                appointment.getCustomer().getUserId(), appointment.getCustomer().getName(),
                appointment.getArrivalMileage(), appointment.getTenantId(), appointment.getLocationId());
        outbox.save(OutboxMessage.create(appointment.getId(), OutboxMessage.CREATE_JOB_CARD,
                json.writeValueAsString(payload), clock.instant()));
    }

    /** A notification event was queued; sent right after the transaction commits (see the dispatcher). */
    public record NotificationQueued(UUID tenantId, UUID appointmentId) {
    }

    /**
     * Queues an event for notification-service in the caller's transaction, so it is sent if
     * (and only if) the change commits. The actor is whoever is signed in.
     */
    @Transactional
    public void enqueueNotification(Appointment appointment, String type, Instant previousScheduledAt) {
        enqueueNotification(appointment, type, previousScheduledAt, false);
    }

    /**
     * {@code byCustomer}: the customer did it without signing in (the public booking page or
     * their booking's private link), so the shop's desk is told.
     */
    @Transactional
    public void enqueueNotification(Appointment appointment, String type, Instant previousScheduledAt,
            boolean byCustomer) {
        if (!properties.getNotifications().isEnabled()) {
            return;
        }
        CurrentUser actor = currentActor();
        UUID tenant = TenantContext.require();
        NotificationPayload payload = new NotificationPayload(null, tenant,
                appointment.getLocationId(), type, actor == null ? null : parseId(actor.subject()),
                byCustomer || actor != null && actor.isCustomer(), appointment.getCustomer().getUserId(),
                appointment.getId(),
                appointment.getVehicle().getRegistration(), appointment.getScheduledAt(), previousScheduledAt);
        outbox.save(OutboxMessage.create(appointment.getId(), OutboxMessage.NOTIFY,
                json.writeValueAsString(payload), clock.instant()));
        events.publishEvent(new NotificationQueued(tenant, appointment.getId()));
    }

    public NotificationPayload notificationPayload(OutboxMessage message) {
        return json.readValue(message.getPayload(), NotificationPayload.class).withEventId(message.getId());
    }

    /** Settles a delivered message that has nothing to link (notifications). Repeats are harmless. */
    @Transactional
    public void markSent(UUID messageId) {
        OutboxMessage message = outbox.findById(messageId).orElseThrow();
        if (!message.isProcessed()) {
            message.markDelivered(clock.instant());
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

    @Transactional(readOnly = true)
    public List<OutboxMessage> pendingFor(UUID appointmentId) {
        return outbox.findByAppointmentIdAndProcessedAtIsNull(appointmentId);
    }

    /**
     * Ids and workshops of due messages in every workshop. The caller then handles each one
     * inside its own workshop ({@link #find(UUID)} under {@code TenantContext}).
     */
    @Transactional(readOnly = true)
    public List<DueMessage> due() {
        return outbox.findDueAcrossTenants(clock.instant(), properties.getDispatcher().getBatchSize());
    }

    /** A message of the current workshop. */
    @Transactional(readOnly = true)
    public Optional<OutboxMessage> find(UUID messageId) {
        return outbox.findById(messageId);
    }

    public OpenJobCardPayload payload(OutboxMessage message) {
        OpenJobCardPayload payload = json.readValue(message.getPayload(), OpenJobCardPayload.class);
        return payload.withDefaults(message.getTenantId());
    }

    /** Settles the message and stores the job card id on the appointment. Repeats are harmless. */
    @Transactional
    public void markDelivered(UUID messageId, UUID jobCardId) {
        OutboxMessage message = outbox.findById(messageId).orElseThrow();
        if (message.isProcessed()) {
            return;
        }
        Instant now = clock.instant();
        message.markDelivered(now);
        Appointment appointment = appointments.findById(message.getAppointmentId()).orElseThrow();
        if (appointment.getJobCardId() == null) {
            appointment.linkJobCard(jobCardId, now);
        } else if (!appointment.getJobCardId().equals(jobCardId)) {
            log.warn("Appointment {} is linked to job card {} but workshop returned {}", appointment.getId(),
                    appointment.getJobCardId(), jobCardId);
        }
    }

    @Transactional
    public void markFailed(UUID messageId, String error) {
        OutboxMessage message = outbox.findById(messageId).orElseThrow();
        if (message.isProcessed()) {
            return;
        }
        IntegrationProperties.Dispatcher settings = properties.getDispatcher();
        message.markFailed(error, clock.instant(), settings.getInitialBackoff(), settings.getMaxBackoff());
    }
}
