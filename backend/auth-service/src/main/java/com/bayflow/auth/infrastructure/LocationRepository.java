package com.bayflow.auth.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Location;

/** Locations of the current organization (row-level security and the Hibernate tenant filter). */
@Transactional(readOnly = true)
public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findAllByOrderByNameAsc();

    java.util.Optional<Location> findByCode(String code);
}
