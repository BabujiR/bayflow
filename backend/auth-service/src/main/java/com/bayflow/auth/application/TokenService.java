package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

/** Issues RS256-signed access tokens for users and service clients. */
@Service
public class TokenService {

    /** A signed token and the moment it expires. */
    public record IssuedToken(String value, Instant issuedAt, Instant expiresAt) {

        /** Lifetime in seconds, as reported to clients in "expiresIn". */
        public long expiresInSeconds() {
            return Duration.between(issuedAt, expiresAt).getSeconds();
        }
    }

    private final JwtEncoder encoder;
    private final RsaKeyProvider keys;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(RsaKeyProvider keys, AuthProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(keys.signingJwk())));
    }

    public IssuedToken forUser(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
        return issue(user.getId().toString(), user.getUsername(), roles, properties.getAccessTokenTtl());
    }

    public IssuedToken forClient(String clientId, Collection<String> roles) {
        return issue(clientId, clientId, List.copyOf(roles), properties.getSystemTokenTtl());
    }

    private IssuedToken issue(String subject, String username, List<String> roles, Duration ttl) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("username", username)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keys.keyId())
                .type("JWT")
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, now, expiresAt);
    }
}
