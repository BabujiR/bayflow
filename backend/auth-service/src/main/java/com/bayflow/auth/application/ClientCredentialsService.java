package com.bayflow.auth.application;

import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.auth.application.TokenService.IssuedToken;
import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.ServiceClient;
import com.bayflow.auth.infrastructure.ServiceClientRepository;

/** Client-credentials grant for service-to-service calls. */
@Service
public class ClientCredentialsService {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsService.class);
    static final String DEV_SECRET = "dev-system-secret";
    /** The built-in development secrets (dev-system-secret, dev-workshop-secret...) all start so. */
    static final String DEV_SECRET_PREFIX = "dev-";

    private final ServiceClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;
    private final Environment environment;
    private final String dummyHash;

    public ClientCredentialsService(ServiceClientRepository clients, PasswordEncoder passwordEncoder,
            TokenService tokenService, Clock clock, Environment environment) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
        this.environment = environment;
        this.dummyHash = passwordEncoder.encode("bayflow-timing-equalizer");
    }

    @Transactional(readOnly = true)
    public IssuedToken issue(String clientId, String clientSecret) {
        ServiceClient client = StringUtils.hasText(clientId)
                ? clients.findById(clientId).filter(ServiceClient::isEnabled).orElse(null)
                : null;
        String secret = clientSecret == null ? "" : clientSecret;
        if (client == null) {
            passwordEncoder.matches(secret, dummyHash);
            throw AuthenticationFailedException.invalidClient();
        }
        if (!passwordEncoder.matches(secret, client.getSecretHash())) {
            throw AuthenticationFailedException.invalidClient();
        }
        return tokenService.forClient(client.getClientId(), client.getRoleList());
    }

    /** Creates or updates the clients listed under {@code bayflow.auth.clients} at startup. */
    @Transactional
    public void syncConfiguredClients(Map<String, AuthProperties.Client> configured) {
        configured.forEach((clientId, settings) -> {
            if (!StringUtils.hasText(settings.getSecret())) {
                throw new IllegalStateException("bayflow.auth.clients." + clientId + ".secret must be set");
            }
            if (settings.getSecret().startsWith(DEV_SECRET_PREFIX)
                    && environment.acceptsProfiles(Profiles.of("prod"))) {
                // A secret anyone can read on GitHub would let anyone call the services as SYSTEM.
                throw new IllegalStateException("bayflow.auth.clients." + clientId + ".secret is a development"
                        + " secret; the prod profile needs a real one (BAYFLOW_*_CLIENT_SECRET).");
            }
            if (settings.getSecret().startsWith(DEV_SECRET_PREFIX)) {
                log.warn("Client '{}' uses the development secret. Set a real secret outside local development.",
                        clientId);
            }
            String hash = passwordEncoder.encode(settings.getSecret());
            clients.findById(clientId).ifPresentOrElse(
                    existing -> existing.update(hash, settings.getRoles()),
                    () -> clients.save(new ServiceClient(clientId, hash, settings.getRoles(), clock.instant())));
        });
    }
}
