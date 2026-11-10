package com.bayflow.auth.api;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.RsaKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Publishes the public signing key so other services can verify Bayflow tokens. */
@RestController
@Tag(name = "Keys")
public class JwksController {

    private final RsaKeyProvider keys;

    public JwksController(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "JSON Web Key Set with the public token-signing key")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new JWKSet(keys.publicJwk()).toJSONObject());
    }
}
