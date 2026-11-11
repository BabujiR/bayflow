package com.bayflow.auth.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.ApiException;

/**
 * CSRF defence for the endpoints the browser authenticates with the refresh-token cookie
 * ({@code /refresh} and {@code /logout}). Everything else in Bayflow is authenticated by a
 * bearer token in the {@code Authorization} header, which browsers never attach on their own,
 * so it cannot be forged cross-site.
 * <p>
 * Layers, following the OWASP CSRF cheat sheet:
 * <ol>
 *   <li>The cookie is {@code SameSite=Strict}, {@code HttpOnly} and scoped to {@code /api/v1/auth}.</li>
 *   <li>The request must carry the custom {@value #HEADER} header. A cross-site page can only
 *       add custom headers through CORS, and Bayflow allows no cross-origin requests, so the
 *       browser blocks it at the preflight.</li>
 *   <li>A browser that reports {@code Sec-Fetch-Site: cross-site} is refused outright.</li>
 * </ol>
 * This replaces Spring's synchronizer-token CSRF, which needs a server-side or cookie token the
 * SPA would have to fetch before its very first call ({@code /refresh} on page load).
 */
public final class CookieRequestGuard {

    /** Sent by the Bayflow web app on every request. */
    public static final String HEADER = "X-Bayflow-Client";

    private CookieRequestGuard() {
    }

    public static void check(HttpServletRequest request) {
        boolean crossSite = "cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"));
        if (crossSite || !StringUtils.hasText(request.getHeader(HEADER))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "csrf-check-failed", "Forbidden",
                    "This request must come from the Bayflow app.");
        }
    }
}
