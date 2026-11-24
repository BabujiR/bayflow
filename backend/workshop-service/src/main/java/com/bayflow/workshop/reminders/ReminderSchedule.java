package com.bayflow.workshop.reminders;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.security.TenantContext;

/**
 * Sends the day's maintenance reminders, organization by organization (by default 14:00 UTC,
 * mid-morning in the US). Off in tests, which call {@link #runOnce()} themselves.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.workshop.reminders", name = "scheduler-enabled", havingValue = "true",
        matchIfMissing = true)
public class ReminderSchedule {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedule.class);

    private final ReminderService reminders;

    public ReminderSchedule(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${bayflow.workshop.reminders.cron:0 0 14 * * *}", zone = "UTC")
    void daily() {
        runOnce();
    }

    /** One pass over every organization with something due; one failing doesn't stop the rest. */
    public void runOnce() {
        for (UUID tenant : reminders.tenantsToRemind()) {
            try {
                TenantContext.run(tenant, reminders::remindDue);
            } catch (RuntimeException e) {
                log.warn("Service reminders for organization {} failed: {}", tenant, e.toString());
            }
        }
    }
}
