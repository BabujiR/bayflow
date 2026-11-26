package com.bayflow.auth.application;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.infrastructure.RefreshTokenRepository;
import com.bayflow.auth.infrastructure.UserRepository;

/**
 * A customer's online account, as the shop sees it from the customer's page: its status, and
 * enabling, disabling and unlocking it. Only customers' accounts: never staff (those are on the
 * Team page, for admins), so an advisor can't touch a colleague's account through here.
 */
@Service
public class CustomerAccountService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountService.class);

    /** At most this many accounts per status request (one page of customers). */
    public static final int MAX_IDS = 100;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public CustomerAccountService(UserRepository users, RefreshTokenRepository refreshTokens, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).filter(CustomerAccountService::isCustomer)
                .orElseThrow(() -> ApiException.notFound("Customer account"));
    }

    /** The customer accounts among {@code ids} (others are left out). */
    @Transactional(readOnly = true)
    public List<User> getAll(Collection<UUID> ids) {
        if (ids.size() > MAX_IDS) {
            throw ApiException.badRequest("too-many-ids", "Ask for at most " + MAX_IDS + " accounts at a time.");
        }
        return users.findAllById(ids).stream().filter(CustomerAccountService::isCustomer).toList();
    }

    /** Disabling also signs them out everywhere. */
    @Transactional
    public User setEnabled(UUID userId, boolean enabled, UUID actingUserId) {
        User user = get(userId);
        user.setEnabled(enabled, clock.instant());
        users.saveAndFlush(user);
        if (!enabled) {
            refreshTokens.revokeAllForUser(userId);
        }
        log.info("Customer account {} {} by {}", userId, enabled ? "enabled" : "disabled", actingUserId);
        return user;
    }

    @Transactional
    public User unlock(UUID userId, UUID actingUserId) {
        User user = get(userId);
        user.unlock(clock.instant());
        users.saveAndFlush(user);
        log.info("Customer account {} unlocked by {}", userId, actingUserId);
        return user;
    }

    private static boolean isCustomer(User user) {
        return user.getRoles().equals(Set.of(Role.CUSTOMER));
    }
}
