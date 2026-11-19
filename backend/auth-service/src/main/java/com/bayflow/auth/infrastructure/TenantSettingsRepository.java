package com.bayflow.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.TenantSettings;

/** Runs every query in a transaction, so the workshop reaches PostgreSQL (row-level security). */
@Transactional(readOnly = true)
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {

    Optional<TenantSettings> findByTenantId(UUID tenantId);
}
