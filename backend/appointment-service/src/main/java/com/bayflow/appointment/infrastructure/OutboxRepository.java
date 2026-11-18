package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bayflow.appointment.domain.OutboxMessage;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    /** A due message: just enough to handle it inside its own workshop. */
    interface DueMessage {
        UUID getId();

        UUID getTenantId();
    }

    /**
     * Undelivered messages whose retry time has come, oldest first, in every workshop. Row-level
     * security hides other workshops' rows, so this goes through the owner-run database function
     * {@code apt_due_outbox} (migration V4), which returns only ids and workshops.
     */
    @Query(value = "select d.id as id, d.tenant_id as \"tenantId\" from {h-schema}apt_due_outbox(:now, :limit) d",
            nativeQuery = true)
    List<DueMessage> findDueAcrossTenants(@Param("now") Instant now, @Param("limit") int limit);

    List<OutboxMessage> findByAppointmentIdAndProcessedAtIsNull(UUID appointmentId);

    List<OutboxMessage> findByAppointmentId(UUID appointmentId);
}
