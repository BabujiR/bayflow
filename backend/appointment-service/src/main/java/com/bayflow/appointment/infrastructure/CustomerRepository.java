package com.bayflow.appointment.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.domain.Customer;

/**
 * Every query here runs in a transaction (read-only unless a method says otherwise), so the
 * workshop is always passed to PostgreSQL for row-level security: query methods declared in
 * a Spring Data interface are not transactional by default, and without a transaction the
 * database would see no workshop and return nothing.
 */
@Transactional(readOnly = true)
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /** {@code pattern} is a lower-case LIKE pattern, e.g. "%smith%". */
    @Query("""
            select c from Customer c
            where lower(c.name) like :pattern or lower(c.email) like :pattern or c.phone like :pattern
            """)
    Page<Customer> search(@Param("pattern") String pattern, Pageable pageable);
}
