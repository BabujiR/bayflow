package com.bayflow.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.auth.mail.MailDispatcher;

/** Retries unsent e-mails. Disabled in tests, which trigger runs themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.auth.mail", name = "dispatcher-enabled", havingValue = "true",
        matchIfMissing = true)
public class MailSchedule {

    private final MailDispatcher dispatcher;

    public MailSchedule(MailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${bayflow.auth.mail.interval-ms:30000}",
            initialDelayString = "${bayflow.auth.mail.interval-ms:30000}")
    void retryUnsent() {
        dispatcher.dispatchDue();
    }
}
