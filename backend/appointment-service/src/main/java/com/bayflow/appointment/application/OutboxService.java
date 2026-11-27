package com.bayflow.appointment.application;

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

import com.bayflow.appointment.config.IntegrationProperties;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.OutboxMessage;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.infrastructure.OutboxRepository;
import com.bayflow.appointment.infrastructure.OutboxRepository.DueMessage;
import com.bayflow.appointment.integration.MailClient;
import com.bayflow.appointment.integration.NotificationPayload;
import com.bayflow.appointment.integration.OpenJobCardPayload;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;

import tools.jackson.databind.json.JsonMapper;

/** Database side of the outbox: storing, finding and settling messages. */
@Service
public class OutboxService {

    /** How long a claimed message is left to its sender (longer than any HTTP timeout here). */
    static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

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

    /**
     * A quick estimate's job card, opened before the visit (the car's last known reading), with
     * what the customer asked for as its first finding. Joins the caller's transaction.
     */
    @Transactional
    public void enqueueEstimateJobCard(Appointment appointment, String request, String requestedBy) {
        OpenJobCardPayload payload = new OpenJobCardPayload(appointment.getId(), appointment.getVehicle().getId(),
                appointment.getVehicle().getVin(), appointment.getVehicle().getRegistration(),
                appointment.getCustomer().getUserId(), appointment.getCustomer().getName(),
                appointment.getVehicle().getMileage(), appointment.getTenantId(), appointment.getLocationId(), true,
                request, requestedBy);
        outbox.save(OutboxMessage.create(appointment.getId(), OutboxMessage.CREATE_JOB_CARD,
                json.writeValueAsString(payload), clock.instant()));
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

    /** An e-mail was queued; sent right after the transaction commits (see the dispatcher). */
    public record MailQueued(UUID tenantId, UUID appointmentId) {
    }

    /**
     * Queues an e-mail in the caller's transaction: it is sent (through auth-service) only if the
     * change commits, after the commit, so no database lock waits on the mail service; failures
     * are retried like any outbox message.
     */
    @Transactional
    public void enqueueMail(UUID appointmentId, MailClient.Mail mail) {
        UUID tenant = TenantContext.require();
        outbox.save(OutboxMessage.create(appointmentId, OutboxMessage.SEND_MAIL, json.writeValueAsString(mail),
                clock.instant()));
        events.publishEvent(new MailQueued(tenant, appointmentId));
    }

    public MailClient.Mail mail(OutboxMessage message) {
        return json.readValue(message.getPayload(), MailClient.Mail.class);
    }

    /** Settles a sent e-mail and forgets its content (it may hold a private link). */
    @Transactional
    public void markMailSent(UUID messageId) {
        OutboxMessage message = outbox.findById(messageId).orElseThrow();
        if (!message.isProcessed()) {
            message.markDelivered(clock.instant());
            message.forgetPayload();
        }
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
        return outbox.findByAppointmentIdAndProcessedAtIsNullAndFailedAtIsNull(appointmentId);
    }

    /**
     * Ids and workshops of due messages in every workshop. The caller then handles each one
     * inside its own workshop ({@link #find(UUID)} under {@code TenantContext}).
     */
    @Transactional(readOnly = true)
    public List<DueMessage> due() {
        return outbox.findDueAcrossTenants(clock.instant(), properties.getDispatcher().getBatchSize());
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
        if (appointment.getJobCardId() == null && !appointment.canLinkJobCard()) {
            // An estimate's card that arrived after the visit was scheduled or cancelled: linked at check-in.
            log.info("Job card {} of appointment {} not linked while {}", jobCardId, appointment.getId(),
                    appointment.getStatus());
        } else if (appointment.getJobCardId() == null) {
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
        if (message.markFailed(error, clock.instant(), settings.getInitialBackoff(), settings.getMaxBackoff(),
                settings.getMaxAttempts())) {
            log.error("Gave up on outbox message {} ({}, appointment {}) after {} attempts: {}", message.getId(),
                    message.getType(), message.getAppointmentId(), message.getAttempts(), message.getLastError());
        }
    }
}
