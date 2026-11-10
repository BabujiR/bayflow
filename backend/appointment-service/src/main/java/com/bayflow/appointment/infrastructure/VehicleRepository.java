package com.bayflow.appointment.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bayflow.appointment.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    boolean existsByVin(String vin);

    Page<Vehicle> findByOwnerId(UUID customerId, Pageable pageable);
}
