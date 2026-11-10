package com.bayflow.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.bayflow.auth.MutableClock;
import com.bayflow.auth.config.AuthProperties;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"));
    private final AuthProperties properties = new AuthProperties();
    private final LoginRateLimiter limiter;

    LoginRateLimiterTest() {
        properties.getLogin().setMaxAttemptsPerMinute(3);
        limiter = new LoginRateLimiter(properties, clock);
    }

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.check("alice|10.0.0.1");
        }
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> limiter.check("alice|10.0.0.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(40));
                    assertThat(ex.getStatus().value()).isEqualTo(429);
                });
    }

    @Test
    void newWindowResetsTheCount() {
        for (int i = 0; i < 3; i++) {
            limiter.check("bob|10.0.0.1");
        }
        clock.advance(LoginRateLimiter.WINDOW);

        assertThatCode(() -> limiter.check("bob|10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.check("carol|10.0.0.1");
        }
        assertThatCode(() -> limiter.check("carol|10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("dave|10.0.0.1")).doesNotThrowAnyException();
    }
}
