package com.safjnest.spring.util;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;

public class ApiKeyGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    public static String generateApiKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    public static String generateJWTKey() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String base64Encoded = base64Encoder.encodeToString(key.getEncoded());
        return base64Encoded;
    }
}