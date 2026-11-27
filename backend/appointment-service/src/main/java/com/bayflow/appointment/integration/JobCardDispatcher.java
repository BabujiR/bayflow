package com.bayflow.appointment.integration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.bayflow.appointment.application.OutboxService;
import com.bayflow.appointment.application.OutboxService.MailQueued;
import com.bayflow.appointment.application.OutboxService.NotificationQueued;
import com.bayflow.appointment.config.IntegrationProperties;
import com.bayflow.appointment.domain.OutboxMessage;
import com.bayflow.appointment.infrastructure.OutboxRepository.DueMessage;
import com.bayflow.security.TenantContext;

/**
 * Delivers outbox messages: job-card requests to workshop-service and notification events to
 * notification-service. Delivery is attempted right away and then retried on a schedule; both
 * receivers are idempotent (on the appointment id, on the event id), so a repeated delivery
 * never creates a second job card or notification.
 */
@Component
public class JobCardDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobCardDispatcher.class);

    private final OutboxService outbox;
    private final WorkshopClient workshop;
    private final NotificationClient notifications;
    private final MailClient mail;
    private final IntegrationProperties properties;
    /**
     * Own transactions for e-mail bookkeeping: right after a commit the finished transaction's
     * resources are still bound, and a joined write there would never be committed.
     */
    private final TransactionTemplate fresh;
    /** Sends notification events off the request thread, so a slow receiver never slows a booking. */
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    public JobCardDispatcher(OutboxService outbox, WorkshopClient workshop, NotificationClient notifications,
            MailClient mail, IntegrationProperties properties, PlatformTransactionManager transactions) {
        this.fresh = new TransactionTemplate(transactions);
        this.fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.outbox = outbox;
        this.workshop = workshop;
        this.notifications = notifications;
        this.mail = mail;
        this.properties = properties;
    }

    /** Tries this appointment's pending messages now. Never throws: failures stay in the outbox. */
    public void dispatchFor(UUID appointmentId) {
        outbox.pendingFor(appointmentId).forEach(this::deliver);
    }

    /** Right after a change with a notification event commits: send it in the background. */
    @TransactionalEventListener
    public void onNotificationQueued(NotificationQueued queued) {
        if (!properties.getNotifications().isSendImmediately()) {
            return;
        }
        background.execute(() -> TenantContext.run(queued.tenantId(), () -> {
            try {
                outbox.pendingFor(queued.appointmentId()).stream()
                        .filter(m -> OutboxMessage.NOTIFY.equals(m.getType()))
                        .forEach(this::deliver);
            } catch (RuntimeException e) {
                log.warn("Could not send notification events for appointment {}: {}", queued.appointmentId(),
                        e.getMessage());
            }
        }));
    }

    /**
     * Right after a change with an e-mail commits: send it now, on this thread (outside the
     * transaction, so no lock waits on auth-service). A failure stays in the outbox and is retried.
     */
    @TransactionalEventListener
    public void onMailQueued(MailQueued queued) {
        try {
            TenantContext.run(queued.tenantId(), () -> fresh.execute(s -> outbox.pendingFor(queued.appointmentId()))
                    .stream()
                    .filter(m -> OutboxMessage.SEND_MAIL.equals(m.getType()))
                    .forEach(this::deliver));
        } catch (RuntimeException e) {
            log.warn("Could not send e-mails for appointment {}: {}", queued.appointmentId(), e.getMessage());
        }
    }

    /**
     * Retries every message whose next attempt is due, in all workshops. Finding them is the one
     * cross-workshop read (ids and workshops only); each message is then loaded and delivered
     * inside its own workshop. Returns how many were tried.
     */
    public int dispatchDue() {
        List<DueMessage> due = outbox.due();
        due.forEach(next -> TenantContext.run(next.getTenantId(), () -> outbox.find(next.getId())
                .filter(message -> !message.isProcessed())
                .ifPresent(this::deliver)));
        return due.size();
    }

    @PreDestroy
    void stop() {
        background.shutdown();
    }

    /** Sends one message, if no other sender has claimed it (see {@link OutboxService#claim}). */
    private void deliver(OutboxMessage message) {
        if (!outbox.claim(message.getId())) {
            log.debug("Message {} is being sent elsewhere", message.getId());
            return;
        }
        switch (message.getType()) {
            case OutboxMessage.NOTIFY -> deliverNotification(message);
            case OutboxMessage.SEND_MAIL -> deliverMail(message);
            default -> deliverJobCard(message);
        }
    }

    private void deliverMail(OutboxMessage message) {
        try {
            mail.send(message.getTenantId(), outbox.mail(message));
        } catch (RuntimeException e) {
            log.warn("Could not send e-mail {} for appointment {} (attempt {}): {}", message.getId(),
                    message.getAppointmentId(), message.getAttempts() + 1, e.getMessage());
            fresh.executeWithoutResult(s -> settleFailure(message, e));
            return;
        }
        try {
            fresh.executeWithoutResult(s -> outbox.markMailSent(message.getId()));
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Message {} was settled concurrently", message.getId());
        }
    }

    private void deliverJobCard(OutboxMessage message) {
        JobCardRef jobCard;
        try {
            jobCard = workshop.openJobCard(outbox.payload(message));
        } catch (RuntimeException e) {
            log.warn("Could not open job card for appointment {} (attempt {}): {}", message.getAppointmentId(),
                    message.getAttempts() + 1, e.getMessage());
            settleFailure(message, e);
            return;
        }
        try {
            outbox.markDelivered(message.getId(), jobCard.id());
            log.info("Opened job card {} for appointment {}", jobCard.id(), message.getAppointmentId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Message {} was settled concurrently", message.getId());
        }
    }

    private void deliverNotification(OutboxMessage message) {
        try {
            notifications.send(outbox.notificationPayload(message));
        } catch (RuntimeException e) {
            log.warn("Could not send notification event {} (attempt {}): {}", message.getId(),
                    message.getAttempts() + 1, e.getMessage());
            settleFailure(message, e);
            return;
        }
        try {
            outbox.markSent(message.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Message {} was settled concurrently", message.getId());
        }
    }

    private void settleFailure(OutboxMessage message, RuntimeException cause) {
        try {
            outbox.markFailed(message.getId(), cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Message {} was settled concurrently", message.getId());
        }
    }
}
