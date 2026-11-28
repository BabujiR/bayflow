package com.bayflow.workshop.integration;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.bayflow.http.SystemTokens;
import com.bayflow.workshop.config.WorkshopProperties;

/** SYSTEM access tokens from auth-service (client credentials), kept until shortly before they expire. */
@Component
public class SystemTokenProvider {

    private final SystemTokens tokens;

    public SystemTokenProvider(WorkshopProperties properties, Clock clock) {
        this.tokens = new SystemTokens(HttpClients.create(properties.getAuthUrl(), properties),
                properties.getClientId(), properties.getClientSecret(), clock);
    }

    public String token() {
        return tokens.token();
    }

    /** Forget the kept token (e.g. after a 401 because auth-service restarted with a new key). */
    public void invalidate() {
        tokens.invalidate();
    }
}
