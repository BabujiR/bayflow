package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bayflow.appointment.domain.OutboxMessage;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    /** Undelivered messages whose retry time has come, oldest first. */
    List<OutboxMessage> findByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant now,
            Pageable pageable);

    List<OutboxMessage> findByAppointmentIdAndProcessedAtIsNull(UUID appointmentId);

    List<OutboxMessage> findByAppointmentId(UUID appointmentId);
}
