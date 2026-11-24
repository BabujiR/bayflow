package com.bayflow.workshop.reminders;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, UUID> {
}
