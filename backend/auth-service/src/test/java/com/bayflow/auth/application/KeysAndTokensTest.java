package com.bayflow.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.bayflow.auth.config.AuthProperties;
import com.bayflow.auth.domain.Role;
import com.bayflow.auth.domain.User;
import com.bayflow.security.BayflowTokenValidators;

class KeysAndTokensTest {

    @TempDir
    Path dir;

    @Test
    void loadsPemKeysFromFiles() throws Exception {
        KeyPair pair = rsaKeyPair();
        Path privatePem = write("private.pem", pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Path publicPem = write("public.pem", pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        AuthProperties properties = new AuthProperties();
        properties.getKeys().setPrivateKeyPath(privatePem.toString());
        properties.getKeys().setPublicKeyPath(publicPem.toString());
        properties.getKeys().setKeyId("key-2026");

        RsaKeyProvider provider = new RsaKeyProvider(properties);

        assertThat(provider.publicKey()).isEqualTo(pair.getPublic());
        assertThat(provider.keyId()).isEqualTo("key-2026");
        assertThat(provider.publicJwk().isPrivate()).isFalse();
        assertThat(provider.signingJwk().isPrivate()).isTrue();
    }

    @Test
    void generatesAnEphemeralKeyWithThumbprintIdWhenNoneConfigured() {
        RsaKeyProvider provider = new RsaKeyProvider(new AuthProperties());

        assertThat(provider.publicKey().getModulus().bitLength()).isEqualTo(2048);
        assertThat(provider.keyId()).isNotBlank();
    }

    @Test
    void refusesToStartWithoutKeysWhenEphemeralIsDisabled() {
        AuthProperties properties = new AuthProperties();
        properties.getKeys().setAllowEphemeral(false);

        assertThatThrownBy(() -> new RsaKeyProvider(properties)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPkcs1PrivateKeys() throws Exception {
        Path file = write("old.pem", "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");

        assertThatThrownBy(() -> PemKeys.readPrivateKey(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKCS#8");
    }

    @Test
    void rejectsFilesWithoutTheExpectedPemBlock() throws Exception {
        Path file = write("junk.pem", "not a key");

        assertThatThrownBy(() -> PemKeys.readPublicKey(file)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PemKeys.readPrivateKey(dir.resolve("missing.pem")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userTokenCarriesBayflowClaimsAndVerifiesWithThePublicKey() {
        AuthProperties properties = new AuthProperties();
        RsaKeyProvider keys = new RsaKeyProvider(properties);
        TokenService tokens = new TokenService(keys, properties, Clock.systemUTC());
        User user = User.create("advisor1", "a@b.c", "{noop}x", Set.of(Role.SERVICE_ADVISOR), Instant.now());

        TokenService.IssuedToken issued = tokens.forUser(user);
        Jwt jwt = decoder(keys, properties).decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo("advisor1");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE_ADVISOR");
        assertThat(jwt.getAudience()).containsExactly("bayflow");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("bayflow-auth");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("kid", keys.keyId()).containsEntry("typ", "JWT");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(issued.expiresAt()).isEqualTo(jwt.getExpiresAt());
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void clientTokenHasSystemRoleAndShorterLifetime() {
        AuthProperties properties = new AuthProperties();
        RsaKeyProvider keys = new RsaKeyProvider(properties);
        TokenService tokens = new TokenService(keys, properties, Clock.systemUTC());

        Jwt jwt = decoder(keys, properties).decode(
                tokens.forClient("appointment-service", java.util.List.of("SYSTEM")).value());

        assertThat(jwt.getSubject()).isEqualTo("appointment-service");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SYSTEM");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void secureTokensAreRandomAndHashesAreStable() {
        String a = SecureTokens.newToken();
        String b = SecureTokens.newToken();

        assertThat(a).hasSize(43).isNotEqualTo(b);
        assertThat(SecureTokens.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private static NimbusJwtDecoder decoder(RsaKeyProvider keys, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        decoder.setJwtValidator(BayflowTokenValidators.of(properties.getIssuer(), properties.getAudience()));
        return decoder;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(dir.resolve(name), content, StandardCharsets.US_ASCII);
    }
}
