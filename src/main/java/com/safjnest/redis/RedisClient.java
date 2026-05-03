package com.safjnest.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RedisClient {

    private static final JedisPool pool;

    private static final ObjectMapper mapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true)
        .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .build();

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(32);
        config.setMaxIdle(32);
        config.setMinIdle(8);
        config.setTestOnBorrow(false);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        config.setMinEvictableIdleDuration(Duration.ofMinutes(1));
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        config.setNumTestsPerEvictionRun(-1);
        config.setBlockWhenExhausted(true);

        pool = new JedisPool(config, "localhost", 6379, 2000);

        try {
            List<Jedis> warmup = new ArrayList<>();
            for (int i = 0; i < 8; i++) warmup.add(pool.getResource());
            warmup.forEach(Jedis::close);
        } catch (Exception ignored) {}
    }

    public static void set(String key, String value, int ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) {
                jedis.setex(key, ttlSeconds, value);
            } else {
                jedis.set(key, value);
            }
        }
    }

    public static <T> void set(String key, T value, int ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) {
                jedis.setex(key, ttlSeconds, mapper.writeValueAsString(value));
            } else {
                jedis.set(key, mapper.writeValueAsString(value));
            }
        } catch (Exception ignored) {}
    }

    public static String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    public static <T> T get(String key, Class<T> type) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            return value != null ? mapper.readValue(value, type) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T get(String key, TypeReference<T> type) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            return value != null ? mapper.readValue(value, type) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void delete(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    public static boolean exists(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    public static void close() {
        pool.close();
    }
}