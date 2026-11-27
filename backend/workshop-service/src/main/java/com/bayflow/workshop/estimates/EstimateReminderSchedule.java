package com.bayflow.workshop.estimates;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.ops.JobLock;

/** Every 15 minutes: reminders for estimates still waiting for the customer. Off in tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.workshop.estimates", name = "reminders-enabled", havingValue = "true",
        matchIfMissing = true)
public class EstimateReminderSchedule {

    private final EstimateLinks links;
    private final JobLock lock;

    public EstimateReminderSchedule(EstimateLinks links, JobLock lock) {
        this.links = links;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${bayflow.workshop.estimates.reminder-interval-ms:900000}",
            initialDelayString = "${bayflow.workshop.estimates.reminder-interval-ms:900000}")
    void remind() {
        lock.runExclusively("estimate-reminders", Duration.ZERO, Duration.ofMinutes(30), links::sendDueReminders);
    }
}
