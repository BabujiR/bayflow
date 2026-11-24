package com.bayflow.auth.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bayflow.auth.domain.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByWorkshopIdIn(Collection<UUID> workshopIds);

    /** Unused invites expiring within (now, until] that have had no reminder yet. */
    @Query("""
            select i from Invitation i
            where i.status = com.bayflow.auth.domain.Invitation.Status.PENDING
              and i.remindedAt is null and i.expiresAt > :now and i.expiresAt <= :until
            order by i.expiresAt""")
    List<Invitation> dueReminders(@Param("now") Instant now, @Param("until") Instant until);

    /** The invite behind a setup link, locked until the sign-up commits (one workshop per link). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :hash")
    Optional<Invitation> lockByTokenHash(@Param("hash") String tokenHash);

    /** Whether the address has an invite with this status expiring after {@code after}. */
    boolean existsByEmailAndStatusAndExpiresAtAfter(String email, Invitation.Status status, Instant after);

    /**
     * Newest first. {@code expiry}: 0 any, 1 not yet expired, 2 expired (with status PENDING,
     * that is what the console calls Pending and Expired); q in email, contact or shop.
     */
    @Query("""
            select i from Invitation i
            where (:status is null or i.status = :status)
              and (:expiry = 0 or (:expiry = 1 and i.expiresAt > :now) or (:expiry = 2 and i.expiresAt <= :now))
              and (:q is null or lower(i.email) like :q or lower(i.shopName) like :q
                   or lower(i.contactName) like :q)
            order by i.createdAt desc, i.id""")
    Page<Invitation> search(@Param("status") Invitation.Status status, @Param("expiry") int expiry,
            @Param("now") Instant now, @Param("q") String q, Pageable page);
}
