package com.bayflow.appointment.integration;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.application.OutboxService;
import com.bayflow.appointment.domain.OutboxMessage;

/**
 * Delivers job-card requests from the outbox to workshop-service. Delivery is attempted
 * right after check-in and then retried on a schedule; workshop-service is idempotent on
 * the appointment id, so a repeated delivery never creates a second job card.
 */
@Component
public class JobCardDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobCardDispatcher.class);

    private final OutboxService outbox;
    private final WorkshopClient workshop;

    public JobCardDispatcher(OutboxService outbox, WorkshopClient workshop) {
        this.outbox = outbox;
        this.workshop = workshop;
    }

    /** Tries this appointment's pending messages now. Never throws: failures stay in the outbox. */
    public void dispatchFor(UUID appointmentId) {
        outbox.pendingFor(appointmentId).forEach(this::deliver);
    }

    /** Retries every message whose next attempt is due. Returns how many were tried. */
    public int dispatchDue() {
        List<OutboxMessage> due = outbox.due();
        due.forEach(this::deliver);
        return due.size();
    }

    private void deliver(OutboxMessage message) {
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

    private void settleFailure(OutboxMessage message, RuntimeException cause) {
        try {
            outbox.markFailed(message.getId(), cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Message {} was settled concurrently", message.getId());
        }
    }
}
