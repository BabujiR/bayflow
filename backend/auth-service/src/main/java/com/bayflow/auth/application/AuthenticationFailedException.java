package com.bayflow.auth.application;

import org.springframework.http.HttpStatus;

/**
 * Login, refresh or client authentication failed. Transactions do not roll back on this
 * exception, so failure counters and token revocations are still saved.
 */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String type, String detail) {
        super(HttpStatus.UNAUTHORIZED, type, "Unauthorized", detail);
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("invalid-credentials", "Username or password is incorrect.");
    }

    public static AuthenticationFailedException accountLocked() {
        return new AuthenticationFailedException("account-locked",
                "Too many failed attempts. The account is temporarily locked; try again later.");
    }

    public static AuthenticationFailedException accountDisabled() {
        return new AuthenticationFailedException("account-disabled", "This account has been disabled.");
    }

    public static AuthenticationFailedException invalidRefreshToken() {
        return new AuthenticationFailedException("invalid-refresh-token",
                "The session has expired or is no longer valid. Please sign in again.");
    }

    public static AuthenticationFailedException invalidClient() {
        return new AuthenticationFailedException("invalid-client", "Client authentication failed.");
    }
}
