package com.bayflow.auth.api;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.bayflow.auth.config.AuthProperties;

/** Builds and reads the HttpOnly refresh-token cookie. The token never appears in a response body. */
@Component
public class RefreshCookies {

    private final AuthProperties.CookieSettings settings;

    public RefreshCookies(AuthProperties properties) {
        this.settings = properties.getCookie();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (settings.getName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public ResponseCookie issue(String value, Instant expiresAt, Instant now) {
        return base(value).maxAge(Duration.between(now, expiresAt)).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(settings.getName(), value)
                .httpOnly(true)
                .secure(settings.isSecure())
                .sameSite(settings.getSameSite())
                .path(settings.getPath());
    }
}
