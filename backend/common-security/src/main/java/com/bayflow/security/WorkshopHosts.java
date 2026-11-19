package com.bayflow.security;

import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/**
 * Reads the workshop from the address a request came to: {@code <slug>.<base domain>}, e.g.
 * {@code acme.localhost} with base domain {@code localhost}. The web app's nginx (and the Vite
 * dev server) pass the browser's host in {@code X-Forwarded-Host}; direct calls use {@code Host}.
 * <p>
 * Addresses without a workshop label (the bare base domain, {@code localhost:8081},
 * {@code auth-service:8083} between containers) give no slug.
 */
public class WorkshopHosts {

    private final String baseDomain;

    public WorkshopHosts(String baseDomain) {
        this.baseDomain = normalize(baseDomain == null ? "localhost" : baseDomain);
    }

    public String getBaseDomain() {
        return baseDomain;
    }

    /** The workshop slug of the request's address, lower-case; empty when it names none. */
    public Optional<String> slugOf(HttpServletRequest request) {
        String host = request.getHeader("X-Forwarded-Host");
        if (!StringUtils.hasText(host)) {
            host = request.getHeader("Host");
        }
        return slugOf(host);
    }

    /** As above, for a host value such as {@code acme.localhost:3000}. */
    public Optional<String> slugOf(String host) {
        if (!StringUtils.hasText(host)) {
            return Optional.empty();
        }
        // X-Forwarded-Host may list several hosts; the first is the browser's.
        String first = host.split(",")[0];
        String name = normalize(stripPort(first));
        String suffix = "." + baseDomain;
        if (!name.endsWith(suffix)) {
            return Optional.empty();
        }
        String label = name.substring(0, name.length() - suffix.length());
        // Only one label: "a.b.localhost" is not a workshop address. "www" is the base domain.
        if (label.isEmpty() || label.contains(".") || label.equals("www")) {
            return Optional.empty();
        }
        return Optional.of(label);
    }

    private static String stripPort(String host) {
        String h = host.trim();
        if (h.startsWith("[")) {
            return h; // IPv6 literal: never a workshop address
        }
        int colon = h.lastIndexOf(':');
        return colon >= 0 ? h.substring(0, colon) : h;
    }

    private static String normalize(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.endsWith(".") ? v.substring(0, v.length() - 1) : v;
    }
}
