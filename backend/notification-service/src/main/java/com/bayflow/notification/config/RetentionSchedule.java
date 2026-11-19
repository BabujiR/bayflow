package com.bayflow.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.notification.application.NotificationService;

/** Deletes notifications older than {@code bayflow.notification.retention.keep}, every night. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.notification.retention", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class RetentionSchedule {

    private final NotificationService notifications;

    public RetentionSchedule(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "${bayflow.notification.retention.cron:0 17 3 * * *}")
    void deleteOld() {
        notifications.deleteOld();
    }
}
