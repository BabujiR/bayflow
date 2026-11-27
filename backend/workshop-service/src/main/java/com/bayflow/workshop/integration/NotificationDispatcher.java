package com.bayflow.workshop.integration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.OutboxService;
import com.bayflow.workshop.application.OutboxService.NotificationQueued;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.domain.OutboxMessage;
import com.bayflow.workshop.infrastructure.OutboxRepository.DueMessage;

/**
 * Delivers notification events from the outbox to notification-service: right after the change
 * commits (in the background, so a slow receiver never slows the request) and then on a
 * schedule until they arrive. notification-service ignores a repeated event id.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final OutboxService outbox;
    private final NotificationClient client;
    private final WorkshopProperties properties;
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    public NotificationDispatcher(OutboxService outbox, NotificationClient client, WorkshopProperties properties) {
        this.outbox = outbox;
        this.client = client;
        this.properties = properties;
    }

    @TransactionalEventListener
    public void onQueued(NotificationQueued queued) {
        if (!properties.getNotifications().isSendImmediately()) {
            return;
        }
        background.execute(() -> TenantContext.run(queued.tenantId(), () -> {
            try {
                dispatchFor(queued.jobCardId());
            } catch (RuntimeException e) {
                log.warn("Could not send notification events for job card {}: {}", queued.jobCardId(),
                        e.getMessage());
            }
        }));
    }

    /** Tries this job card's pending events now. Never throws for a failed delivery. */
    public void dispatchFor(UUID jobCardId) {
        outbox.pendingFor(jobCardId).forEach(this::deliver);
    }

    /** Retries every event whose next attempt is due, in all workshops. Returns how many were tried. */
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

    /** Sends one event, if no other sender has claimed it (see {@link OutboxService#claim}). */
    private void deliver(OutboxMessage message) {
        if (!outbox.claim(message.getId())) {
            log.debug("Message {} is being sent elsewhere", message.getId());
            return;
        }
        try {
            client.send(outbox.payload(message));
        } catch (RuntimeException e) {
            log.warn("Could not send notification event {} (attempt {}): {}", message.getId(),
                    message.getAttempts() + 1, e.getMessage());
            try {
                outbox.markFailed(message.getId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (ObjectOptimisticLockingFailureException concurrent) {
                log.debug("Message {} was settled concurrently", message.getId());
            }
            return;
        }
        try {
            outbox.markSent(message.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Message {} was settled concurrently", message.getId());
        }
    }
}
