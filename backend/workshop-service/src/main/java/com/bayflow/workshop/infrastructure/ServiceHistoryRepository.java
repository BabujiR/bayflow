package com.bayflow.workshop.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bayflow.workshop.domain.ServiceHistoryEntry;

public interface ServiceHistoryRepository extends JpaRepository<ServiceHistoryEntry, UUID> {

    Page<ServiceHistoryEntry> findByVehicleId(UUID vehicleId, Pageable pageable);

    Page<ServiceHistoryEntry> findByVehicleIdAndCustomerUserId(UUID vehicleId, UUID customerUserId,
            Pageable pageable);
}
