package com.bayflow.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Standard Bayflow token checks: signature (by the decoder), expiry, issuer and audience. */
public final class BayflowTokenValidators {

    private BayflowTokenValidators() {
    }

    public static OAuth2TokenValidator<Jwt> of(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience));
    }
}
