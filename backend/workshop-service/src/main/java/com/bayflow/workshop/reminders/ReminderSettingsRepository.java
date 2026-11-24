package com.bayflow.workshop.reminders;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, UUID> {

    /**
     * Makes the organization's settings row if there is none; 1 when this call made it. Two
     * first requests at once (the settings page asks for intervals and settings together) can't
     * both make it: the second waits for the first and then does nothing.
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into {h-schema}ws_reminder_settings
                (tenant_id, enabled, remind_days_before, miles_per_day, defaults_added, updated_at, version)
            values (:tenant, true, 14, 30, true, :now, 0)
            on conflict (tenant_id) do nothing""", nativeQuery = true)
    int createIfMissing(@Param("tenant") UUID tenant, @Param("now") Instant now);
}
