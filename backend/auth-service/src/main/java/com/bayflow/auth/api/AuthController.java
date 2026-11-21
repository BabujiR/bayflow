package com.bayflow.auth.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.ChangePasswordRequest;
import com.bayflow.auth.api.AuthDtos.ClientTokenRequest;
import com.bayflow.auth.api.AuthDtos.ForgotPasswordRequest;
import com.bayflow.auth.api.AuthDtos.LoginRequest;
import com.bayflow.auth.api.AuthDtos.RegisterRequest;
import com.bayflow.auth.api.AuthDtos.ResetPasswordRequest;
import com.bayflow.auth.api.AuthDtos.TokenResponse;
import com.bayflow.auth.api.AuthDtos.UserResponse;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.application.AuthService.Session;
import com.bayflow.auth.application.ClientCredentialsService;
import com.bayflow.auth.application.LocationAccess;
import com.bayflow.auth.application.PasswordResetService;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.application.TokenService.IssuedToken;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final String BEARER = "Bearer";

    private final AuthService authService;
    private final ClientCredentialsService clientCredentials;
    private final RefreshCookies cookies;
    private final RequestTenants tenants;
    private final PasswordResetService passwordResets;

    public AuthController(AuthService authService, ClientCredentialsService clientCredentials,
            RefreshCookies cookies, RequestTenants tenants, PasswordResetService passwordResets) {
        this.passwordResets = passwordResets;
        this.authService = authService;
        this.clientCredentials = clientCredentials;
        this.cookies = cookies;
        this.tenants = tenants;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer account in this workshop")
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        // Customers work with every location of the organization.
        return TenantContext.call(tenants.resolve(httpRequest), () -> UserResponse.from(
                authService.register(request.username(), request.email(), request.password()), LocationAccess.ALL));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with username or e-mail; returns an access token and sets the refresh-token cookie")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        Session session = TenantContext.call(tenants.resolve(httpRequest),
                () -> authService.login(request.username(), request.password(), httpRequest.getRemoteAddr()));
        return sessionResponse(session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh-token cookie and return a new access token")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest httpRequest) {
        CookieRequestGuard.check(httpRequest);
        String refreshToken = cookies.read(httpRequest);
        return sessionResponse(TenantContext.call(tenants.resolve(httpRequest),
                () -> authService.refresh(refreshToken)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session and clear the refresh-token cookie")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        CookieRequestGuard.check(httpRequest);
        String refreshToken = cookies.read(httpRequest);
        TenantContext.run(tenants.resolveAnyStatus(httpRequest), () -> authService.logout(refreshToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Forgot password: e-mail a reset link to this address, if it has an account",
            description = "Always 202, whether or not the address has an account. 503 when e-mail is not set up; "
                    + "429 after too many requests from one IP address.")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        TenantContext.run(tenants.resolve(httpRequest),
                () -> passwordResets.requestReset(request.email(), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password with the token from the reset e-mail; signs in",
            description = "The link works once, for 60 minutes. Ends every other session. "
                    + "400 reset-link-invalid when it expired or was used.")
    public ResponseEntity<TokenResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        return sessionResponse(TenantContext.call(tenants.resolve(httpRequest),
                () -> passwordResets.resetPassword(request.token(), request.newPassword(),
                        httpRequest.getRemoteAddr())));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change your own password (also replaces a temporary one after a reset)",
            description = "Ends your other sessions; returns a new access token and refresh-token cookie.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        UUID userId = userIdOf(authentication);
        return sessionResponse(authService.changePassword(userId, request.currentPassword(),
                request.newPassword(), httpRequest.getRemoteAddr()));
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(Authentication authentication) {
        User user = authService.getUser(userIdOf(authentication));
        return UserResponse.from(user, authService.accessOf(user));
    }

    private static UUID userIdOf(Authentication authentication) {
        try {
            return UUID.fromString(CurrentUser.from(authentication).subject());
        } catch (IllegalArgumentException e) {
            // SYSTEM tokens carry a client id, not a user id
            throw ApiException.notFound("User");
        }
    }

    @PostMapping("/token")
    @Operation(summary = "Client-credentials grant for service-to-service calls (role SYSTEM)")
    public TokenResponse clientToken(@Valid @RequestBody ClientTokenRequest request) {
        // Service clients are platform-level, not part of any workshop.
        IssuedToken token = TenantContext.callAcrossTenants(
                () -> clientCredentials.issue(request.clientId(), request.clientSecret()));
        return new TokenResponse(token.value(), BEARER, token.expiresInSeconds(), token.expiresAt(), null);
    }

    private ResponseEntity<TokenResponse> sessionResponse(Session session) {
        IssuedToken access = session.accessToken();
        TokenResponse body = new TokenResponse(access.value(), BEARER, access.expiresInSeconds(),
                access.expiresAt(), UserResponse.from(session.user(), session.access()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.refreshExpiresAt(),
                        session.issuedAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
