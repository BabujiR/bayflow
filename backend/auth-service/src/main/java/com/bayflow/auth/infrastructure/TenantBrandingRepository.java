package com.bayflow.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.TenantBranding;

@Transactional(readOnly = true)
public interface TenantBrandingRepository extends JpaRepository<TenantBranding, UUID> {

    Optional<TenantBranding> findByTenantId(UUID tenantId);
}
