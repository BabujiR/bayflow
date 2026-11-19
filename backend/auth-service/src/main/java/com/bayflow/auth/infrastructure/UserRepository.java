package com.bayflow.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;

/**
 * Every query here runs in a transaction (read-only unless a method says otherwise), so the
 * workshop is always passed to PostgreSQL for row-level security: query methods declared in
 * a Spring Data interface are not transactional by default, and without a transaction the
 * database would see no workshop and return nothing.
 */
@Transactional(readOnly = true)
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u join u.roles r where r = :role")
    boolean existsWithRole(@Param("role") Role role);

    /** Enabled users with one of the roles who work at the location (notification-service asks). */
    @Query("""
            select distinct u.id from User u join u.roles r
            where u.enabled = true and r in :roles
              and exists (select 1 from UserLocation l where l.userId = u.id and l.locationId = :locationId)
            """)
    java.util.List<UUID> findEnabledIdsWithRolesAt(@Param("roles") java.util.Collection<Role> roles,
            @Param("locationId") UUID locationId);
}
