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

    /** Organizations with an un-reminded service due before {@code before}, via ws_due_reminder_tenants (V15). */
    @Query(value = "select t.tenant_id from {h-schema}ws_due_reminder_tenants(:before) t", nativeQuery = true)
    List<UUID> tenantsWithDueReminders(@Param("before") Instant before);
}
