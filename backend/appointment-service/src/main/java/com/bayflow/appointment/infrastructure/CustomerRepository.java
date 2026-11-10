package com.bayflow.appointment.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bayflow.appointment.domain.Customer;

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
