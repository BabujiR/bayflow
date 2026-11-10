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

    private final Keys keys = new Keys();
    private final CookieSettings cookie = new CookieSettings();
    private final Login login = new Login();
    /** Service-to-service clients, keyed by client id. */
    private Map<String, Client> clients = new LinkedHashMap<>();

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public Duration getSystemTokenTtl() { return systemTokenTtl; }
    public void setSystemTokenTtl(Duration systemTokenTtl) { this.systemTokenTtl = systemTokenTtl; }
    public Keys getKeys() { return keys; }
    public CookieSettings getCookie() { return cookie; }
    public Login getLogin() { return login; }
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
