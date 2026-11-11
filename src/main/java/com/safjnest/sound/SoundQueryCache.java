package com.safjnest.sound;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.App;
import com.safjnest.redis.RedisClient;

public class SoundQueryCache {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int TTL_SECONDS = 60;

    public interface Store {
        String get(String key);
        void put(String key, String value, int ttl);
    }

    private final Store store;

    public SoundQueryCache() {
        this(new Store() {
            public String get(String key) {
                return RedisClient.get(key);
            }
            public void put(String key, String value, int ttl) {
                RedisClient.setValue(key, value, ttl);
            }
        });
    }

    public SoundQueryCache(Store store) {
        this.store = store;
    }

    public List<String> ids(List<?> parameters, Supplier<List<String>> source) {
        String generation = store.get(prefix() + "generation");
        if (generation == null) return source.get();
        String key = prefix() + "v1:" + generation + ":" + hash(parameters);
        String value = store.get(key);
        if (value != null) {
            try {
                List<String> ids = JSON.readValue(value, new TypeReference<List<String>>() {
                }
                );
                if (ids != null && ids.stream().allMatch(id -> id != null && id.matches("[0-9]+"))) return ids;
            }
            catch (Exception ignored) {
            }
        }
        List<String> ids = source.get();
        try {
            store.put(key, JSON.writeValueAsString(ids), TTL_SECONDS);
        }
        catch (Exception ignored) {
        }
        return ids;
    }

    public void invalidate() {
        store.put(prefix() + "generation", UUID.randomUUID().toString(), 0);
    }

    public void initialize() {
        if (store.get(prefix() + "generation") == null) invalidate();
    }
    // ================================================================================================================

    private String prefix() {
        return (App.isTesting() ? "beebot_test" : "beebot") + ":sound:query:";
    }

    private String hash(List<?> parameters) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(parameters).getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("Invalid query key", exception);
        }
    }
}
