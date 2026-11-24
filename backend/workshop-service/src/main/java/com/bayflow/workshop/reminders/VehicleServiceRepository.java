package com.bayflow.workshop.reminders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface VehicleServiceRepository extends JpaRepository<VehicleService, UUID> {

    Optional<VehicleService> findByVehicleIdAndIntervalId(UUID vehicleId, UUID intervalId);

    List<VehicleService> findByVehicleId(UUID vehicleId);

    /** Due before {@code before}, for active intervals, soonest first. */
    @Query("""
            select v from VehicleService v
            where v.dueAt < :before
              and exists (select i.id from ServiceInterval i where i.id = v.intervalId and i.active = true)
            order by v.dueAt, v.registration""")
    List<VehicleService> findDueBefore(@Param("before") Instant before);
}
