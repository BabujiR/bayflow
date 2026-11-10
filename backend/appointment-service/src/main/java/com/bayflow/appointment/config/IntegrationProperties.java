package com.bayflow.appointment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** How appointment-service reaches auth-service and workshop-service ({@code bayflow.integration}). */
@ConfigurationProperties("bayflow.integration")
public class IntegrationProperties {

    private String authUrl = "http://localhost:8083";
    private String workshopUrl = "http://localhost:8082";
    /** Client-credentials login for SYSTEM tokens. */
    private String clientId = "appointment-service";
    private String clientSecret = "dev-system-secret";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private final Dispatcher dispatcher = new Dispatcher();

    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }
    public String getWorkshopUrl() { return workshopUrl; }
    public void setWorkshopUrl(String workshopUrl) { this.workshopUrl = workshopUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Dispatcher getDispatcher() { return dispatcher; }

    /** The scheduled retry of undelivered outbox messages. */
    public static class Dispatcher {
        private boolean enabled = true;
        /** Pause between retry runs, in milliseconds. */
        private long intervalMs = 10_000;
        private int batchSize = 20;
        /** First retry delay; doubles on each failure up to {@code maxBackoff}. */
        private Duration initialBackoff = Duration.ofSeconds(10);
        private Duration maxBackoff = Duration.ofMinutes(5);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    }
}
