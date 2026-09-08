package com.safjnest.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.utils.JsonCodec;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import redis.clients.jedis.Response;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.SetParams;

public class RedisClient {

    public record SortedSetEntry(byte[] member, double score) {}

    public static final int SORTED_SET_BATCH_SIZE = 250;
    private static final int CONNECTION_TIMEOUT_MS = 500;
    private static final int TEMPORARY_TTL_SECONDS = 60;
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

    public static void set(RedisKey key, String value, Object... args) {
        set(key.of(args), value);
    }

    public static <T> void set(RedisKey key, T value, Object... args) {
        set(key.of(args), value);
    }

    public static boolean claim(RedisKey key, String value, Object... args) {
        if (key == null || !canUseRedis()) return false;
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.set(key.of(args), value, SetParams.setParams().nx().ex(TEMPORARY_TTL_SECONDS));
            markAvailable();
            return "OK".equals(result);
        } catch (Exception ignored) {
            markUnavailable();
            return false;
        }
    }

    private static void set(String key, String value) {
        if (!canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, TEMPORARY_TTL_SECONDS, value);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    private static <T> void set(String key, T value) {
        if (!canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, TEMPORARY_TTL_SECONDS, JsonCodec.toJson(value));
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

    public static void delete(List<String> keys) {
        if (keys == null || keys.isEmpty() || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(keys.toArray(new String[0]));
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static boolean exists(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    public static long rpush(String key, String element) {
        try (Jedis jedis = pool.getResource()) {
            long result = jedis.rpush(key, element);
            jedis.expire(key, TEMPORARY_TTL_SECONDS);
            return result;
        }
    }

    public static long sadd(String key, String element) {
        try (Jedis jedis = pool.getResource()) {
            long result = jedis.sadd(key, element);
            jedis.expire(key, TEMPORARY_TTL_SECONDS);
            return result;
        }
        catch (Exception ignored) {
            return 0;
        }
    }

    public static Set<String> members(String key) {
        try (Jedis jedis = pool.getResource()) {
            Set<String> members = jedis.smembers(key);
            return members != null && !members.isEmpty() ? Set.copyOf(members) : Set.of();
        }
    }

    public static long countMembers(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.scard(key);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static long removeMember(String key, String element) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.srem(key, element);
        } catch (Exception ignored) {
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

    public static Long getLong(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static void setCached(String key, String value, int ttlSeconds) {
        if (key == null || value == null || ttlSeconds <= 0 || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static long ttl(String key) {
        if (key == null || !canUseRedis()) return -2;
        try (Jedis jedis = pool.getResource()) {
            long remaining = jedis.ttl(key);
            markAvailable();
            return remaining;
        } catch (Exception ignored) {
            markUnavailable();
            return -2;
        }
    }

    public static void expire(String key, int seconds) {
        if (key == null || seconds <= 0 || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.expire(key, seconds);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void addPersistentMember(String key, String member) {
        if (key == null || member == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.sadd(key, member);
            jedis.persist(key);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void setPersistent(String key, long value) {
        if (key == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, Long.toString(value));
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static Long dbSize() {
        if (!canUseRedis()) return null;
        try (Jedis jedis = pool.getResource()) {
            long size = jedis.dbSize();
            markAvailable();
            return size;
        } catch (Exception ignored) {
            markUnavailable();
            return null;
        }
    }

    public static Long usedMemory() {
        if (!canUseRedis()) return null;
        try (Jedis jedis = pool.getResource()) {
            Long memory = RedisMemoryParser.parseUsedMemory(jedis.info("memory"));
            markAvailable();
            return memory;
        } catch (Exception ignored) {
            markUnavailable();
            return null;
        }
    }

    public static boolean sortedSetExists(String key) {
        if (key == null || !canUseRedis()) return false;
        try (Jedis jedis = pool.getResource()) {
            boolean result = jedis.exists(key);
            markAvailable();
            return result;
        } catch (Exception ignored) {
            markUnavailable();
            return false;
        }
    }

    public static Set<String> existingSortedSets(List<String> keys) {
        Set<String> result = new java.util.HashSet<>();
        if (keys == null || keys.isEmpty() || !canUseRedis()) return result;
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            List<Response<Boolean>> responses = new ArrayList<>(keys.size());
            for (String key : keys) responses.add(pipeline.exists(key));
            pipeline.sync();
            for (int index = 0; index < keys.size(); index++) if (Boolean.TRUE.equals(responses.get(index).get())) result.add(keys.get(index));
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
        return result;
    }

    public static void addSortedSet(String key, List<SortedSetEntry> entries) {
        if (key == null || entries == null || entries.isEmpty() || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            byte[] encodedKey = key.getBytes(StandardCharsets.UTF_8);
            for (SortedSetEntry entry : entries)
                if (entry != null && entry.member() != null) pipeline.zadd(encodedKey, entry.score(), entry.member());
            pipeline.sync();
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void removeSortedSetMember(String key, byte[] member) {
        if (key == null || member == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.zrem(key.getBytes(StandardCharsets.UTF_8), member);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void publishSortedSet(String temporaryKey, String key, int ttlSeconds) {
        if (temporaryKey == null || key == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.rename(temporaryKey, key);
            if (ttlSeconds > 0) jedis.expire(key, ttlSeconds);
            else jedis.persist(key);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static boolean buildSortedSet(
        String temporaryKey,
        String key,
        int ttlSeconds,
        int temporaryTtlSeconds,
        Consumer<Consumer<SortedSetEntry>> source
    ) {
        if (temporaryKey == null || key == null || source == null) return false;
        List<SortedSetEntry> batch = new ArrayList<>(SORTED_SET_BATCH_SIZE);
        try {
            source.accept(entry -> {
                batch.add(entry);
                if (batch.size() < SORTED_SET_BATCH_SIZE) return;
                addSortedSet(temporaryKey, batch);
                batch.clear();
            });
            if (!batch.isEmpty()) addSortedSet(temporaryKey, batch);
            expire(temporaryKey, temporaryTtlSeconds);
            if (!sortedSetExists(temporaryKey)) return false;
            publishSortedSet(temporaryKey, key, ttlSeconds);
            return sortedSetExists(key);
        } finally {
            delete(temporaryKey);
        }
    }

    public static List<Long> reverseRanks(List<String> keys, List<byte[]> members) {
        List<Long> result = new ArrayList<>();
        if (keys == null || members == null || keys.size() != members.size() || !canUseRedis()) return result;
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            List<Response<Long>> responses = new ArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++)
                responses.add(pipeline.zrevrank(keys.get(index).getBytes(StandardCharsets.UTF_8), members.get(index)));
            pipeline.sync();
            for (Response<Long> response : responses) result.add(response.get());
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
        return result;
    }

    public static List<Long> countsAbove(List<String> keys, List<Double> scores) {
        List<Long> result = new ArrayList<>();
        if (keys == null || scores == null || keys.size() != scores.size() || !canUseRedis()) return result;
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            List<Response<Long>> responses = new ArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++)
                responses.add(pipeline.zcount(keys.get(index).getBytes(StandardCharsets.UTF_8), Math.nextUp(scores.get(index)), Double.POSITIVE_INFINITY));
            pipeline.sync();
            for (Response<Long> response : responses) result.add(response.get());
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
        return result;
    }

    public static long sortedSetCardinality(String key) {
        if (key == null || !canUseRedis()) return 0;
        try (Jedis jedis = pool.getResource()) {
            long result = jedis.zcard(key);
            markAvailable();
            return result;
        } catch (Exception ignored) {
            markUnavailable();
            return 0;
        }
    }

    public static Long memoryUsage(String key) {
        if (key == null || !canUseRedis()) return null;
        try (Jedis jedis = pool.getResource()) {
            Long result = jedis.memoryUsage(key);
            markAvailable();
            return result;
        } catch (Exception ignored) {
            markUnavailable();
            return null;
        }
    }

    public static Map<String, Long> getHashLongs(String key) {
        Map<String, Long> result = new HashMap<>();
        if (key == null || !canUseRedis()) return result;
        try (Jedis jedis = pool.getResource()) {
            for (Map.Entry<String, String> entry : jedis.hgetAll(key).entrySet()) {
                try { result.put(entry.getKey(), Long.parseLong(entry.getValue())); }
                catch (NumberFormatException ignored) {}
            }
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
        return result;
    }

    public static void replaceHash(String key, Map<String, Long> values) {
        if (key == null || values == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
            Map<String, String> encoded = new HashMap<>();
            for (Map.Entry<String, Long> entry : values.entrySet()) encoded.put(entry.getKey(), Long.toString(entry.getValue()));
            if (!encoded.isEmpty()) jedis.hset(key, encoded);
            jedis.persist(key);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void incrementHashIfPresent(String key, String field, long amount) {
        if (key == null || field == null || amount == 0 || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            if (jedis.exists(key)) jedis.hincrBy(key, field, amount);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void setHashLong(String key, String field, long value) {
        if (key == null || field == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(key, field, Long.toString(value));
            jedis.persist(key);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
    }

    public static void removeHashField(String key, String field) {
        if (key == null || field == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hdel(key, field);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
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
