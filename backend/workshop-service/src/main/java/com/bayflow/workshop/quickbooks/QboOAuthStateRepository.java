package com.bayflow.workshop.quickbooks;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QboOAuthStateRepository extends JpaRepository<QboOAuthState, UUID> {

    Optional<QboOAuthState> findByStateHash(String stateHash);

    @Modifying
    @Query("delete from QboOAuthState s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
