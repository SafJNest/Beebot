package com.safjnest.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private CryptoUtils() {}

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static SecretKeySpec parseEncryptionKey(String configured) {
        if (configured == null || configured.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(configured);
            return bytes.length == 32 ? new SecretKeySpec(bytes, "AES") : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static String encrypt(String plaintext, SecretKeySpec key) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Encryption unavailable", exception);
        }
    }

    public static String decrypt(String encryptedValue, SecretKeySpec key) {
        try {
            byte[] value = Base64.getDecoder().decode(encryptedValue);
            byte[] nonce = Arrays.copyOfRange(value, 0, GCM_NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(value, GCM_NONCE_BYTES, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Decryption unavailable", exception);
        }
    }
}
