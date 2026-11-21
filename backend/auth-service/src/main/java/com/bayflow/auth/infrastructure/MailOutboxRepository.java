package com.bayflow.auth.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.MailMessage;

/** Runs every query in a transaction, so PostgreSQL knows the workshop (row-level security). */
@Transactional(readOnly = true)
public interface MailOutboxRepository extends JpaRepository<MailMessage, UUID> {

    /** A due e-mail: just enough to handle it inside its own workshop. */
    interface DueMail {
        UUID getId();

        UUID getTenantId();
    }

    /** Unsent e-mails due now, in every workshop, via the owner-run auth_due_mail (V12). */
    @Query(value = "select d.id as id, d.tenant_id as \"tenantId\" from {h-schema}auth_due_mail(:now, :limit) d",
            nativeQuery = true)
    List<DueMail> findDueAcrossTenants(@Param("now") Instant now, @Param("limit") int limit);
}
