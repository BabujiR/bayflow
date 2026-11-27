package com.bayflow.auth.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.CustomerAccountService;
import com.bayflow.auth.domain.User;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Customers' online accounts, for the shop's staff on a customer's page (customers' accounts
 * only; 404 for anyone else's). Advisors see them, reset passwords (password-reset) and unlock;
 * managers and admins also disable and enable them.
 */
@RestController
@RequestMapping("/api/v1/auth/customer-accounts")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer accounts")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SERVICE_ADVISOR')")
public class CustomerAccountController {

    /** {@code locked}: too many failed sign-ins, until {@code lockedUntil} (or until unlocked). */
    public record CustomerAccount(UUID id, String username, String email, boolean enabled, boolean locked,
            Instant lockedUntil, boolean mustChangePassword, Instant createdAt) {

        static CustomerAccount from(User user, Instant now) {
            boolean locked = user.isLocked(now);
            return new CustomerAccount(user.getId(), user.getUsername(), user.getEmail(), user.isEnabled(), locked,
                    locked ? user.getLockedUntil() : null, user.isMustChangePassword(), user.getCreatedAt());
        }
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    private final CustomerAccountService accounts;
    private final Clock clock;

    public CustomerAccountController(CustomerAccountService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @GetMapping("/{userId}")
    @Operation(summary = "A customer's online account: status for their page")
    public CustomerAccount get(@PathVariable UUID userId) {
        return CustomerAccount.from(accounts.get(userId), clock.instant());
    }

    @GetMapping
    @Operation(summary = "Several customers' account status at once (ids=…, at most 100): the customer list")
    public List<CustomerAccount> statuses(@RequestParam List<UUID> ids) {
        return accounts.getAll(ids).stream().map(u -> CustomerAccount.from(u, clock.instant())).toList();
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Disable or enable a customer's sign-in (MANAGER, ADMIN); disabling signs them out")
    public CustomerAccount setEnabled(@PathVariable UUID userId, @Valid @RequestBody EnabledRequest request,
            Authentication auth) {
        return CustomerAccount.from(accounts.setEnabled(userId, request.enabled(), actor(auth)), clock.instant());
    }

    @PostMapping("/{userId}/unlock")
    @Operation(summary = "Unlock a customer locked out after too many failed sign-ins")
    public CustomerAccount unlock(@PathVariable UUID userId, Authentication auth) {
        return CustomerAccount.from(accounts.unlock(userId, actor(auth)), clock.instant());
    }

    private static UUID actor(Authentication auth) {
        return UUID.fromString(CurrentUser.from(auth).subject());
    }
}
