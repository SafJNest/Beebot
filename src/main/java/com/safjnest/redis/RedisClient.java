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
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

public class RedisClient {

    private static final JedisPool pool;

    private static final ObjectMapper mapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true)
        .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
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

    public static void setBytes(String key, byte[] value, int ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            byte[] redisKey = key.getBytes(StandardCharsets.UTF_8);
            if (ttlSeconds > 0) {
                jedis.setex(redisKey, ttlSeconds, value);
            } else {
                jedis.set(redisKey, value);
            }
        }
    }

    public static byte[] getBytes(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void setSerializable(
        String key,
        Serializable value,
        int ttlSeconds
    ) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            output.flush();
            setBytes(key, bytes.toByteArray(), ttlSeconds);
        } catch (IOException ignored) {}
    }

    public static <T> T getSerializable(String key, Class<T> type) {
        byte[] payload = getBytes(key);
        if (payload == null) return null;

        try (ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
             ObjectInputStream input = new ObjectInputStream(bytes)) {
            Object value = input.readObject();
            if (type.isInstance(value)) return type.cast(value);
        } catch (IOException | ClassNotFoundException ignored) {}

        delete(key);
        return null;
    }

    public static void delete(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    public static long deleteByPattern(String pattern) {
        try (Jedis jedis = pool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(pattern).count(500);
            long deleted = 0;

            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                List<String> keys = result.getResult();
                if (!keys.isEmpty()) deleted += jedis.del(keys.toArray(String[]::new));
                cursor = result.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

            return deleted;
        }
    }

    public static long strlen(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.strlen(key.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static boolean exists(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    public static boolean isAvailable() {
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public static long rpush(String key, String element) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.rpush(key, element);
        }
    }

    public static long sadd(String key, String element) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.sadd(key, element);
        }
    }

    public static List<String> lrangeAll(String key) {
        try (Jedis jedis = pool.getResource()) {
            List<String> list = jedis.lrange(key, 0, -1);
            return list != null && !list.isEmpty() ? new ArrayList<>(list) : new ArrayList<>();
        }
    }

    public static List<String> popList(String key) {
        try (Jedis jedis = pool.getResource()) {
            Transaction tx = jedis.multi();
            Response<List<String>> range = tx.lrange(key, 0, -1);
            tx.del(key);
            tx.exec();
            List<String> list = range.get();
            return list != null && !list.isEmpty() ? new ArrayList<>(list) : new ArrayList<>();
        }
    }

    public static Set<String> smembers(String key) {
        try (Jedis jedis = pool.getResource()) {
            Transaction tx = jedis.multi();
            Response<Set<String>> members = tx.smembers(key);
            tx.del(key);
            tx.exec();
            Set<String> set = members.get();
            return set != null && !set.isEmpty() ? set : Set.of();
        }
    }

    public static void close() {
        pool.close();
    }
}
