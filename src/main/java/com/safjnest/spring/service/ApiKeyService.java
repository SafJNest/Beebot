package com.safjnest.spring.service;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;

import com.safjnest.spring.api.model.ApiKey;
import com.safjnest.sql.database.WebsiteDB;

import java.util.Base64;
import java.util.Optional;

@Service
public class ApiKeyService {

    public String generateAndSaveApiKey(String owner) {
        String rawKey = generateRawKey();

        String sha256 = sha256Hash(rawKey);

        Optional<ApiKey> existing = WebsiteDB.getApiBySha256Hash(sha256);
        if (existing.isPresent()) {
            throw new RuntimeException("Duplicate API key detected! Try again.");
        }

        String hashedKey = hashKey(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setHashedKey(hashedKey);
        apiKey.setSha256Hash(sha256);
        apiKey.setOwner(owner);

        WebsiteDB.insertApiKey(apiKey);

        return rawKey;
    }

    public boolean isValidApiKey(String rawKey) {
        String sha256 = sha256Hash(rawKey);
        Optional<ApiKey> possible = WebsiteDB.getApiBySha256Hash(sha256);

        return possible.filter(k -> k.isActive() && BCrypt.checkpw(rawKey, k.getHashedKey())).isPresent();
    }

    private String generateRawKey() {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String sha256Hash(String input) {
        return DigestUtils.sha256Hex(input);
    }

    private String hashKey(String rawKey) {
        return BCrypt.hashpw(rawKey, BCrypt.gensalt());
    }
}