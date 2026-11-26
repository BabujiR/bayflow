package com.bayflow.appointment.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.domain.Vehicle;

/**
 * Every query here runs in a transaction (read-only unless a method says otherwise), so the
 * workshop is always passed to PostgreSQL for row-level security: query methods declared in
 * a Spring Data interface are not transactional by default, and without a transaction the
 * database would see no workshop and return nothing.
 */
@Transactional(readOnly = true)
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    boolean existsByVin(String vin);

    Page<Vehicle> findByOwnerId(UUID customerId, Pageable pageable);

    boolean existsByVinAndIdNot(String vin, UUID id);

    java.util.Optional<Vehicle> findByVin(String vin);

    /** The customer's cars, for matching an online booking to one they booked before. */
    java.util.List<Vehicle> findAllByOwnerId(UUID customerId);
}
