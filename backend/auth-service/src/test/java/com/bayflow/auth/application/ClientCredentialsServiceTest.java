package com.bayflow.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.infrastructure.ServiceClientRepository;

class ClientCredentialsServiceTest {

    @Test
    void theProdProfileRefusesTheDevelopmentSecrets() {
        var service = service("prod");

        var clients = Map.of("workshop-service", client("dev-workshop-secret"));

        assertThatThrownBy(() -> service.syncConfiguredClients(clients))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development secret");
    }

    @Test
    void otherProfilesOnlyWarn() {
        var service = service("dev");

        var clients = Map.of("workshop-service", client("dev-workshop-secret"));

        assertThatCode(() -> service.syncConfiguredClients(clients))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("deprecation")
    private static ClientCredentialsService service(String profile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new ClientCredentialsService(mock(ServiceClientRepository.class), NoOpPasswordEncoder.getInstance(),
                mock(TokenService.class), Clock.systemUTC(), environment);
    }

    private static AuthProperties.Client client(String secret) {
        var client = new AuthProperties.Client();
        client.setSecret(secret);
        client.setRoles(List.of("SYSTEM"));
        return client;
    }
}
