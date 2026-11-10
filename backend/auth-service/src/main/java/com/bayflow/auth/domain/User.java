package com.bayflow.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "auth_users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "auth_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected User() {
        // for JPA
    }

    private User(String username, String email, String passwordHash, Set<Role> roles, Instant now) {
        this.id = UUID.randomUUID();
        this.username = normalizeUsername(username);
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.roles = EnumSet.copyOf(roles);
        this.enabled = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User create(String username, String email, String passwordHash, Set<Role> roles, Instant now) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("A user needs at least one role");
        }
        return new User(username, email, passwordHash, roles, now);
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ---- login protection -------------------------------------------------

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a wrong password. After {@code maxFailures} in a row the account is locked
     * for {@code lockDuration}; an expired lock starts a fresh count.
     *
     * @return true if this failure locked the account
     */
    public boolean registerFailedLogin(Instant now, int maxFailures, Duration lockDuration) {
        if (lockedUntil != null && !isLocked(now)) {
            lockedUntil = null;
            failedAttempts = 0;
        }
        failedAttempts++;
        updatedAt = now;
        if (failedAttempts >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        if (failedAttempts != 0 || lockedUntil != null) {
            failedAttempts = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    // ---- getters ----------------------------------------------------------

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
