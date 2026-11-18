package com.bayflow.workshop.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;

/**
 * Every query here runs in a transaction (read-only unless a method says otherwise), so the
 * workshop is always passed to PostgreSQL for row-level security: query methods declared in
 * a Spring Data interface are not transactional by default, and without a transaction the
 * database would see no workshop and return nothing.
 */
@Transactional(readOnly = true)
public interface JobCardRepository extends JpaRepository<JobCard, UUID> {

    Optional<JobCard> findByAppointmentId(UUID appointmentId);

    Page<JobCard> findByStatus(JobCardStatus status, Pageable pageable);

    Page<JobCard> findByCustomerUserId(UUID customerUserId, Pageable pageable);

    Page<JobCard> findByCustomerUserIdAndStatus(UUID customerUserId, JobCardStatus status, Pageable pageable);
}
