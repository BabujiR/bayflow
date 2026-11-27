package com.bayflow.appointment.bookingservices;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The private "change or cancel" link of an online booking: a random token in the link, and
 * only its SHA-256 in the database (a leaked database holds no working links).
 */
public final class ManageTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ManageTokens() {
    }

    /** 32 random bytes, URL-safe (43 characters). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Shape check before any lookup (a token is 43 URL-safe characters). */
    public static boolean looksValid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }
}
