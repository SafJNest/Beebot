package com.safjnest.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisClient {

    private static final JedisPool pool = new JedisPool(buildPoolConfig(), "localhost", 6379);

    private static final ObjectMapper mapper = JsonMapper.builder()
        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true)
        .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .build();

    private static JedisPoolConfig buildPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        config.setMaxIdle(8);
        config.setMinIdle(2);
        config.setTestOnBorrow(true);
        return config;
    }

    public static void set(String key, String value, int ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
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