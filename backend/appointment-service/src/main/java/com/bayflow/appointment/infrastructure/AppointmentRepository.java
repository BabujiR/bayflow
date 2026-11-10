package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, AppointmentSearch {

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
