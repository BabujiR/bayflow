package com.bayflow.workshop.reminders;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ServiceIntervalRepository extends JpaRepository<ServiceInterval, UUID> {

    List<ServiceInterval> findAllByOrderByPositionAscNameAsc();
}
