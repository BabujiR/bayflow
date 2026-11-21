package com.bayflow.workshop.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code bayflow.workshop}: how workshop-service reaches auth-service for each
 * workshop's own settings (labour rate, currency). Those are no longer configured here.
 */
@ConfigurationProperties("bayflow.workshop")
public class WorkshopProperties {

    private String authUrl = "http://localhost:8083";
    /** Client-credentials login for SYSTEM tokens. */
    private String clientId = "workshop-service";
    private String clientSecret = "dev-workshop-secret";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    /** How long a workshop's settings are reused before asking auth-service again. */
    private Duration settingsCacheTtl = Duration.ofMinutes(5);
    private final Notifications notifications = new Notifications();

    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getSettingsCacheTtl() { return settingsCacheTtl; }
    public void setSettingsCacheTtl(Duration settingsCacheTtl) { this.settingsCacheTtl = settingsCacheTtl; }
    public Notifications getNotifications() { return notifications; }

    /** Events for notification-service, sent through the outbox (ws_outbox). */
    public static class Notifications {
        /** Off: no events are queued (most tests). */
        private boolean enabled = true;
        private String url = "http://localhost:8084";
        /** Send right after the change commits, not only on the next retry run. */
        private boolean sendImmediately = true;
        /** The scheduled retry of undelivered events. */
        private boolean dispatcherEnabled = true;
        private long intervalMs = 10_000;
        private int batchSize = 20;
        private Duration initialBackoff = Duration.ofSeconds(10);
        private Duration maxBackoff = Duration.ofMinutes(5);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public boolean isSendImmediately() { return sendImmediately; }
        public void setSendImmediately(boolean sendImmediately) { this.sendImmediately = sendImmediately; }
        public boolean isDispatcherEnabled() { return dispatcherEnabled; }
        public void setDispatcherEnabled(boolean dispatcherEnabled) { this.dispatcherEnabled = dispatcherEnabled; }
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
