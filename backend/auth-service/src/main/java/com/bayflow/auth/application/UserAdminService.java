package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.RefreshTokenRepository;
import com.bayflow.auth.infrastructure.UserRepository;
import com.bayflow.security.CurrentUser;

/** Account management, used by admins (and password resets, by the staff who serve customers). */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    /** A reset's temporary password, shown once to the staff member who asked for it. */
    public record TemporaryPassword(User user, String password, Instant expiresAt) {
    }

    private final AuthService authService;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final UserLocationService userLocations;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public UserAdminService(AuthService authService, UserRepository users, RefreshTokenRepository refreshTokens,
            UserLocationService userLocations, PasswordEncoder passwordEncoder, AuthProperties properties,
            Clock clock) {
        this.authService = authService;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.userLocations = userLocations;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Gives the account a temporary password that must be replaced at the next sign-in, lifts a
     * lockout and ends all of its sessions. An ADMIN may reset anyone else's password; a MANAGER
     * or SERVICE_ADVISOR only a customer's. Nobody resets their own (they change it instead).
     */
    @Transactional
    public TemporaryPassword resetPassword(UUID userId, CurrentUser actor) {
        UUID actorId = UUID.fromString(actor.subject());
        if (userId.equals(actorId)) {
            throw ApiException.conflict("cannot-reset-self",
                    "Use Change password in your account menu to change your own password.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!actor.isAdmin() && !user.getRoles().equals(Set.of(Role.CUSTOMER))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                    "Only an admin can reset a staff member's password.");
        }
        String password = SecureTokens.temporaryPassword();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getTemporaryPasswordTtl());
        user.resetPassword(passwordEncoder.encode(password), expiresAt, now);
        users.saveAndFlush(user);
        refreshTokens.revokeAllForUser(userId);
        log.info("Password of account {} reset by {}", userId, actorId);
        return new TemporaryPassword(user, password, expiresAt);
    }

    /** A staff account with its locations (MANAGER, SERVICE_ADVISOR, MECHANIC) in one transaction. */
    @Transactional
    public User createStaff(String username, String email, String password, Role role, List<UUID> locationIds) {
        if (role == null || !role.isStaff()) {
            throw ApiException.badRequest("invalid-role",
                    "Staff accounts must have role MANAGER, SERVICE_ADVISOR, MECHANIC or ADMIN.");
        }
        User user = authService.createUser(username, email, password, Set.of(role));
        userLocations.assign(user, locationIds);
        return user;
    }

    /** Changes where a staff member works; applies to their next token (within 15 minutes). */
    @Transactional
    public User setLocations(UUID userId, List<UUID> locationIds) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!UserLocationService.isLocationScoped(user)) {
            throw ApiException.badRequest("not-location-scoped",
                    "Admins and customers work with every location; they take no location list.");
        }
        userLocations.assign(user, locationIds);
        return user;
    }

    public LocationAccess accessOf(User user) {
        return userLocations.accessOf(user);
    }

    public java.util.Map<UUID, LocationAccess> accessOf(java.util.Collection<User> list) {
        return userLocations.accessOf(list);
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return users.findAll(pageable);
    }

    /** Enables or disables an account. Disabling also ends all of the user's sessions. */
    @Transactional
    public User setEnabled(UUID userId, boolean enabled, UUID actingUserId) {
        if (!enabled && userId.equals(actingUserId)) {
            throw ApiException.conflict("cannot-disable-self", "You cannot disable your own account.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setEnabled(enabled, clock.instant());
        users.saveAndFlush(user);
        if (!enabled) {
            refreshTokens.revokeAllForUser(userId);
        }
        return user;
    }

    /**
     * Creates the first admin from configuration when the system has no admin yet.
     *
     * @return true if an admin was created
     */
    @Transactional
    public boolean bootstrapAdmin(String username, String email, String password) {
        if (users.existsWithRole(Role.ADMIN)) {
            return false;
        }
        authService.createUser(username, email, password, Set.of(Role.ADMIN));
        return true;
    }

    /** The first console user, when the console has no PLATFORM_ADMIN yet (run in the console's organization). */
    @Transactional
    public boolean bootstrapPlatformAdmin(String username, String email, String password) {
        if (users.existsWithRole(Role.PLATFORM_ADMIN)) {
            return false;
        }
        authService.createUser(username, email, password, Set.of(Role.PLATFORM_ADMIN));
        return true;
    }

    /** Creates the user unless the username already exists (development seed data). */
    @Transactional
    public void ensureUser(String username, String email, String password, Role role) {
        if (!users.existsByUsername(User.normalizeUsername(username))) {
            User user = authService.createUser(username, email, password, Set.of(role));
            if (role.isLocationScoped()) {
                userLocations.assign(user, List.of());
            }
        }
    }
}
