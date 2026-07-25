package com.safjnest.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.utils.JsonCodec;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

public class RedisClient {

    private static final int CONNECTION_TIMEOUT_MS = 500;
    private static final long RETRY_AFTER_FAILURE_MS = 30_000;
    private static final JedisPool pool;
    private static volatile long disabledUntil;

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

        pool = new JedisPool(config, "localhost", 6379, CONNECTION_TIMEOUT_MS);

        try {
            List<Jedis> warmup = new ArrayList<>();
            for (int i = 0; i < 8; i++) warmup.add(pool.getResource());
            warmup.forEach(Jedis::close);
        } catch (Exception ignored) {}
    }

    public static void set(String key, String value, int ttlSeconds) {
        if (!canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) {
                jedis.setex(key, ttlSeconds, value);
            } else {
                jedis.set(key, value);
            }
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static <T> void set(String key, T value, int ttlSeconds) {
        if (!canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) {
                jedis.setex(key, ttlSeconds, JsonCodec.toJson(value));
            } else {
                jedis.set(key, JsonCodec.toJson(value));
            }
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static String get(String key) {
        if (!canUseRedis()) return null;
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            markAvailable();
            return value;
        } catch (Exception e) {
            markUnavailable();
            return null;
        }
    }

    public static <T> T get(String key, Class<T> type) {
        if (!canUseRedis()) return null;
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            markAvailable();
            return value != null ? JsonCodec.fromJson(value, type) : null;
        } catch (Exception e) {
            markUnavailable();
            return null;
        }
    }

    public static <T> T get(String key, TypeReference<T> type) {
        if (!canUseRedis()) return null;
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            markAvailable();
            return value != null ? JsonCodec.fromJson(value, type) : null;
        } catch (Exception e) {
            markUnavailable();
            return null;
        }
    }

    public static <T> Map<String, T> get(List<String> keys, Class<T> type) {
        Map<String, T> result = new HashMap<>();
        if (keys == null || keys.isEmpty() || !canUseRedis()) return result;

        try (Jedis jedis = pool.getResource()) {
            List<String> values = jedis.mget(keys.toArray(new String[0]));
            for (int i = 0; i < keys.size(); i++) {
                String value = values.get(i);
                if (value != null) result.put(keys.get(i), JsonCodec.fromJson(value, type));
            }
            markAvailable();
        } catch (Exception exception) {
            markUnavailable();
        }
        return result;
    }

    public static void delete(String key) {
        if (!canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static long increment(String key) {
        if (!canUseRedis()) return 0;
        try (Jedis jedis = pool.getResource()) {
            long value = jedis.incr(key);
            markAvailable();
            return value;
        } catch (Exception ignored) {
            markUnavailable();
            return 0;
        }
    }

    public static boolean exists(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
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
        catch (Exception ignored) {
            return 0;
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

    private static boolean canUseRedis() {
        return System.currentTimeMillis() >= disabledUntil;
    }

    private static void markUnavailable() {
        disabledUntil = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
    }

    private static void markAvailable() {
        disabledUntil = 0;
    }
}
