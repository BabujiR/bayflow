package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;

/**
 * Every query here runs in a transaction (read-only unless a method says otherwise), so the
 * workshop is always passed to PostgreSQL for row-level security: query methods declared in
 * a Spring Data interface are not transactional by default, and without a transaction the
 * database would see no workshop and return nothing.
 */
@Transactional(readOnly = true)
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, AppointmentSearch {

    /** Appointments at a location in the given statuses (archiving a location checks it). */
    long countByLocationIdAndStatusIn(UUID locationId, Collection<AppointmentStatus> statuses);

    /** True if another active appointment of the vehicle overlaps [start, end). */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.vehicle.id = :vehicleId
              and a.id <> :excludeId
              and a.status in :statuses
              and a.scheduledAt < :end
              and a.endsAt > :start
            """)
    boolean existsOverlap(@Param("vehicleId") UUID vehicleId, @Param("excludeId") UUID excludeId,
            @Param("statuses") Collection<AppointmentStatus> statuses, @Param("start") Instant start,
            @Param("end") Instant end);
}
