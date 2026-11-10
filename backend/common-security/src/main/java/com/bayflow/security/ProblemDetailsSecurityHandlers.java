package com.bayflow.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes RFC 7807 Problem Details for security failures:
 * 401 when there is no valid token, 403 when the token lacks the required role.
 */
public class ProblemDetailsSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String TYPE_PREFIX = "urn:bayflow:problem:";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Unauthorized",
                "A valid access token is required.", request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to perform this action.", request.getRequestURI());
    }

    private static void write(HttpServletResponse response, int status, String type, String title,
            String detail, String instance) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{"
                + "\"type\":" + json(TYPE_PREFIX + type) + ","
                + "\"title\":" + json(title) + ","
                + "\"status\":" + status + ","
                + "\"detail\":" + json(detail) + ","
                + "\"instance\":" + json(instance)
                + "}";
        response.getWriter().write(body);
    }

    /** Minimal JSON string encoding; inputs are short, server-controlled strings plus the request URI. */
    static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
