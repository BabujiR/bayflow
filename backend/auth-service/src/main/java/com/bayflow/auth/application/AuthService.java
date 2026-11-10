package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.TokenService.IssuedToken;
import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.RefreshToken;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.RefreshTokenRepository;
import com.bayflow.auth.infrastructure.UserRepository;

/** Registration, login, refresh-token rotation and logout. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** A signed-in session: short-lived access token plus the raw refresh token for the cookie. */
    public record Session(IssuedToken accessToken, String refreshToken, Instant refreshExpiresAt, User user,
            Instant issuedAt) {
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginRateLimiter rateLimiter;
    private final AuthProperties properties;
    private final Clock clock;
    /** Compared against when the username is unknown, so response time does not reveal which users exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            TokenService tokenService, LoginRateLimiter rateLimiter, AuthProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("bayflow-timing-equalizer");
    }

    /** Self-registration always creates a CUSTOMER. */
    @Transactional
    public User register(String username, String email, String password) {
        return createUser(username, email, password, Set.of(Role.CUSTOMER));
    }

    @Transactional
    public User createUser(String username, String email, String password, Set<Role> roles) {
        String normalizedUsername = User.normalizeUsername(username);
        String normalizedEmail = User.normalizeEmail(email);
        if (users.existsByUsername(normalizedUsername)) {
            throw ApiException.conflict("username-taken", "That username is already taken.");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("email-taken", "An account with that email already exists.");
        }
        User user = User.create(normalizedUsername, normalizedEmail, passwordEncoder.encode(password), roles,
                clock.instant());
        return users.save(user);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session login(String username, String password, String clientIp) {
        String normalized = User.normalizeUsername(username);
        rateLimiter.check(normalized + "|" + clientIp);
        Instant now = clock.instant();

        Optional<User> found = users.findByUsername(normalized);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw AuthenticationFailedException.invalidCredentials();
        }
        User user = found.get();
        if (user.isLocked(now)) {
            throw AuthenticationFailedException.accountLocked();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            AuthProperties.Login login = properties.getLogin();
            if (user.registerFailedLogin(now, login.getMaxFailedAttempts(), login.getLockDuration())) {
                log.warn("Account {} locked after repeated failed logins", user.getId());
            }
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw AuthenticationFailedException.accountDisabled();
        }
        user.registerSuccessfulLogin(now);
        return startSession(user, now);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
                .orElseThrow(AuthenticationFailedException::invalidRefreshToken);

        if (current.isRevoked()) {
            // A rotated token was presented again: assume it was stolen and end the whole session.
            int revoked = refreshTokens.revokeFamily(current.getFamilyId());
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s)", current.getUserId(), revoked);
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        if (current.isExpired(now)) {
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        User user = users.findById(current.getUserId())
                .orElseThrow(AuthenticationFailedException::invalidRefreshToken);
        if (!user.isEnabled() || user.isLocked(now)) {
            refreshTokens.revokeFamily(current.getFamilyId());
            throw AuthenticationFailedException.invalidRefreshToken();
        }

        String raw = SecureTokens.newToken();
        Instant expiresAt = now.plus(properties.getRefreshTokenTtl());
        refreshTokens.save(current.rotate(SecureTokens.sha256(raw), expiresAt, now));
        return new Session(tokenService.forUser(user), raw, expiresAt, user, now);
    }

    /** Ends the session the refresh token belongs to. Unknown or missing tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        refreshTokens.findByTokenHash(SecureTokens.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId()));
    }

    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private Session startSession(User user, Instant now) {
        String raw = SecureTokens.newToken();
        Instant expiresAt = now.plus(properties.getRefreshTokenTtl());
        refreshTokens.save(RefreshToken.newFamily(user.getId(), SecureTokens.sha256(raw), expiresAt, now));
        return new Session(tokenService.forUser(user), raw, expiresAt, user, now);
    }
}
