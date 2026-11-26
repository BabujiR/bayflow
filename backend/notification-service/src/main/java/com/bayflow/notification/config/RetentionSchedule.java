package com.bayflow.notification.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.notification.application.NotificationService;
import com.bayflow.ops.JobLock;

/** Deletes notifications older than {@code bayflow.notification.retention.keep}, every night. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.notification.retention", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class RetentionSchedule {

    private final NotificationService notifications;
    private final JobLock lock;

    public RetentionSchedule(NotificationService notifications, JobLock lock) {
        this.notifications = notifications;
        this.lock = lock;
    }

    @Scheduled(cron = "${bayflow.notification.retention.cron:0 17 3 * * *}")
    void deleteOld() {
        lock.runExclusively("retention", Duration.ofMinutes(5), Duration.ofHours(1), notifications::deleteOld);
    }
}
