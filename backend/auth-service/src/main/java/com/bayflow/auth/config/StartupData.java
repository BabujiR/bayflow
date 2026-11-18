package com.bayflow.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.ClientCredentialsService;
import com.bayflow.auth.application.UserAdminService;
import com.bayflow.auth.domain.Role;
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

    /** Demo users in the default workshop, for local development. Never active outside "dev". */
    @Bean
    @Profile("dev")
    ApplicationRunner seedDevUsers(UserAdminService userAdmin) {
        return args -> TenantContext.run(Tenants.DEFAULT_TENANT_ID, () -> {
            userAdmin.ensureUser("customer1", "customer1@bayflow.local", DEV_PASSWORD, Role.CUSTOMER);
            userAdmin.ensureUser("advisor1", "advisor1@bayflow.local", DEV_PASSWORD, Role.SERVICE_ADVISOR);
            userAdmin.ensureUser("mechanic1", "mechanic1@bayflow.local", DEV_PASSWORD, Role.MECHANIC);
            userAdmin.ensureUser("admin1", "admin1@bayflow.local", DEV_PASSWORD, Role.ADMIN);
        });
    }
}
