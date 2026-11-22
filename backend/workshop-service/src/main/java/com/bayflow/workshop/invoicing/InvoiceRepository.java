package com.bayflow.workshop.invoicing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

/** Read-only transactions by default, so row-level security always knows the workshop. */
@Transactional(readOnly = true)
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    /** The job card's invoice that is not voided (there is at most one). */
    Optional<Invoice> findFirstByJobCardIdAndStatusNot(UUID jobCardId, InvoiceStatus status);
}
