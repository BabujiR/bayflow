package com.bayflow.workshop.reviews;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Runs every query in a transaction, so PostgreSQL knows the workshop (row-level security). */
@Transactional(readOnly = true)
public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, UUID> {

    interface Due {
        UUID getId();

        UUID getTenantId();
    }

    Optional<ReviewRequest> findByInvoiceId(UUID invoiceId);

    Optional<ReviewRequest> findByTokenHash(String tokenHash);

    /** The last request this customer was sent (any location of the workshop). */
    Optional<ReviewRequest> findFirstByCustomerEmailAndStatusOrderBySentAtDesc(String email,
            ReviewRequest.Status status);

    /** Whether the customer asked not to get review requests from this workshop. */
    @Query(value = "select exists(select 1 from {h-schema}ws_review_opt_outs o where o.customer_email = :email)",
            nativeQuery = true)
    boolean optedOut(@Param("email") String email);

    /** The customer asked not to get them again (repeats are harmless). */
    @Modifying
    @Transactional
    @Query(value = "insert into {h-schema}ws_review_opt_outs (tenant_id, customer_email, created_at)"
            + " values (:tenant, :email, :now) on conflict do nothing", nativeQuery = true)
    int optOut(@Param("tenant") UUID tenant, @Param("email") String email, @Param("now") Instant now);

    /** The workshop of a stop link's token, whoever asks (owner-run ws_review_request_tenant, V20). */
    @Query(value = "select {h-schema}ws_review_request_tenant(:hash)", nativeQuery = true)
    Optional<UUID> tenantOf(@Param("hash") String tokenHash);

    /** Requests due to go out in every workshop (owner-run ws_due_review_requests, V20). */
    @Query(value = "select d.id as id, d.tenant_id as \"tenantId\""
            + " from {h-schema}ws_due_review_requests(:now, :limit) d", nativeQuery = true)
    List<Due> due(@Param("now") Instant now, @Param("limit") int limit);
}
