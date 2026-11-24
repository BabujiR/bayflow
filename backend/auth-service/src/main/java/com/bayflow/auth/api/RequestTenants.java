package com.bayflow.auth.api;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.security.WorkshopHosts;

/**
 * Decides which workshop an unauthenticated auth request (sign-in, registration, refresh,
 * sign-out) is for: the one whose address it came to, {@code <slug>.<base domain>}. After
 * sign-in the workshop comes from the token instead.
 * <p>
 * An address without a workshop label uses {@code bayflow.auth.fallback-workshop} when set
 * (Swagger UI and scripts on the service's own port, locally); otherwise it is not found.
 */
@Component
public class RequestTenants {

    private final WorkshopHosts hosts;
    private final TenantRepository tenants;
    private final AuthProperties properties;

    public RequestTenants(WorkshopHosts hosts, TenantRepository tenants, AuthProperties properties) {
        this.hosts = hosts;
        this.tenants = tenants;
        this.properties = properties;
    }

    /** The active workshop for sign-in, registration and refresh. */
    public UUID resolve(HttpServletRequest request) {
        Tenant tenant = workshopOf(request);
        if (!tenant.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "workshop-suspended", "Forbidden",
                    "This workshop's account is suspended. Please contact the workshop.");
        }
        return tenant.getId();
    }

    /** The workshop for sign-out, which works even while it is suspended. */
    public UUID resolveAnyStatus(HttpServletRequest request) {
        return workshopOf(request).getId();
    }

    /**
     * The workshop of the request's address, whatever its status. The console (base domain, client
     * header "console") gets the console organization, which no workshop address reaches.
     */
    public Tenant workshopOf(HttpServletRequest request) {
        Optional<String> slug = hosts.slugOf(request);
        if (slug.isEmpty() && Platform.CONSOLE_CLIENT.equals(request.getHeader(CookieRequestGuard.HEADER))) {
            return tenants.findById(Platform.TENANT_ID).orElseThrow(RequestTenants::notFound);
        }
        String wanted = slug.orElse(properties.getFallbackWorkshop());
        if (!StringUtils.hasText(wanted)) {
            throw notFound();
        }
        Tenant tenant = tenants.findBySlug(wanted.trim().toLowerCase()).orElseThrow(RequestTenants::notFound);
        if (Platform.TENANT_ID.equals(tenant.getId())) {
            throw notFound();
        }
        return tenant;
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "workshop-not-found", "Not Found",
                "There is no workshop at this address.");
    }
}
