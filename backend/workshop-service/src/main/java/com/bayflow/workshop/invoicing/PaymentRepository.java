package com.bayflow.workshop.invoicing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Payments across invoices, for exports (row-level security scopes them like their invoices). */
@Transactional(readOnly = true)
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("""
            select p from Payment p join fetch p.invoice
            where p.receivedAt >= :from and p.receivedAt < :to
            order by p.receivedAt, p.id""")
    List<Payment> findReceivedBetween(@Param("from") Instant from, @Param("to") Instant to, Pageable page);
}
