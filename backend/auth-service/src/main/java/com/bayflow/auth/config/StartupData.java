package com.bayflow.auth.config;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.ClientCredentialsService;
import com.bayflow.auth.application.LocationService;
import com.bayflow.auth.application.TenantSettingsService;
import com.bayflow.auth.application.UserAdminService;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.security.TenantContext;
import com.bayflow.security.Tenants;

@Configuration
public class StartupData {

    private static final Logger log = LoggerFactory.getLogger(StartupData.class);

    /** Password of the development demo users (dev profile only). */
    public static final String DEV_PASSWORD = "Bayflow!2026";

    /** Keeps the service clients in the database in line with configuration. */
    @Bean
    ApplicationRunner syncServiceClients(ClientCredentialsService clientCredentials, AuthProperties properties) {
        return args -> TenantContext.runAcrossTenants(
                () -> clientCredentials.syncConfiguredClients(properties.getClients()));
    }

    /**
     * Creates the first ADMIN of the default workshop from bayflow.auth.bootstrap-admin.*
     * when it has no admin yet.
     */
    @Bean
    ApplicationRunner bootstrapAdmin(UserAdminService userAdmin, AuthProperties properties) {
        return args -> TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> {
            AuthProperties.BootstrapAdmin admin = properties.getBootstrapAdmin();
            if (StringUtils.hasText(admin.getPassword())
                    && userAdmin.bootstrapAdmin(admin.getUsername(), admin.getEmail(), admin.getPassword())) {
                log.info("Created bootstrap admin account '{}'. Change its password after first login.",
                        admin.getUsername());
            }
        });
    }

    /**
     * Creates the first Ramplight console user (PLATFORM_ADMIN) from bayflow.auth.platform-admin.*
     * when the console has none yet.
     */
    @Bean
    ApplicationRunner bootstrapPlatformAdmin(UserAdminService userAdmin, AuthProperties properties) {
        return args -> TenantContext.run(Platform.TENANT_ID, () -> {
            AuthProperties.BootstrapAdmin admin = properties.getPlatformAdmin();
            if (StringUtils.hasText(admin.getPassword())
                    && userAdmin.bootstrapPlatformAdmin(admin.getUsername(), admin.getEmail(), admin.getPassword())) {
                log.info("Created Ramplight console account '{}'. Change its password after first login.",
                        admin.getUsername());
            }
        });
    }

    /** The second demo workshop (acme.localhost), dev profile only. Fixed id so it is stable. */
    public static final UUID ACME_TENANT_ID = UUID.fromString("0000000a-c3e0-4000-8000-000000000002");

    /**
     * Two demo workshops for local development, {@code demo} (all pre-multi-tenancy data) and
     * {@code acme}, each with the same four users, so you can check they are independent.
     * Never active outside "dev".
     */
    @Bean
    @Profile("dev")
    ApplicationRunner seedDevWorkshops(UserAdminService userAdmin, TenantRepository tenants,
            TenantSettingsService settings, LocationService locations, Clock clock) {
        return args -> {
            if (tenants.findById(ACME_TENANT_ID).isEmpty() && !tenants.existsBySlug("acme")) {
                tenants.saveAndFlush(Tenant.create(ACME_TENANT_ID, "acme", "Acme Motors", clock.instant()));
                log.info("Created demo workshop 'acme' (acme.localhost)");
            }
            for (UUID workshop : List.of(Tenants.DEFAULT_TENANT_ID, ACME_TENANT_ID)) {
                if (tenants.findById(workshop).isEmpty()) {
                    continue;
                }
                TenantContext.run(workshop, () -> {
                    settings.current();
                    locations.ensureMain();
                    userAdmin.ensureUser("customer1", "customer1@bayflow.local", DEV_PASSWORD, Role.CUSTOMER);
                    userAdmin.ensureUser("advisor1", "advisor1@bayflow.local", DEV_PASSWORD, Role.SERVICE_ADVISOR);
                    userAdmin.ensureUser("mechanic1", "mechanic1@bayflow.local", DEV_PASSWORD, Role.MECHANIC);
                    userAdmin.ensureUser("admin1", "admin1@bayflow.local", DEV_PASSWORD, Role.ADMIN);
                });
            }
            // A Ramplight console account (<base domain>/console) for local work and the e2e tests.
            TenantContext.run(Platform.TENANT_ID, () -> userAdmin.ensureUser("console1", "console1@bayflow.local",
                    DEV_PASSWORD, Role.PLATFORM_ADMIN));
        };
    }
}
