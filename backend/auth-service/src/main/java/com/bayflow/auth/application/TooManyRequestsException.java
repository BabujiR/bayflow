package com.bayflow.auth.application;

import java.time.Duration;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too Many Requests",
                "Too many login attempts. Please wait a moment and try again.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
