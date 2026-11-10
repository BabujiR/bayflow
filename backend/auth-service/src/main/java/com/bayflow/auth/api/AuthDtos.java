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

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ClientTokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {
    }

    public record CreateStaffRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role) {
    }

    public record UpdateUserRequest(@NotNull Boolean enabled) {
    }

    public record UserResponse(UUID id, String username, String email, List<String> roles, boolean enabled,
            Instant createdAt) {

        public static UserResponse from(User user) {
            List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), roles, user.isEnabled(),
                    user.getCreatedAt());
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
