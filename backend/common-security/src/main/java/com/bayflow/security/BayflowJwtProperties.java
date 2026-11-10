package com.bayflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How resource servers validate Bayflow access tokens.
 *
 * <pre>
 * bayflow.security.jwt.jwk-set-uri=http://auth-service:8083/.well-known/jwks.json
 * bayflow.security.jwt.issuer=bayflow-auth
 * bayflow.security.jwt.audience=bayflow
 * </pre>
 */
@ConfigurationProperties("bayflow.security.jwt")
public class BayflowJwtProperties {

    /** JWKS endpoint of auth-service. When unset, no JwtDecoder is auto-configured. */
    private String jwkSetUri;

    /** Expected "iss" claim. */
    private String issuer = "bayflow-auth";

    /** Expected value in the "aud" claim. */
    private String audience = "bayflow";

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
