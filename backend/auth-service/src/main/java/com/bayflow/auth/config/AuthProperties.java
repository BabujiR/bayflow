package com.bayflow.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code bayflow.auth}. */
@ConfigurationProperties("bayflow.auth")
public class AuthProperties {

    /** "iss" claim of issued tokens. */
    private String issuer = "bayflow-auth";
    /** "aud" claim of issued tokens. */
    private String audience = "bayflow";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private Duration systemTokenTtl = Duration.ofMinutes(10);
    /** How long a temporary password from a staff reset works (it must be replaced at sign-in). */
    private Duration temporaryPasswordTtl = Duration.ofHours(72);

    private final Keys keys = new Keys();
    private final CookieSettings cookie = new CookieSettings();
    private final Login login = new Login();
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    /** The first Ramplight console user (PLATFORM_ADMIN); none while the password is empty. */
    private final BootstrapAdmin platformAdmin = new BootstrapAdmin();
    private final TenantDefaults tenantDefaults = new TenantDefaults();
    private final Signup signup = new Signup();
    private final Mail mail = new Mail();
    private final Demo demo = new Demo();
    /** appointment-service, asked before a location is archived whether it has upcoming bookings. */
    private String appointmentUrl = "http://localhost:8081";
    /**
     * Workshop for sign-in at an address without a workshop label (Swagger UI on the service's
     * own port, API scripts). Empty: such requests get 404 workshop-not-found.
     */
    private String fallbackWorkshop = "";
    /** Service-to-service clients, keyed by client id. */
    private Map<String, Client> clients = new LinkedHashMap<>();

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public Duration getTemporaryPasswordTtl() { return temporaryPasswordTtl; }
    public void setTemporaryPasswordTtl(Duration temporaryPasswordTtl) { this.temporaryPasswordTtl = temporaryPasswordTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public Duration getSystemTokenTtl() { return systemTokenTtl; }
    public void setSystemTokenTtl(Duration systemTokenTtl) { this.systemTokenTtl = systemTokenTtl; }
    public Keys getKeys() { return keys; }
    public CookieSettings getCookie() { return cookie; }
    public Login getLogin() { return login; }
    public BootstrapAdmin getBootstrapAdmin() { return bootstrapAdmin; }
    public BootstrapAdmin getPlatformAdmin() { return platformAdmin; }
    public TenantDefaults getTenantDefaults() { return tenantDefaults; }
    public Signup getSignup() { return signup; }
    public Mail getMail() { return mail; }
    public Demo getDemo() { return demo; }

    /** "Request a demo" on the public site. */
    public static class Demo {
        /** Who hears about new requests (empty = stored only). */
        private String notifyEmail = "";
        private int maxPerHourPerIp = 5;

        public String getNotifyEmail() { return notifyEmail; }
        public void setNotifyEmail(String notifyEmail) { this.notifyEmail = notifyEmail == null ? "" : notifyEmail; }
        public int getMaxPerHourPerIp() { return maxPerHourPerIp; }
        public void setMaxPerHourPerIp(int maxPerHourPerIp) { this.maxPerHourPerIp = maxPerHourPerIp; }
    }
    public String getAppointmentUrl() { return appointmentUrl; }
    public void setAppointmentUrl(String appointmentUrl) { this.appointmentUrl = appointmentUrl; }
    public String getFallbackWorkshop() { return fallbackWorkshop; }
    public void setFallbackWorkshop(String fallbackWorkshop) { this.fallbackWorkshop = fallbackWorkshop; }

    /** Self-service workshop sign-up. */
    public static class Signup {
        /** Sign-ups allowed per client IP per hour. */
        private int maxPerHourPerIp = 10;
        /** How long a setup invite's link works. */
        private Duration inviteTtl = Duration.ofDays(7);

        public int getMaxPerHourPerIp() { return maxPerHourPerIp; }
        public void setMaxPerHourPerIp(int maxPerHourPerIp) { this.maxPerHourPerIp = maxPerHourPerIp; }
        public Duration getInviteTtl() { return inviteTtl; }
        public void setInviteTtl(Duration inviteTtl) { this.inviteTtl = inviteTtl; }
    }

    /** Starting settings of a new workshop; each workshop then changes its own. */
    public static class TenantDefaults {
        private java.math.BigDecimal labourRate = new java.math.BigDecimal("95.00");
        private String currency = "USD";
        private String timeZone = "America/New_York";

        public java.math.BigDecimal getLabourRate() { return labourRate; }
        public void setLabourRate(java.math.BigDecimal labourRate) { this.labourRate = labourRate; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    }
    public Map<String, Client> getClients() { return clients; }
    public void setClients(Map<String, Client> clients) { this.clients = clients; }

    /** RSA signing key. Leave both paths empty in dev to use a throwaway key generated at startup. */
    public static class Keys {
        /** PKCS#8 PEM ("BEGIN PRIVATE KEY"). */
        private String privateKeyPath;
        /** X.509 PEM ("BEGIN PUBLIC KEY"). */
        private String publicKeyPath;
        /** "kid" header; defaults to the key's JWK thumbprint. */
        private String keyId;
        /** Allow a generated key when no paths are configured. Set false in production. */
        private boolean allowEphemeral = true;

        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
        public String getPublicKeyPath() { return publicKeyPath; }
        public void setPublicKeyPath(String publicKeyPath) { this.publicKeyPath = publicKeyPath; }
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public boolean isAllowEphemeral() { return allowEphemeral; }
        public void setAllowEphemeral(boolean allowEphemeral) { this.allowEphemeral = allowEphemeral; }
    }

    /** The refresh-token cookie. */
    public static class CookieSettings {
        private String name = "bayflow_rt";
        private String path = "/api/v1/auth";
        private boolean secure = true;
        private String sameSite = "Strict";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    }

    /**
     * Outgoing e-mail (password reset links) over SMTP. Off until {@code host} is set; then
     * "Forgot password?" appears on the sign-in pages.
     */
    public static class Mail {
        /** SMTP server, e.g. smtp.gmail.com, email-smtp.us-east-1.amazonaws.com, mailpit. Empty = no e-mail. */
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private boolean smtpAuth = true;
        /** STARTTLS on port 587 (required by Gmail and the providers; off for Mailpit). */
        private boolean startTls = true;
        /** Sender address; with Gmail it must be the Gmail account itself. */
        private String from = "";
        /** The public site, e.g. https://getramplight.com; links go to https://<slug>.getramplight.com. */
        private String siteUrl = "http://localhost:3000";
        /** How long a reset link works. */
        private Duration resetLinkTtl = Duration.ofMinutes(60);
        /** Reset e-mails per address per hour, and requests per IP address per hour. */
        private int maxPerEmailPerHour = 3;
        private int maxPerIpPerHour = 10;
        /** Send right after the request commits (tests turn this off and send by hand). */
        private boolean sendImmediately = true;
        /** Retry delivery: every {@code bayflow.auth.mail.interval-ms}, with backoff. */
        private boolean dispatcherEnabled = true;
        private int batchSize = 20;
        private Duration initialBackoff = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(10);
        /** Undelivered after this long: dropped (a reset link would have expired anyway). */
        private Duration giveUpAfter = Duration.ofHours(1);

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isSmtpAuth() { return smtpAuth; }
        public void setSmtpAuth(boolean smtpAuth) { this.smtpAuth = smtpAuth; }
        public boolean isStartTls() { return startTls; }
        public void setStartTls(boolean startTls) { this.startTls = startTls; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getSiteUrl() { return siteUrl; }
        public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
        public Duration getResetLinkTtl() { return resetLinkTtl; }
        public void setResetLinkTtl(Duration resetLinkTtl) { this.resetLinkTtl = resetLinkTtl; }
        public int getMaxPerEmailPerHour() { return maxPerEmailPerHour; }
        public void setMaxPerEmailPerHour(int maxPerEmailPerHour) { this.maxPerEmailPerHour = maxPerEmailPerHour; }
        public int getMaxPerIpPerHour() { return maxPerIpPerHour; }
        public void setMaxPerIpPerHour(int maxPerIpPerHour) { this.maxPerIpPerHour = maxPerIpPerHour; }
        public boolean isSendImmediately() { return sendImmediately; }
        public void setSendImmediately(boolean sendImmediately) { this.sendImmediately = sendImmediately; }
        public boolean isDispatcherEnabled() { return dispatcherEnabled; }
        public void setDispatcherEnabled(boolean dispatcherEnabled) { this.dispatcherEnabled = dispatcherEnabled; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
        public Duration getGiveUpAfter() { return giveUpAfter; }
        public void setGiveUpAfter(Duration giveUpAfter) { this.giveUpAfter = giveUpAfter; }
    }

    /** Brute-force protection. */
    public static class Login {
        /** Failed passwords in a row before the account is locked. */
        private int maxFailedAttempts = 5;
        private Duration lockDuration = Duration.ofMinutes(15);
        /** Login attempts allowed per username + IP per minute. */
        private int maxAttemptsPerMinute = 10;

        public int getMaxFailedAttempts() { return maxFailedAttempts; }
        public void setMaxFailedAttempts(int maxFailedAttempts) { this.maxFailedAttempts = maxFailedAttempts; }
        public Duration getLockDuration() { return lockDuration; }
        public void setLockDuration(Duration lockDuration) { this.lockDuration = lockDuration; }
        public int getMaxAttemptsPerMinute() { return maxAttemptsPerMinute; }
        public void setMaxAttemptsPerMinute(int maxAttemptsPerMinute) { this.maxAttemptsPerMinute = maxAttemptsPerMinute; }
    }

    /**
     * First ADMIN account, created at startup only while no admin exists.
     * Leave the password empty to skip.
     */
    public static class BootstrapAdmin {
        private String username = "admin";
        private String email = "admin@bayflow.local";
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** A service-to-service client. */
    public static class Client {
        private String secret;
        private List<String> roles = new ArrayList<>(List.of("SYSTEM"));

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
