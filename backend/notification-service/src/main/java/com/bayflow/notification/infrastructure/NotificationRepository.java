package com.bayflow.notification.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.notification.domain.Notification;

/**
 * Every query runs in a transaction, so PostgreSQL knows the workshop and the recipient
 * (row-level security). The queries also name the recipient themselves, so a misconfigured
 * database still could not show one user another's notifications.
 */
@Transactional(readOnly = true)
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.recipientId = :recipient order by n.createdAt desc, n.id desc")
    Page<Notification> findForRecipient(@Param("recipient") UUID recipient, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipient and n.readAt is null")
    int markAllRead(@Param("recipient") UUID recipient, @Param("now") Instant now);

    /** Deletes old notifications in every workshop (owner-run function, migration V1). */
    @Transactional
    @Query(value = "select {h-schema}ntf_delete_before(:cutoff)", nativeQuery = true)
    int deleteBefore(@Param("cutoff") Instant cutoff);
}
