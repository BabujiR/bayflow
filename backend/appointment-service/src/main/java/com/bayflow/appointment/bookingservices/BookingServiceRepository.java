package com.bayflow.appointment.bookingservices;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Runs in transactions so the workshop always reaches PostgreSQL (row-level security). */
@Transactional(readOnly = true)
public interface BookingServiceRepository extends JpaRepository<BookingService, UUID> {

    List<BookingService> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCode(String code);

    @Query("select coalesce(max(s.sortOrder), 0) from BookingService s")
    int maxSortOrder();

    /**
     * One of a new shop's starting services, unless it exists: two first visits at once must not
     * both insert them (the unique code makes the second a no-op).
     */
    @Modifying
    @Transactional
    @Query(value = """
            insert into {h-schema}apt_booking_services
                (id, tenant_id, code, name, description, price_kind, active, sort_order,
                 created_at, updated_at, version)
            values (:id, :tenant, :code, :name, :description, 'NONE', true, :sortOrder, :now, :now, 0)
            on conflict (tenant_id, code) do nothing""", nativeQuery = true)
    int insertStarter(@Param("id") UUID id, @Param("tenant") UUID tenant, @Param("code") String code,
            @Param("name") String name, @Param("description") String description, @Param("sortOrder") int sortOrder,
            @Param("now") Instant now);
}
