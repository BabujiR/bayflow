package com.bayflow.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random opaque tokens and their SHA-256 hashes (only hashes are stored). */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** 256 bits of randomness, URL-safe Base64 without padding (43 characters). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Letters and digits that can't be mistaken for each other when read out (no I, L, O, 0, 1). */
    private static final String READABLE = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /**
     * A temporary password a person can read out over the phone: 12 characters from
     * {@link #READABLE} in groups of four, e.g. {@code K7QH-3MZD-W9PT} (about 59 bits).
     */
    public static String temporaryPassword() {
        StringBuilder password = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                password.append('-');
            }
            password.append(READABLE.charAt(RANDOM.nextInt(READABLE.length())));
        }
        return password.toString();
    }

    /** Lower-case hex SHA-256 (64 characters). */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
