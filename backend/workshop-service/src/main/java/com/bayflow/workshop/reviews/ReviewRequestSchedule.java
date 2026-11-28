package com.bayflow.workshop.reviews;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.bayflow.ops.JobLock;

/** Every 15 minutes: the review requests that are due. One instance at a time. Off in tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "bayflow.workshop.reviews", name = "sender-enabled", havingValue = "true",
        matchIfMissing = true)
public class ReviewRequestSchedule {

    private final ReviewRequests reviews;
    private final JobLock lock;

    public ReviewRequestSchedule(ReviewRequests reviews, JobLock lock) {
        this.reviews = reviews;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${bayflow.workshop.reviews.sender-interval-ms:900000}",
            initialDelayString = "${bayflow.workshop.reviews.sender-interval-ms:900000}")
    void send() {
        lock.runExclusively("review-requests", Duration.ZERO, Duration.ofMinutes(30), reviews::sendDue);
    }
}
