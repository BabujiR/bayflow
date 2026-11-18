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
}
