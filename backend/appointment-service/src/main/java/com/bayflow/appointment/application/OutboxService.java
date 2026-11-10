package com.bayflow.appointment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.config.IntegrationProperties;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.OutboxMessage;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.infrastructure.OutboxRepository;
import com.bayflow.appointment.integration.OpenJobCardPayload;

import tools.jackson.databind.json.JsonMapper;

/** Database side of the outbox: storing, finding and settling messages. */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outbox;
    private final AppointmentRepository appointments;
    private final IntegrationProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    public OutboxService(OutboxRepository outbox, AppointmentRepository appointments,
            IntegrationProperties properties, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.appointments = appointments;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so the message commits together with the check-in. */
    @Transactional
    public void enqueueJobCard(Appointment appointment) {
        OpenJobCardPayload payload = new OpenJobCardPayload(appointment.getId(), appointment.getVehicle().getId(),
                appointment.getVehicle().getVin(), appointment.getVehicle().getRegistration(),
                appointment.getCustomer().getUserId(), appointment.getCustomer().getName(),
                appointment.getArrivalMileage());
        outbox.save(OutboxMessage.create(appointment.getId(), OutboxMessage.CREATE_JOB_CARD,
                json.writeValueAsString(payload), clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> pendingFor(UUID appointmentId) {
        return outbox.findByAppointmentIdAndProcessedAtIsNull(appointmentId);
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> due() {
        return outbox.findByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(clock.instant(),
                PageRequest.of(0, properties.getDispatcher().getBatchSize()));
    }

    public OpenJobCardPayload payload(OutboxMessage message) {
        return json.readValue(message.getPayload(), OpenJobCardPayload.class);
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
