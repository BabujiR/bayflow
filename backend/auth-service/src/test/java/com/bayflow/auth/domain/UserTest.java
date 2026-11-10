package com.bayflow.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    private User user() {
        return User.create("  Customer1 ", " Customer1@Example.COM ", "{bcrypt}hash", Set.of(Role.CUSTOMER), NOW);
    }

    @Test
    void createNormalizesUsernameAndEmail() {
        User user = user();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("customer1");
        assertThat(user.getEmail()).isEqualTo("customer1@example.com");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRequiresARole() {
        assertThatThrownBy(() -> User.create("a", "a@b.c", "h", Set.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locksAfterMaxFailuresAndUnlocksWhenTheLockExpires() {
        User user = user();

        for (int i = 1; i <= 4; i++) {
            assertThat(user.registerFailedLogin(NOW, 5, LOCK)).isFalse();
        }
        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.registerFailedLogin(NOW, 5, LOCK)).isTrue();
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(NOW.plus(LOCK))).isFalse();
    }

    @Test
    void expiredLockStartsAFreshCount() {
        User user = user();
        for (int i = 0; i < 5; i++) {
            user.registerFailedLogin(NOW, 5, LOCK);
        }
        Instant later = NOW.plus(LOCK).plusSeconds(1);

        assertThat(user.registerFailedLogin(later, 5, LOCK)).isFalse();
        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsFailures() {
        User user = user();
        user.registerFailedLogin(NOW, 5, LOCK);
        user.registerFailedLogin(NOW, 5, LOCK);

        user.registerSuccessfulLogin(NOW);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void canBeDisabledAndEnabled() {
        User user = user();
        user.setEnabled(false, NOW.plusSeconds(5));
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getUpdatedAt()).isEqualTo(NOW.plusSeconds(5));
        user.setEnabled(true, NOW);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void staffRoles() {
        assertThat(Role.SERVICE_ADVISOR.isStaff()).isTrue();
        assertThat(Role.MECHANIC.isStaff()).isTrue();
        assertThat(Role.ADMIN.isStaff()).isTrue();
        assertThat(Role.CUSTOMER.isStaff()).isFalse();
    }
}
