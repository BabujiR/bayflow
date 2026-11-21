package com.bayflow.notification.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code bayflow.notification}. */
@ConfigurationProperties("bayflow.notification")
public class NotificationProperties {

    private String authUrl = "http://localhost:8083";
    /** Client-credentials login for SYSTEM tokens. */
    private String clientId = "notification-service";
    private String clientSecret = "dev-notification-secret";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    /** How long "who works at this location" is reused before asking auth-service again. */
    private Duration staffCacheTtl = Duration.ofMinutes(1);
    /** How long an organization's locations (names, time zones) are reused. */
    private Duration locationsCacheTtl = Duration.ofMinutes(5);
    /** Time zone for times in notifications when a location's is unknown. */
    private ZoneId fallbackZone = ZoneId.of("America/New_York");
    private final Retention retention = new Retention();

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
    public Duration getStaffCacheTtl() { return staffCacheTtl; }
    public void setStaffCacheTtl(Duration staffCacheTtl) { this.staffCacheTtl = staffCacheTtl; }
    public Duration getLocationsCacheTtl() { return locationsCacheTtl; }
    public void setLocationsCacheTtl(Duration locationsCacheTtl) { this.locationsCacheTtl = locationsCacheTtl; }
    public ZoneId getFallbackZone() { return fallbackZone; }
    public void setFallbackZone(ZoneId fallbackZone) { this.fallbackZone = fallbackZone; }
    public Retention getRetention() { return retention; }

    /** Old notifications are deleted every night. */
    public static class Retention {
        private boolean enabled = true;
        private Duration keep = Duration.ofDays(90);
        private String cron = "0 17 3 * * *";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getKeep() { return keep; }
        public void setKeep(Duration keep) { this.keep = keep; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}
