package com.bayflow.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    @Test
    void rotateRevokesTheOldTokenAndKeepsTheFamily() {
        UUID userId = UUID.randomUUID();
        RefreshToken first = RefreshToken.newFamily(userId, "hash-1", NOW.plusSeconds(60), NOW);

        RefreshToken second = first.rotate("hash-2", NOW.plusSeconds(120), NOW.plusSeconds(10));

        assertThat(first.isRevoked()).isTrue();
        assertThat(first.getReplacedBy()).isEqualTo(second.getId());
        assertThat(second.isRevoked()).isFalse();
        assertThat(second.getFamilyId()).isEqualTo(first.getFamilyId());
        assertThat(second.getUserId()).isEqualTo(userId);
        assertThat(second.getTokenHash()).isEqualTo("hash-2");
    }

    @Test
    void revokedTokenCannotBeRotated() {
        RefreshToken token = RefreshToken.newFamily(UUID.randomUUID(), "h", NOW.plusSeconds(60), NOW);
        token.revoke();

        assertThatThrownBy(() -> token.rotate("h2", NOW.plusSeconds(60), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiresAtItsExpiryInstant() {
        RefreshToken token = RefreshToken.newFamily(UUID.randomUUID(), "h", NOW.plusSeconds(60), NOW);

        assertThat(token.isExpired(NOW.plusSeconds(59))).isFalse();
        assertThat(token.isExpired(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void serviceClientParsesRoles() {
        ServiceClient client = new ServiceClient("svc", "hash", java.util.List.of("SYSTEM", " OTHER "), NOW);
        assertThat(client.getRoleList()).containsExactly("SYSTEM", "OTHER");
        assertThat(client.isEnabled()).isTrue();

        client.update("hash2", java.util.List.of("SYSTEM"));
        assertThat(client.getSecretHash()).isEqualTo("hash2");
        assertThat(client.getRoleList()).containsExactly("SYSTEM");
    }
}
