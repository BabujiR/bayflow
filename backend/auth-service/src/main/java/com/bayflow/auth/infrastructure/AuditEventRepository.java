package com.bayflow.auth.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.AuditEvent;

/** Runs every query in a transaction, so PostgreSQL knows the workshop (row-level security). */
@Transactional(readOnly = true)
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    Page<AuditEvent> findByTargetIdOrderByOccurredAtDescIdDesc(UUID targetId, Pageable pageable);
}
