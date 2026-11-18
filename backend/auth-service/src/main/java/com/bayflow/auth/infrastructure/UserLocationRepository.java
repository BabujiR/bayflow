package com.bayflow.auth.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.UserLocation;

@Transactional(readOnly = true)
public interface UserLocationRepository extends JpaRepository<UserLocation, UUID> {

    List<UserLocation> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<UserLocation> findByUserIdIn(Collection<UUID> userIds);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserLocation a where a.userId = :userId")
    int deleteByUserId(UUID userId);
}
