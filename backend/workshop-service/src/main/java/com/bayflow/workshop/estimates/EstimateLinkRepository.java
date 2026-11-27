package com.bayflow.workshop.estimates;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Runs every query in a transaction, so PostgreSQL knows the workshop (row-level security). */
@Transactional(readOnly = true)
public interface EstimateLinkRepository extends JpaRepository<EstimateLink, UUID> {

    interface Due {
        UUID getId();

        UUID getTenantId();
    }

    Optional<EstimateLink> findByTokenHash(String tokenHash);

    /** The links of the last send (it and its reminders), oldest first. */
    List<EstimateLink> findByJobCardIdAndReplacedAtIsNullOrderBySentAtAsc(UUID jobCardId);

    /** The link the customer signed on, if they did. */
    Optional<EstimateLink> findFirstByJobCardIdAndDecidedAtIsNotNull(UUID jobCardId);

    /** The workshop of a token, whoever asks (owner-run ws_estimate_link_tenant, V18). */
    @Query(value = "select {h-schema}ws_estimate_link_tenant(:hash)", nativeQuery = true)
    Optional<UUID> tenantOf(@Param("hash") String tokenHash);

    /** Links due a reminder in every workshop (owner-run ws_due_estimate_reminders, V18). */
    @Query(value = "select d.id as id, d.tenant_id as \"tenantId\""
            + " from {h-schema}ws_due_estimate_reminders(:now, :limit) d", nativeQuery = true)
    List<Due> dueReminders(@Param("now") Instant now, @Param("limit") int limit);
}
