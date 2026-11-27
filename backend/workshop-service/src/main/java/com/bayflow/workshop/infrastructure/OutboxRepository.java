package com.bayflow.workshop.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.workshop.domain.OutboxMessage;

/** Runs every query in a transaction, so PostgreSQL knows the workshop (row-level security). */
@Transactional(readOnly = true)
public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    /** A due message: just enough to handle it inside its own workshop. */
    interface DueMessage {
        UUID getId();

        UUID getTenantId();
    }

    /** Undelivered messages due now, in every workshop, via the owner-run ws_due_outbox (V8). */
    @Query(value = "select d.id as id, d.tenant_id as \"tenantId\" from {h-schema}ws_due_outbox(:now, :limit) d",
            nativeQuery = true)
    List<DueMessage> findDueAcrossTenants(@Param("now") Instant now, @Param("limit") int limit);

    /** Undelivered and not given up. */
    List<OutboxMessage> findByJobCardIdAndProcessedAtIsNullAndFailedAtIsNull(UUID jobCardId);

    List<OutboxMessage> findByJobCardId(UUID jobCardId);

    /**
     * Takes the message for one sending attempt: moves its next attempt to {@code leaseUntil}
     * if it is still unsent, not given up and due. 1 when this caller got it, 0 when another
     * sender (the other path, or another instance) already has it. Commits on its own, before the
     * send, so the claim is visible to everyone else while the HTTP call runs.
     */
    @Modifying
    @Transactional
    @Query(value = "update {h-schema}ws_outbox set next_attempt_at = :leaseUntil where id = :id"
            + " and processed_at is null and failed_at is null and next_attempt_at <= :now", nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
