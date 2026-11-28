package com.bayflow.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SYSTEM access tokens from auth-service (client-credentials grant), kept until shortly before
 * they expire. Callers that need a new one at the same moment wait for one request, not one each.
 */
public class SystemTokens {

    /** Fetch a new token this long before the kept one expires. */
    public static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);
    /** How long a token without an expiry is kept. */
    static final Duration DEFAULT_LIFETIME = Duration.ofSeconds(120);

    record TokenRequest(String clientId, String clientSecret) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, Instant expiresAt) {
    }

    private final RestClient auth;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    /** {@code auth}: a client for auth-service (see {@link ServiceHttp#internal}). */
    public SystemTokens(RestClient auth, String clientId, String clientSecret, Clock clock) {
        this.auth = auth;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    /** A valid token: the kept one, or a new one from auth-service. */
    public String token() {
        lock.lock();
        try {
            if (token != null && clock.instant().isBefore(expiresAt.minus(REFRESH_MARGIN))) {
                return token;
            }
            TokenResponse response = auth.post()
                    .uri("/api/v1/auth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TokenRequest(clientId, clientSecret))
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("auth-service returned no access token");
            }
            token = response.accessToken();
            expiresAt = response.expiresAt() != null ? response.expiresAt() : clock.instant().plus(DEFAULT_LIFETIME);
            return token;
        } finally {
            lock.unlock();
        }
    }

    /** Forget the kept token (after a 401: auth-service may have restarted with a new key). */
    public void invalidate() {
        lock.lock();
        try {
            token = null;
            expiresAt = Instant.EPOCH;
        } finally {
            lock.unlock();
        }
    }
}
