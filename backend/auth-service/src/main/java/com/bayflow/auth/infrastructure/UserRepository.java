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

    /** Sign-in with the e-mail address instead of the username (unique within the workshop). */
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u join u.roles r where r = :role")
    boolean existsWithRole(@Param("role") Role role);

    /** Accounts with the role (e.g. CUSTOMER), or (without) those without it: the team. Paged. */
    @Query(value = "select u from User u where :role member of u.roles",
            countQuery = "select count(u) from User u where :role member of u.roles")
    org.springframework.data.domain.Page<User> findWithRole(@Param("role") Role role,
            org.springframework.data.domain.Pageable pageable);

    @Query(value = "select u from User u where :role not member of u.roles",
            countQuery = "select count(u) from User u where :role not member of u.roles")
    org.springframework.data.domain.Page<User> findWithoutRole(@Param("role") Role role,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Enabled users with one of the roles who work at the location (notification-service asks).
     * An ADMIN works at every location, so an owner who is also an advisor or mechanic is included.
     */
    @Query("""
            select distinct u.id from User u join u.roles r
            where u.enabled = true and r in :roles
              and (:admin member of u.roles
                   or exists (select 1 from UserLocation l where l.userId = u.id and l.locationId = :locationId))
            """)
    java.util.List<UUID> findEnabledIdsWithRolesAt(@Param("roles") java.util.Collection<Role> roles,
            @Param("locationId") UUID locationId, @Param("admin") Role admin);

    default java.util.List<UUID> findEnabledIdsWithRolesAt(java.util.Collection<Role> roles, UUID locationId) {
        return findEnabledIdsWithRolesAt(roles, locationId, Role.ADMIN);
    }

    /** Enabled users other than {@code userId} with the role (the workshop keeps an admin). */
    @Query("""
            select count(distinct u) from User u join u.roles r
            where u.enabled = true and r = :role and u.id <> :userId
            """)
    long countOtherEnabledWithRole(@Param("role") Role role, @Param("userId") UUID userId);
}
