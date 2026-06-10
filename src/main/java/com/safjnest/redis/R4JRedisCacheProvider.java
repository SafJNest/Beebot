package com.safjnest.redis;

import no.stelar7.api.r4j.basic.cache.CacheLifetimeHint;
import no.stelar7.api.r4j.basic.cache.CacheProvider;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class R4JRedisCacheProvider implements CacheProvider {

    private static final String VALUE_KEY = "value";
    private static final int DEFAULT_TTL_SECONDS = 600;
    private static final int SUMMONER_TTL_SECONDS = 600;
    private static final int ACCOUNT_TTL_SECONDS = 1_800;
    private static final int LEAGUE_TTL_SECONDS = 300;
    private static final int MASTERY_TTL_SECONDS = 600;
    private static final int MATCH_LIST_TTL_SECONDS = 120;
    private static final int SPECTATOR_TTL_SECONDS = 30;
    private static final int MATCH_TTL_SECONDS = 86_400;
    private static final int DDRAGON_TTL_SECONDS = 86_400;

    private final EnumMap<URLEndpoint, Integer> timeToLiveSeconds = new EnumMap<>(URLEndpoint.class);

    public R4JRedisCacheProvider() {
        resetTimeToLive();
    }

    @Override
    public void store(URLEndpoint endpoint, Map<String, Object> data) {
        if (!data.containsKey(VALUE_KEY)) throw new IllegalArgumentException("Invalid cache insert");

        Object value = data.get(VALUE_KEY);
        if (value == null) return;

        try {
            RedisClient.setBytes(
                R4JCacheKeyBuilder.build(endpoint, data),
                serialize(value),
                ttlSeconds(endpoint)
            );
        } catch (IOException | RuntimeException ignored) {}
    }

    @Override
    public void update(URLEndpoint endpoint, Map<String, Object> data) {
        store(endpoint, data);
    }

    @Override
    public Optional<?> get(URLEndpoint endpoint, Map<String, Object> data) {
        String key = R4JCacheKeyBuilder.build(endpoint, data);

        try {
            byte[] payload = RedisClient.getBytes(key);
            if (payload == null) return Optional.empty();

            Object value = deserialize(payload);
            if (!isExpectedType(endpoint, value)) {
                RedisClient.delete(key);
                return Optional.empty();
            }

            return Optional.ofNullable(value);
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            deleteQuietly(key);
            return Optional.empty();
        }
    }

    @Override
    public void clear(URLEndpoint endpoint, Map<String, Object> data) {
        try {
            if (hasParameters(data)) {
                RedisClient.delete(R4JCacheKeyBuilder.build(endpoint, data));
            } else {
                RedisClient.deleteByPattern(R4JCacheKeyBuilder.endpointPattern(endpoint));
            }
        } catch (RuntimeException ignored) {}
    }

    @Override
    public long getSize(URLEndpoint endpoint, Map<String, Object> data) {
        try {
            return RedisClient.strlen(R4JCacheKeyBuilder.build(endpoint, data));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void clearOldCache() {}

    @Override
    public long getTimeToLive(URLEndpoint endpoint) {
        int seconds = ttlSeconds(endpoint);
        return seconds > 0 ? TimeUnit.SECONDS.toMillis(seconds) : seconds;
    }

    @Override
    public synchronized void setTimeToLiveGlobal(long timeToLiveMS) {
        if (timeToLiveMS == TTL_USE_HINTS) {
            resetTimeToLive();
            return;
        }

        int seconds = toSeconds(timeToLiveMS);
        for (URLEndpoint endpoint : URLEndpoint.values()) timeToLiveSeconds.put(endpoint, seconds);
    }

    @Override
    public synchronized void setTimeToLive(CacheLifetimeHint hints) {
        for (URLEndpoint endpoint : URLEndpoint.values()) {
            timeToLiveSeconds.put(endpoint, toSeconds(hints.get(endpoint)));
        }
    }

    static byte[] serialize(Object value) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            output.flush();
            return bytes.toByteArray();
        }
    }

    static Object deserialize(byte[] payload) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
             ObjectInputStream input = new ObjectInputStream(bytes)) {
            return input.readObject();
        }
    }

    private synchronized void resetTimeToLive() {
        timeToLiveSeconds.clear();
        for (URLEndpoint endpoint : URLEndpoint.values()) {
            int ttl = endpoint.name().startsWith("DDRAGON_") ? DDRAGON_TTL_SECONDS : DEFAULT_TTL_SECONDS;
            timeToLiveSeconds.put(endpoint, ttl);
        }

        put(SUMMONER_TTL_SECONDS, URLEndpoint.V4_SUMMONER_BY_PUUID);
        put(ACCOUNT_TTL_SECONDS, URLEndpoint.V1_SHARED_ACCOUNT_BY_PUUID, URLEndpoint.V1_SHARED_ACCOUNT_BY_TAG);
        put(
            LEAGUE_TTL_SECONDS,
            URLEndpoint.V4_LEAGUE,
            URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID,
            URLEndpoint.V4_LEAGUE_RANK,
            URLEndpoint.V4_LEAGUE_MASTER,
            URLEndpoint.V4_LEAGUE_GRANDMASTER,
            URLEndpoint.V4_LEAGUE_CHALLENGER
        );
        put(
            MASTERY_TTL_SECONDS,
            URLEndpoint.V4_MASTERY_BY_PUUID,
            URLEndpoint.V4_MASTERY_BY_CHAMPION,
            URLEndpoint.V4_MASTERY_TOP,
            URLEndpoint.V4_MASTERY_SCORE
        );
        put(MATCH_LIST_TTL_SECONDS, URLEndpoint.V5_MATCHLIST, URLEndpoint.V5_MATCHLIST_REPLAYS);
        put(SPECTATOR_TTL_SECONDS, URLEndpoint.V5_SPECTATOR_CURRENT, URLEndpoint.V5_SPECTATOR_FEATURED);
        put(MATCH_TTL_SECONDS, URLEndpoint.V5_MATCH, URLEndpoint.V5_TIMELINE);
    }

    private void put(int seconds, URLEndpoint... endpoints) {
        for (URLEndpoint endpoint : endpoints) timeToLiveSeconds.put(endpoint, seconds);
    }

    private synchronized int ttlSeconds(URLEndpoint endpoint) {
        return timeToLiveSeconds.getOrDefault(endpoint, DEFAULT_TTL_SECONDS);
    }

    private static int toSeconds(long timeToLiveMS) {
        if (timeToLiveMS == TTL_INFINITY) return TTL_INFINITY;
        if (timeToLiveMS <= 0) return (int) timeToLiveMS;

        long seconds = Math.max(1, (timeToLiveMS + 999) / 1_000);
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private static boolean hasParameters(Map<String, Object> data) {
        for (String key : data.keySet()) {
            if (!VALUE_KEY.equals(key)) return true;
        }
        return false;
    }

    private static boolean isExpectedType(URLEndpoint endpoint, Object value) {
        Class<?> expectedType = endpoint.getType();
        if (value == null || expectedType == null || expectedType.isInstance(value)) return true;
        if (!(value instanceof Iterable<?> values)) return false;

        for (Object item : values) {
            if (item != null && !expectedType.isInstance(item)) return false;
        }
        return true;
    }

    private static void deleteQuietly(String key) {
        try {
            RedisClient.delete(key);
        } catch (RuntimeException ignored) {}
    }
}
