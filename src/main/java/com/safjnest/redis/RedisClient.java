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

    public static final int SORTED_SET_BATCH_SIZE = 1_000;
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

    public static void set(RedisKey key, String value, Object... args) {
        if (key == null) return;
        set(key.of(args), value, key.ttlSeconds());
    }

    public static <T> void set(RedisKey key, T value, Object... args) {
        if (key == null) return;
        set(key.of(args), JsonCodec.toJson(value), key.ttlSeconds());
    }

    public static boolean claim(RedisKey key, String value, Object... args) {
        if (key == null || !canUseRedis()) return false;
        try (Jedis jedis = pool.getResource()) {
            SetParams params = SetParams.setParams().nx();
            if (key.ttlSeconds() > 0) params.ex(key.ttlSeconds());
            String result = jedis.set(key.of(args), value, params);
            markAvailable();
            return "OK".equals(result);
        } catch (Exception ignored) {
            markUnavailable();
            return false;
        }
    }

    private static void set(String key, String value, int ttlSeconds) {
        if (key == null || value == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) jedis.setex(key, ttlSeconds, value);
            else jedis.set(key, value);
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
            jedis.unlink(keys.toArray(new String[0]));
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

    public static long rpush(RedisKey key, String element, Object... args) {
        if (key == null || element == null || !canUseRedis()) return 0;
        try (Jedis jedis = pool.getResource()) {
            String redisKey = key.of(args);
            long result = jedis.rpush(redisKey, element);
            applyTtl(jedis, redisKey, key.ttlSeconds());
            markAvailable();
            return result;
        } catch (Exception ignored) {
            markUnavailable();
            return 0;
        }
    }

    public static long sadd(RedisKey key, String element, Object... args) {
        if (key == null || element == null || !canUseRedis()) return 0;
        try (Jedis jedis = pool.getResource()) {
            String redisKey = key.of(args);
            long result = jedis.sadd(redisKey, element);
            applyTtl(jedis, redisKey, key.ttlSeconds());
            markAvailable();
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

    public static void setCached(RedisKey key, String value, Object... args) {
        if (key == null) return;
        set(key.of(args), value, key.ttlSeconds());
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

    public static void expire(RedisKey key, Object... args) {
        if (key == null) return;
        expire(key.of(args), key.ttlSeconds());
    }

    public static void expire(String key, int seconds) {
        if (key == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            applyTtl(jedis, key, seconds);
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
        if (key == null || entries == null || entries.isEmpty()) return;
        if (!canUseRedis()) throw new IllegalStateException("Redis is unavailable");
        try (Jedis jedis = pool.getResource()) {
            writeSortedSet(jedis, key, entries, 0);
            markAvailable();
        } catch (RuntimeException failure) {
            markUnavailable();
            throw failure;
        }
    }

    public static void removeSortedSetMember(String key, byte[] member) {
        if (key == null || member == null || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.eval("if redis.call('TYPE',KEYS[1]).ok=='zset' then return redis.call('ZREM',KEYS[1],ARGV[1]) end; return 0".getBytes(StandardCharsets.UTF_8),
                List.of(key.getBytes(StandardCharsets.UTF_8)), List.of(member));
            markAvailable();
        } catch (RuntimeException failure) {
            markUnavailable();
            throw failure;
        }
    }

    public static boolean buildSortedSet(RedisKey temporaryRedisKey, Object[] temporaryValues,
            RedisKey redisKey, Object[] values, Consumer<Consumer<SortedSetEntry>> source) {
        String key = redisKey.of(values);
        if (!canUseRedis()) return false;
        if (sortedSetExists(key)) return true;
        String temporary = temporaryRedisKey.of(temporaryValues);
        List<SortedSetEntry> batch = new ArrayList<>(SORTED_SET_BATCH_SIZE);
        try (Jedis jedis = pool.getResource()) {
            source.accept(entry -> {
                batch.add(entry);
                if (batch.size() < SORTED_SET_BATCH_SIZE) return;
                writeSortedSet(jedis, temporary, batch, temporaryRedisKey.ttlSeconds());
                batch.clear();
            });
            if (!batch.isEmpty()) writeSortedSet(jedis, temporary, batch, temporaryRedisKey.ttlSeconds());
            jedis.eval("if redis.call('EXISTS',KEYS[2])==1 then return 1 end; "
                + "if redis.call('EXISTS',KEYS[1])==0 then redis.call('SET',KEYS[2],'','EX',60); return 1 end; "
                + "redis.call('RENAME',KEYS[1],KEYS[2]); if tonumber(ARGV[1])>0 then redis.call('EXPIRE',KEYS[2],ARGV[1]) else redis.call('PERSIST',KEYS[2]) end; return 1",
                List.of(temporary, key), List.of(Integer.toString(redisKey.ttlSeconds())));
            return true;
        } catch (RuntimeException failure) {
            markUnavailable();
            throw failure;
        } finally {
            delete(temporary);
        }
    }

    private static void writeSortedSet(Jedis jedis, String key, List<SortedSetEntry> entries, int ttl) {
        List<byte[]> args = new ArrayList<>(entries.size() * 2 + 1);
        args.add(Integer.toString(ttl).getBytes(StandardCharsets.UTF_8));
        for (SortedSetEntry entry : entries) { args.add(Double.toString(entry.score()).getBytes(StandardCharsets.UTF_8)); args.add(entry.member()); }
        jedis.eval(("if redis.call('TYPE',KEYS[1]).ok=='string' then redis.call('UNLINK',KEYS[1]) end; "
            + "redis.call('ZADD',KEYS[1],unpack(ARGV,2)); if tonumber(ARGV[1])>0 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return 1").getBytes(StandardCharsets.UTF_8),
            List.of(key.getBytes(StandardCharsets.UTF_8)), args);
    }

    public static List<Long> reverseRanks(List<String> keys, List<byte[]> members) {
        List<Long> result = new ArrayList<>();
        if (keys == null || members == null || keys.size() != members.size() || !canUseRedis()) return result;
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            List<Response<Object>> responses = new ArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++)
                responses.add(pipeline.eval("if redis.call('TYPE',KEYS[1]).ok~='zset' then return false end; return redis.call('ZREVRANK',KEYS[1],ARGV[1])".getBytes(StandardCharsets.UTF_8),
                    List.of(keys.get(index).getBytes(StandardCharsets.UTF_8)), List.of(members.get(index))));
            pipeline.sync();
            for (Response<Object> response : responses) result.add(response.get() instanceof Number value ? value.longValue() : null);
            markAvailable();
        } catch (Exception ignored) {
            markUnavailable();
        }
        return result;
    }

    public static List<byte[]> reverseRange(String key, long start, long stop) {
        if (key == null || start < 0 || stop < start || !canUseRedis()) return List.of();
        try (Jedis jedis = pool.getResource()) {
            if (!"zset".equals(jedis.type(key))) return List.of();
            List<byte[]> result = new ArrayList<>(jedis.zrevrange(key.getBytes(StandardCharsets.UTF_8), start, stop));
            markAvailable();
            return result;
        } catch (Exception ignored) {
            markUnavailable();
            return List.of();
        }
    }

    public static long sortedSetCardinality(String key) {
        if (key == null || !canUseRedis()) return 0;
        try (Jedis jedis = pool.getResource()) {
            long result = ((Number) jedis.eval("if redis.call('TYPE',KEYS[1]).ok=='zset' then return redis.call('ZCARD',KEYS[1]) end; return 0", List.of(key), List.of())).longValue();
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
        if (!canUseRedis()) throw new IllegalStateException("Redis is unavailable");
        List<String> args = new ArrayList<>();
        for (var value : values.entrySet()) { args.add(value.getKey()); args.add(value.getValue().toString()); }
        try (Jedis jedis = pool.getResource()) {
            jedis.eval("redis.call('DEL',KEYS[1]); if #ARGV>0 then redis.call('HSET',KEYS[1],unpack(ARGV)) end; return 1", List.of(key), args);
            markAvailable();
        } catch (RuntimeException failure) {
            markUnavailable();
            throw failure;
        }
    }

    public static void incrementHashIfPresent(String key, String field, long amount) {
        if (key == null || field == null || amount == 0 || !canUseRedis()) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.eval("if redis.call('EXISTS',KEYS[1])==1 then return redis.call('HINCRBY',KEYS[1],ARGV[1],ARGV[2]) end; return 0",
                List.of(key), List.of(field, Long.toString(amount)));
            markAvailable();
        } catch (RuntimeException failure) {
            markUnavailable();
            throw failure;
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

    public static boolean available() {
        return canUseRedis();
    }

    private static boolean canUseRedis() {
        return System.currentTimeMillis() >= disabledUntil;
    }

    private static void applyTtl(Jedis jedis, String key, int ttlSeconds) {
        if (ttlSeconds > 0) jedis.expire(key, ttlSeconds);
        else jedis.persist(key);
    }

    private static void markUnavailable() {
        disabledUntil = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
    }

    private static void markAvailable() {
        disabledUntil = 0;
    }
}
