package com.bayflow.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.bayflow.auth.application.LocationAccess;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;

/** Request and response bodies of the auth API. */
public final class AuthDtos {

    private AuthDtos() {
    }

    static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]+$";
    static final String USERNAME_MESSAGE = "may only contain letters, digits, '.', '_' and '-'";

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    /** {@code username} may also be the account's e-mail address. */
    public record LoginRequest(@NotBlank @Size(max = 254) String username, @NotBlank String password) {
    }

    /** The signed-in user's new password; {@code currentPassword} is the temporary one after a reset. */
    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    /** "Forgot password?": the answer is the same whether or not the address has an account. */
    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** The token from the e-mailed link and the new password. */
    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    /** Shown once to the staff member who reset the password; never stored in plain text. */
    public record TemporaryPasswordResponse(UUID userId, String username, String temporaryPassword,
            Instant expiresAt) {
    }

    public record ClientTokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {
    }

    public record CreateStaffRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role,
            /* For MANAGER, SERVICE_ADVISOR and MECHANIC; none = the organization's only location. */
            @Size(max = 200) List<UUID> locationIds) {
    }

    /** The locations a MANAGER, SERVICE_ADVISOR or MECHANIC works at (at least one). */
    public record UpdateLocationsRequest(@NotNull @Size(min = 1, max = 200) List<@NotNull UUID> locationIds) {
    }

    public record UpdateUserRequest(@NotNull Boolean enabled) {
    }

    /**
     * An account. {@code allLocations}: works with every location of the organization (ADMIN,
     * CUSTOMER); otherwise {@code locationIds} lists where they work. {@code mustChangePassword}:
     * signed in with a temporary password from a reset, which they must replace first.
     */
    public record UserResponse(UUID id, String username, String email, List<String> roles, boolean enabled,
            Instant createdAt, boolean allLocations, List<UUID> locationIds, boolean mustChangePassword) {

        public static UserResponse from(User user, LocationAccess access) {
            List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), roles, user.isEnabled(),
                    user.getCreatedAt(), access.all(), access.locationIds(), user.isMustChangePassword());
        }
    }

    /** Access token response. {@code user} is omitted for service-client tokens. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt,
            UserResponse user) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
