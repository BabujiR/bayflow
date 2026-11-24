package com.bayflow.auth.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Tenant;

/**
 * Every query here runs in a transaction (read-only unless a method says otherwise), so the
 * workshop is always passed to PostgreSQL for row-level security: query methods declared in
 * a Spring Data interface are not transactional by default, and without a transaction the
 * database would see no workshop and return nothing.
 */
@Transactional(readOnly = true)
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Workshops for the console, newest first, without {@code exclude} (the console's own
     * organization). status / test null = all; q in name or address.
     */
    @Query("""
            select t from Tenant t
            where t.id <> :exclude
              and (:status is null or t.status = :status)
              and (:test is null or t.test = :test)
              and (:q is null or lower(t.name) like :q or t.slug like :q)
            order by t.createdAt desc, t.id""")
    Page<Tenant> search(@Param("exclude") UUID exclude, @Param("status") Tenant.Status status,
            @Param("test") Boolean test, @Param("q") String q, Pageable page);

    /** Per-workshop numbers for the console (an owner-run function; counts and owner only). */
    interface WorkshopStats {
        UUID getTenantId();
        Integer getStaff();
        Integer getCustomers();
        Integer getLocations();
        String getOwnerUsername();
        String getOwnerEmail();
    }

    @Query(value = """
            select s.tenant_id as "tenantId", s.staff as "staff", s.customers as "customers",
                   s.locations as "locations", s.owner_username as "ownerUsername",
                   s.owner_email as "ownerEmail"
            from {h-schema}auth_workshop_stats() s""", nativeQuery = true)
    List<WorkshopStats> stats();
}
