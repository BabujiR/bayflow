package com.bayflow.appointment.bookingservices;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Runs in transactions so the workshop always reaches PostgreSQL (row-level security). */
@Transactional(readOnly = true)
public interface BookingSettingsRepository extends JpaRepository<BookingSettings, UUID> {
}
