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

import com.bayflow.auth.api.AuthDtos.ClientTokenRequest;
import com.bayflow.auth.api.AuthDtos.LoginRequest;
import com.bayflow.auth.api.AuthDtos.RegisterRequest;
import com.bayflow.auth.api.AuthDtos.TokenResponse;
import com.bayflow.auth.api.AuthDtos.UserResponse;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.application.AuthService.Session;
import com.bayflow.auth.application.ClientCredentialsService;
import com.bayflow.auth.application.TokenService.IssuedToken;
import com.bayflow.security.CurrentUser;

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

    public AuthController(AuthService authService, ClientCredentialsService clientCredentials,
            RefreshCookies cookies) {
        this.authService = authService;
        this.clientCredentials = clientCredentials;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer account")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request.username(), request.email(), request.password()));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in; returns an access token and sets the refresh-token cookie")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        Session session = authService.login(request.username(), request.password(), httpRequest.getRemoteAddr());
        return sessionResponse(session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh-token cookie and return a new access token")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest httpRequest) {
        return sessionResponse(authService.refresh(cookies.read(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session and clear the refresh-token cookie")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(cookies.read(httpRequest));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(Authentication authentication) {
        CurrentUser current = CurrentUser.from(authentication);
        UUID userId;
        try {
            userId = UUID.fromString(current.subject());
        } catch (IllegalArgumentException e) {
            // SYSTEM tokens carry a client id, not a user id
            throw ApiException.notFound("User");
        }
        return UserResponse.from(authService.getUser(userId));
    }

    @PostMapping("/token")
    @Operation(summary = "Client-credentials grant for service-to-service calls (role SYSTEM)")
    public TokenResponse clientToken(@Valid @RequestBody ClientTokenRequest request) {
        IssuedToken token = clientCredentials.issue(request.clientId(), request.clientSecret());
        return new TokenResponse(token.value(), BEARER, token.expiresInSeconds(), token.expiresAt(), null);
    }

    private ResponseEntity<TokenResponse> sessionResponse(Session session) {
        IssuedToken access = session.accessToken();
        TokenResponse body = new TokenResponse(access.value(), BEARER, access.expiresInSeconds(),
                access.expiresAt(), UserResponse.from(session.user()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.refreshExpiresAt(),
                        session.issuedAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
