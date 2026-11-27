package com.bayflow.auth.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.auth.mail.MailDispatcher;
import com.bayflow.ops.JobLock;

/** Retries unsent e-mails. Disabled in tests, which trigger runs themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.auth.mail", name = "dispatcher-enabled", havingValue = "true",
        matchIfMissing = true)
public class MailSchedule {

    private final MailDispatcher dispatcher;
    private final JobLock lock;

    public MailSchedule(MailDispatcher dispatcher, JobLock lock) {
        this.dispatcher = dispatcher;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${bayflow.auth.mail.interval-ms:30000}",
            initialDelayString = "${bayflow.auth.mail.interval-ms:30000}")
    void retryUnsent() {
        lock.runExclusively("mail-dispatch", Duration.ZERO, Duration.ofMinutes(10), dispatcher::dispatchDue);
    }
}
