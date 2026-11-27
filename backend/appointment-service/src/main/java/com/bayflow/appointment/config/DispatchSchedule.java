package com.bayflow.appointment.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.appointment.integration.JobCardDispatcher;
import com.bayflow.ops.JobLock;

/** Retries undelivered job-card requests. Disabled in tests, which trigger runs themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.integration.dispatcher", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class DispatchSchedule {

    private final JobCardDispatcher dispatcher;
    private final JobLock lock;

    public DispatchSchedule(JobCardDispatcher dispatcher, JobLock lock) {
        this.dispatcher = dispatcher;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${bayflow.integration.dispatcher.interval-ms:10000}",
            initialDelayString = "${bayflow.integration.dispatcher.interval-ms:10000}")
    void retryUndelivered() {
        lock.runExclusively("outbox-dispatch", Duration.ZERO, Duration.ofMinutes(10), dispatcher::dispatchDue);
    }
}
