package com.bayflow.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutboxMessageTest {

    private static final Instant NOW = Instant.parse("2026-11-16T14:00:00Z");
    private static final Duration INITIAL = Duration.ofSeconds(10);
    private static final Duration MAX = Duration.ofMinutes(5);

    @Test
    void backoffDoublesAndIsCapped() {
        assertThat(OutboxMessage.backoff(1, INITIAL, MAX)).isEqualTo(Duration.ofSeconds(10));
        assertThat(OutboxMessage.backoff(2, INITIAL, MAX)).isEqualTo(Duration.ofSeconds(20));
        assertThat(OutboxMessage.backoff(3, INITIAL, MAX)).isEqualTo(Duration.ofSeconds(40));
        assertThat(OutboxMessage.backoff(6, INITIAL, MAX)).isEqualTo(Duration.ofMinutes(5));
        assertThat(OutboxMessage.backoff(500, INITIAL, MAX)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void newMessageIsDueImmediately() {
        OutboxMessage message = OutboxMessage.create(UUID.randomUUID(), OutboxMessage.CREATE_JOB_CARD, "{}", NOW);

        assertThat(message.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(message.getAttempts()).isZero();
        assertThat(message.isProcessed()).isFalse();
    }

    @Test
    void failureSchedulesTheNextAttemptAndKeepsTheError() {
        OutboxMessage message = OutboxMessage.create(UUID.randomUUID(), OutboxMessage.CREATE_JOB_CARD, "{}", NOW);

        message.markFailed("x".repeat(2000), NOW, INITIAL, MAX);
        message.markFailed("Connection refused", NOW, INITIAL, MAX);

        assertThat(message.getAttempts()).isEqualTo(2);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(message.getLastError()).isEqualTo("Connection refused");
        assertThat(message.isProcessed()).isFalse();

        message.markFailed(null, NOW, INITIAL, MAX);
        assertThat(message.getLastError()).isNull();
    }

    @Test
    void deliveryClosesTheMessage() {
        OutboxMessage message = OutboxMessage.create(UUID.randomUUID(), OutboxMessage.CREATE_JOB_CARD, "{}", NOW);
        message.markFailed("down", NOW, INITIAL, MAX);

        message.markDelivered(NOW.plusSeconds(30));

        assertThat(message.isProcessed()).isTrue();
        assertThat(message.getProcessedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(message.getLastError()).isNull();
        assertThat(message.getAttempts()).isEqualTo(2);
    }
}
