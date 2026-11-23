package com.bayflow.workshop.quickbooks;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** The organization's connection (row-level security: at most one row is ever visible). */
public interface QboConnectionRepository extends JpaRepository<QboConnection, UUID> {

    @Query("select c from QboConnection c")
    Optional<QboConnection> findCurrent();

    /** Locked while its tokens are refreshed, so two requests don't both spend the refresh token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from QboConnection c")
    Optional<QboConnection> findCurrentForUpdate();
}
