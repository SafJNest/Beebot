package com.safjnest.redis;

import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class R4JCacheKeyBuilder {

    private static final String VALUE_KEY = "value";

    private R4JCacheKeyBuilder() {}

    public static String build(URLEndpoint endpoint, Map<String, Object> data) {
        return RedisKey.R4J_CACHE.of(endpoint.name(), parametersHash(data));
    }

    public static String endpointPattern(URLEndpoint endpoint) {
        return RedisKey.R4J_CACHE.of(endpoint.name(), "*");
    }

    public static String parametersHash(Map<String, Object> data) {
        StringBuilder parameters = new StringBuilder();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (VALUE_KEY.equals(entry.getKey())) continue;

            String normalized = normalize(entry.getValue());
            parameters.append(normalized.length())
                      .append(':')
                      .append(normalized)
                      .append(';');
        }

        return sha256(parameters.toString());
    }

    static String normalize(Object value) {
        if (value == null) return "null";
        return URLEncoder.encode(value.toString(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
