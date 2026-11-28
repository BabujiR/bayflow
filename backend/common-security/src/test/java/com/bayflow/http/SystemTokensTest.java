package com.bayflow.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.bayflow.ops.RequestIds;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/** SYSTEM tokens from a stand-in auth-service, over the shared JDK HTTP client. */
class SystemTokensTest {

    static final WireMockServer AUTH = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String TOKEN = "/api/v1/auth/token";

    /** A clock the test moves. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-28T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final TestClock clock = new TestClock();
    SystemTokens tokens;

    @BeforeAll
    static void start() {
        AUTH.start();
    }

    @AfterAll
    static void stop() {
        AUTH.stop();
    }

    @BeforeEach
    void setUp() {
        AUTH.resetAll();
        AUTH.stubFor(post(urlEqualTo(TOKEN)).willReturn(okJson(
                "{\"accessToken\":\"t1\",\"expiresAt\":\"2026-11-28T12:10:00Z\",\"other\":1}")));
        RestClient auth = ServiceHttp.internal(AUTH.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        tokens = new SystemTokens(auth, "workshop-service", "test-secret", clock);
    }

    @Test
    void aTokenIsFetchedOnceAndKeptUntilShortlyBeforeItExpires() {
        assertThat(tokens.token()).isEqualTo("t1");
        assertThat(tokens.token()).isEqualTo("t1");
        AUTH.verify(1, postRequestedFor(urlEqualTo(TOKEN)).withRequestBody(
                equalToJson("{\"clientId\":\"workshop-service\",\"clientSecret\":\"test-secret\"}")));

        // 60 s before it expires, a new one is fetched.
        clock.now = Instant.parse("2026-11-28T12:09:01Z");
        tokens.token();
        AUTH.verify(2, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void invalidateFetchesANewOne() {
        tokens.token();
        tokens.invalidate();
        tokens.token();
        AUTH.verify(2, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void callersAtTheSameMomentShareOneRequest() throws Exception {
        AUTH.stubFor(post(urlEqualTo(TOKEN)).willReturn(okJson(
                "{\"accessToken\":\"t2\",\"expiresAt\":\"2026-11-28T12:10:00Z\"}").withFixedDelay(200)));
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> callers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                callers.add(tokens::token);
            }
            for (Future<String> answer : pool.invokeAll(callers)) {
                assertThat(answer.get()).isEqualTo("t2");
            }
        }
        AUTH.verify(1, postRequestedFor(urlEqualTo(TOKEN)));
    }

    @Test
    void noTokenAndSlowAnswersFail() {
        AUTH.stubFor(post(urlEqualTo(TOKEN)).willReturn(okJson("{}")));
        assertThatThrownBy(tokens::token).isInstanceOf(IllegalStateException.class);

        AUTH.stubFor(post(urlEqualTo(TOKEN)).willReturn(aResponse().withFixedDelay(1_000)));
        RestClient impatient = ServiceHttp.internal(AUTH.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(200));
        SystemTokens slow = new SystemTokens(impatient, "workshop-service", "test-secret", clock);
        assertThatThrownBy(slow::token).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void internalCallsCarryARequestIdAndOutsideOnesDoNot() {
        AUTH.stubFor(get(urlEqualTo("/ping")).willReturn(okJson("{}")));
        MDC.put(RequestIds.MDC_KEY, "req-42");
        try {
            ServiceHttp.internal(AUTH.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2)).get().uri("/ping")
                    .retrieve().toBodilessEntity();
            ServiceHttp.external(AUTH.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2)).get().uri("/ping")
                    .retrieve().toBodilessEntity();
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
        List<String> ids = AUTH.findAll(getRequestedFor(urlEqualTo("/ping"))).stream()
                .map(r -> r.getHeader(RequestIds.HEADER)).toList();
        assertThat(ids).containsExactly("req-42", null);
    }
}
