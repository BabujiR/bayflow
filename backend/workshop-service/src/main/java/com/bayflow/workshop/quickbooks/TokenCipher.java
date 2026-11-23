package com.bayflow.workshop.quickbooks;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.bayflow.workshop.config.WorkshopProperties;

/**
 * Encrypts QuickBooks tokens before they are stored: AES-256-GCM (JDK only), a fresh 96-bit IV
 * each time, and the organization's id as associated data, so a token copied to another
 * organization's row does not decrypt. Stored as "v1:" + base64(iv ‖ ciphertext ‖ tag).
 */
@Component
public class TokenCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public TokenCipher(WorkshopProperties properties) {
        String encoded = properties.getQuickbooks().getTokenKey();
        this.key = encoded.isBlank() ? null : key(encoded);
    }

    public boolean isAvailable() {
        return key != null;
    }

    public String encrypt(String plain, UUID tenantId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(tenantId));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a QuickBooks token", e);
        }
    }

    public String decrypt(String stored, UUID tenantId) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Not an encrypted QuickBooks token");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            cipher.updateAAD(aad(tenantId));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("A QuickBooks token could not be decrypted (was the key changed?)", e);
        }
    }

    private SecretKeySpec requireKey() {
        if (key == null) {
            throw new IllegalStateException("BAYFLOW_QBO_TOKEN_KEY is not set");
        }
        return key;
    }

    private static byte[] aad(UUID tenantId) {
        return tenantId.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static SecretKeySpec key(String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("BAYFLOW_QBO_TOKEN_KEY must be base64 (openssl rand -base64 32)", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("BAYFLOW_QBO_TOKEN_KEY must be 32 bytes in base64 (openssl rand -base64 32)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
