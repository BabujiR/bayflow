package com.bayflow.auth.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Reads RSA keys from PEM files: PKCS#8 private keys and X.509 public keys. */
public final class PemKeys {

    private PemKeys() {
    }

    public static RSAPrivateKey readPrivateKey(Path path) {
        String pem = read(path);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException("Private key " + path + " is PKCS#1. Convert it to PKCS#8: "
                    + "openssl pkcs8 -topk8 -nocrypt -in old.pem -out new.pem");
        }
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decode(pem, "PRIVATE KEY")));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Cannot parse RSA private key " + path, e);
        }
    }

    public static RSAPublicKey readPublicKey(Path path) {
        String pem = read(path);
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decode(pem, "PUBLIC KEY")));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Cannot parse RSA public key " + path, e);
        }
    }

    static byte[] decode(String pem, String label) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < 0) {
            throw new IllegalArgumentException("Expected a PEM block of type " + label);
        }
        String base64 = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read key file " + path, e);
        }
    }
}
