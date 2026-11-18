package com.bayflow.auth.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.bayflow.security.Tenants;

/**
 * Decides which workshop an unauthenticated auth request (sign-in, registration, refresh,
 * sign-out) is for. After sign-in the workshop comes from the token instead.
 * <p>
 * Phase 1 of multi-tenancy: there is one workshop, so every request is for the default one.
 * Phase 4 resolves it from the address ({@code <slug>.<base domain>}).
 */
@Component
public class RequestTenants {

    public UUID resolve(HttpServletRequest request) {
        return Tenants.DEFAULT_TENANT_ID;
    }
}
