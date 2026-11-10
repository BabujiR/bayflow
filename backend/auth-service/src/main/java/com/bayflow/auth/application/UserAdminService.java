package com.bayflow.auth.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.RefreshTokenRepository;
import com.bayflow.auth.infrastructure.UserRepository;

/** Account management, used by admins. */
@Service
public class UserAdminService {

    private final AuthService authService;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public UserAdminService(AuthService authService, UserRepository users, RefreshTokenRepository refreshTokens,
            Clock clock) {
        this.authService = authService;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public User createStaff(String username, String email, String password, Role role) {
        if (role == null || !role.isStaff()) {
            throw ApiException.badRequest("invalid-role",
                    "Staff accounts must have role SERVICE_ADVISOR, MECHANIC or ADMIN.");
        }
        return authService.createUser(username, email, password, Set.of(role));
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

    /** Creates the user unless the username already exists (development seed data). */
    @Transactional
    public void ensureUser(String username, String email, String password, Role role) {
        if (!users.existsByUsername(User.normalizeUsername(username))) {
            authService.createUser(username, email, password, Set.of(role));
        }
    }
}
