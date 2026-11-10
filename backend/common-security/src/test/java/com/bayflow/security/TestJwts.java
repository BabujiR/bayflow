package com.bayflow.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

final class TestJwts {

    private TestJwts() {
    }

    static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    static Jwt userJwt(String subject, String username, List<String> roles) {
        return jwt(Map.of("sub", subject, "username", username, "roles", roles, "aud", List.of("bayflow")));
    }
}
