package com.bayflow.workshop.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.ops.JobLock;
import com.bayflow.workshop.integration.NotificationDispatcher;

/** Retries undelivered notification events. Disabled in tests, which trigger runs themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.workshop.notifications", name = "dispatcher-enabled",
        havingValue = "true", matchIfMissing = true)
public class DispatchSchedule {

    private final NotificationDispatcher dispatcher;
    private final JobLock lock;

    public DispatchSchedule(NotificationDispatcher dispatcher, JobLock lock) {
        this.dispatcher = dispatcher;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${bayflow.workshop.notifications.interval-ms:10000}",
            initialDelayString = "${bayflow.workshop.notifications.interval-ms:10000}")
    void retryUndelivered() {
        lock.runExclusively("outbox-dispatch", Duration.ZERO, Duration.ofMinutes(10), dispatcher::dispatchDue);
    }
}
