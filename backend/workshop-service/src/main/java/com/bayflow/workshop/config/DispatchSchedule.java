package com.bayflow.workshop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.workshop.integration.NotificationDispatcher;

/** Retries undelivered notification events. Disabled in tests, which trigger runs themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.workshop.notifications", name = "dispatcher-enabled",
        havingValue = "true", matchIfMissing = true)
public class DispatchSchedule {

    private final NotificationDispatcher dispatcher;

    public DispatchSchedule(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${bayflow.workshop.notifications.interval-ms:10000}",
            initialDelayString = "${bayflow.workshop.notifications.interval-ms:10000}")
    void retryUndelivered() {
        dispatcher.dispatchDue();
    }
}
