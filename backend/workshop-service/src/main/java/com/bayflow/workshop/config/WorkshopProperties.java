package com.bayflow.workshop.config;

import java.time.Duration;
import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code bayflow.workshop}: how workshop-service reaches auth-service for each
 * workshop's own settings (labour rate, currency). Those are no longer configured here.
 */
@ConfigurationProperties("bayflow.workshop")
public class WorkshopProperties {

    private String authUrl = "http://localhost:8083";
    /** appointment-service: who to bill for a job (invoices). */
    private String appointmentUrl = "http://localhost:8081";
    /** The web app (BAYFLOW_SITE_URL): each workshop is https://<slug>.<host>, for links in e-mails. */
    private String siteUrl = "http://localhost:3000";
    /** Client-credentials login for SYSTEM tokens. */
    private String clientId = "workshop-service";
    private String clientSecret = "dev-workshop-secret";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    /** How long a workshop's settings are reused before asking auth-service again. */
    private Duration settingsCacheTtl = Duration.ofMinutes(5);
    private final Notifications notifications = new Notifications();
    private final QuickBooks quickbooks = new QuickBooks();
    private final Estimates estimates = new Estimates();
    private final Reviews reviews = new Reviews();

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getAppointmentUrl() { return appointmentUrl; }
    public void setAppointmentUrl(String appointmentUrl) { this.appointmentUrl = appointmentUrl; }
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
    public QuickBooks getQuickbooks() { return quickbooks; }
    public Estimates getEstimates() { return estimates; }
    public Reviews getReviews() { return reviews; }

    /**
     * QuickBooks Online (Intuit app keys). Off until client id, secret and token key are set. One
     * redirect URI for every workshop, on the main domain: https://<domain>/api/v1/quickbooks/callback.
     */
    public static class QuickBooks {
        private String clientId = "";
        private String clientSecret = "";
        /** sandbox (development keys) or production. */
        private String environment = "sandbox";
        private String redirectUri = "http://localhost:3000/api/v1/quickbooks/callback";
        /** Base64 of 32 random bytes: encrypts the tokens in the database (AES-GCM). */
        private String tokenKey = "";
        private String authorizeUrl = "https://appcenter.intuit.com/connect/oauth2";
        private String tokenUrl = "https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer";
        private String revokeUrl = "https://developer.api.intuit.com/v2/oauth2/tokens/revoke";
        /** Empty: the sandbox or production API, by environment. */
        private String apiUrl = "";
        private int minorVersion = 75;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);

        public boolean isConfigured() {
            return !clientId.isBlank() && !clientSecret.isBlank() && !tokenKey.isBlank();
        }

        public boolean isProduction() {
            return "production".equalsIgnoreCase(environment);
        }

        public String apiBaseUrl() {
            if (!apiUrl.isBlank()) {
                return apiUrl;
            }
            return isProduction() ? "https://quickbooks.api.intuit.com" : "https://sandbox-quickbooks.api.intuit.com";
        }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId.strip(); }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret == null ? "" : clientSecret.strip(); }
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
        public String getTokenKey() { return tokenKey; }
        public void setTokenKey(String tokenKey) { this.tokenKey = tokenKey == null ? "" : tokenKey.strip(); }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getRevokeUrl() { return revokeUrl; }
        public void setRevokeUrl(String revokeUrl) { this.revokeUrl = revokeUrl; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl == null ? "" : apiUrl.strip(); }
        public int getMinorVersion() { return minorVersion; }
        public void setMinorVersion(int minorVersion) { this.minorVersion = minorVersion; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    /** Estimate approval links e-mailed to customers (approve, decline and sign without an account). */
    public static class Estimates {
        /** E-mail the link as soon as an estimate is submitted for approval (to the customer's address). */
        private boolean sendOnSubmit = true;
        /** How long a link works. */
        private Duration linkLifetime = Duration.ofDays(14);
        /** A reminder this long after the link (and after each reminder), while nothing is decided. */
        private Duration remindEvery = Duration.ofDays(1);
        private int maxReminders = 2;
        /** The scheduled reminder run (off in tests, which call it themselves). */
        private boolean remindersEnabled = true;
        private long reminderIntervalMs = 15 * 60 * 1000L;

        public boolean isSendOnSubmit() { return sendOnSubmit; }
        public void setSendOnSubmit(boolean sendOnSubmit) { this.sendOnSubmit = sendOnSubmit; }
        public Duration getLinkLifetime() { return linkLifetime; }
        public void setLinkLifetime(Duration linkLifetime) { this.linkLifetime = linkLifetime; }
        public Duration getRemindEvery() { return remindEvery; }
        public void setRemindEvery(Duration remindEvery) { this.remindEvery = remindEvery; }
        public int getMaxReminders() { return maxReminders; }
        public void setMaxReminders(int maxReminders) { this.maxReminders = maxReminders; }
        public boolean isRemindersEnabled() { return remindersEnabled; }
        public void setRemindersEnabled(boolean remindersEnabled) { this.remindersEnabled = remindersEnabled; }
        public long getReminderIntervalMs() { return reminderIntervalMs; }
        public void setReminderIntervalMs(long reminderIntervalMs) { this.reminderIntervalMs = reminderIntervalMs; }
    }

    /** Google review requests (each location turns them on and sets its link in auth-service). */
    public static class Reviews {
        /** At most one request per customer (e-mail address) in this long, across locations. */
        private Duration askEvery = Duration.ofDays(90);
        /** The local time "next morning" means. */
        private LocalTime morning = LocalTime.of(9, 0);
        /** Tries before a request that can't be sent is given up. */
        private int maxAttempts = 5;
        /** The scheduled sending run (off in tests, which call it themselves). */
        private boolean senderEnabled = true;
        private long senderIntervalMs = 15 * 60 * 1000L;

        public Duration getAskEvery() { return askEvery; }
        public void setAskEvery(Duration askEvery) { this.askEvery = askEvery; }
        public LocalTime getMorning() { return morning; }
        public void setMorning(LocalTime morning) { this.morning = morning; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public boolean isSenderEnabled() { return senderEnabled; }
        public void setSenderEnabled(boolean senderEnabled) { this.senderEnabled = senderEnabled; }
        public long getSenderIntervalMs() { return senderIntervalMs; }
        public void setSenderIntervalMs(long senderIntervalMs) { this.senderIntervalMs = senderIntervalMs; }
    }

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
        /**
         * Failures after which a message is given up (about 4 hours with the default backoff) and
         * logged as an error for an operator; it stays in the outbox with its last error.
         */
        private int maxAttempts = 50;

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
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }
}
