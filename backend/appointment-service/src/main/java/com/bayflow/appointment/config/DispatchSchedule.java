package com.bayflow.appointment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.appointment.integration.JobCardDispatcher;

/** Retries undelivered job-card requests. Disabled in tests, which trigger runs themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.integration.dispatcher", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class DispatchSchedule {

    private final JobCardDispatcher dispatcher;

    public DispatchSchedule(JobCardDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${bayflow.integration.dispatcher.interval-ms:10000}",
            initialDelayString = "${bayflow.integration.dispatcher.interval-ms:10000}")
    void retryUndelivered() {
        dispatcher.dispatchDue();
    }
}
