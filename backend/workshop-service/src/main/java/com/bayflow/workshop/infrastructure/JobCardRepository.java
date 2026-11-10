package com.bayflow.workshop.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;

public interface JobCardRepository extends JpaRepository<JobCard, UUID> {

    Optional<JobCard> findByAppointmentId(UUID appointmentId);

    Page<JobCard> findByStatus(JobCardStatus status, Pageable pageable);

    Page<JobCard> findByCustomerUserId(UUID customerUserId, Pageable pageable);

    Page<JobCard> findByCustomerUserIdAndStatus(UUID customerUserId, JobCardStatus status, Pageable pageable);
}
