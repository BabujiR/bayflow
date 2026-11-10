package com.bayflow.auth.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.bayflow.auth.application.ClientCredentialsService;
import com.bayflow.auth.application.UserAdminService;
import com.bayflow.auth.domain.Role;

@Configuration
public class StartupData {

    /** Password of the development demo users (dev profile only). */
    public static final String DEV_PASSWORD = "Bayflow!2026";

    /** Keeps the service clients in the database in line with configuration. */
    @Bean
    ApplicationRunner syncServiceClients(ClientCredentialsService clientCredentials, AuthProperties properties) {
        return args -> clientCredentials.syncConfiguredClients(properties.getClients());
    }

    /** Demo users for local development. Never active outside the "dev" profile. */
    @Bean
    @Profile("dev")
    ApplicationRunner seedDevUsers(UserAdminService userAdmin) {
        return args -> {
            userAdmin.ensureUser("customer1", "customer1@bayflow.local", DEV_PASSWORD, Role.CUSTOMER);
            userAdmin.ensureUser("advisor1", "advisor1@bayflow.local", DEV_PASSWORD, Role.SERVICE_ADVISOR);
            userAdmin.ensureUser("mechanic1", "mechanic1@bayflow.local", DEV_PASSWORD, Role.MECHANIC);
        };
    }
}
