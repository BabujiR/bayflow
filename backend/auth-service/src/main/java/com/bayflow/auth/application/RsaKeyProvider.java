package com.bayflow.auth.application;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.bayflow.auth.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * The RSA key pair used to sign access tokens. Loaded from PEM files when configured;
 * otherwise (dev only) a throwaway key is generated at startup, so tokens become
 * invalid when auth-service restarts.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;

    public RsaKeyProvider(AuthProperties properties) {
        AuthProperties.Keys keys = properties.getKeys();
        if (StringUtils.hasText(keys.getPrivateKeyPath()) && StringUtils.hasText(keys.getPublicKeyPath())) {
            this.privateKey = PemKeys.readPrivateKey(Path.of(keys.getPrivateKeyPath()));
            this.publicKey = PemKeys.readPublicKey(Path.of(keys.getPublicKeyPath()));
            log.info("Loaded JWT signing key from {}", keys.getPrivateKeyPath());
        } else if (keys.isAllowEphemeral()) {
            KeyPair pair = generate();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            log.warn("No JWT signing key configured; generated a throwaway key. "
                    + "Tokens will stop working when auth-service restarts. Do not use this in production.");
        } else {
            throw new IllegalStateException("bayflow.auth.keys.private-key-path and public-key-path must be set "
                    + "(or set bayflow.auth.keys.allow-ephemeral=true for development)");
        }
        this.keyId = StringUtils.hasText(keys.getKeyId()) ? keys.getKeyId() : thumbprint(publicKey);
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    /** Full key (private part included), for signing. */
    public RSAKey signingJwk() {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    /** Public part only, for the JWKS endpoint. */
    public RSAKey publicJwk() {
        return new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }

    private static String thumbprint(RSAPublicKey key) {
        try {
            return new RSAKey.Builder(key).build().computeThumbprint().toString();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot compute key id", e);
        }
    }
}
