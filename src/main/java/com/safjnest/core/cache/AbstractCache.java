package com.safjnest.core.cache;

import redis.clients.jedis.Jedis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCache<K, V> {
    
    private static final RedisManager redisManager = RedisManager.getInstance();

    protected static final Map<Class<?>, Integer> typeLimits = new HashMap<>();
    protected static final Map<Class<?>, Map<Object, Integer>> typeCounts = new HashMap<>();
    protected static final Map<Class<?>, Long> expireTimes = new HashMap<>();

    protected void setTypeLimit(int limit) {
        typeLimits.put(getValueType(), limit);
        typeCounts.put(getValueType(), new LinkedHashMap<>());
    }

    protected void setExpireTime(long duration, TimeUnit unit) {
        expireTimes.put(getValueType(), unit.toNanos(duration));
    }



    @SuppressWarnings("unchecked")
    protected void put(K key, V value) {
        Class<?> type = value.getClass();
        Map<K, Integer> countMap = (Map<K, Integer>) typeCounts.computeIfAbsent(type, k -> new LinkedHashMap<>());
        int limit = typeLimits.getOrDefault(type, Integer.MAX_VALUE);
        if (countMap.size() >= limit) {
            removeOldest(countMap);
        }
        
        String keyStr = key.toString();
        String serializedValue = SerializationUtils.serialize(value);
        
        try (Jedis jedis = redisManager.getResource()) {
            // Set value with TTL if configured
            Long ttlNanos = expireTimes.get(type);
            if (ttlNanos != null) {
                int ttlSeconds = (int) TimeUnit.NANOSECONDS.toSeconds(ttlNanos);
                if (ttlSeconds > 0) {
                    jedis.setex(keyStr, ttlSeconds, serializedValue);
                } else {
                    jedis.set(keyStr, serializedValue);
                }
            } else {
                jedis.set(keyStr, serializedValue);
            }
        } catch (Exception e) {
            // Log error but don't fail - could fall back to in-memory if needed
            System.err.println("Redis operation failed: " + e.getMessage());
        }
        
        countMap.put(key, 1);
    }
    

    protected V get(K key) {
        String keyStr = key.toString();
        
        try (Jedis jedis = redisManager.getResource()) {
            String serializedValue = jedis.get(keyStr);
            if (serializedValue != null) {
                return SerializationUtils.deserialize(serializedValue, getValueType());
            }
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Redis get operation failed: " + e.getMessage());
        }
        return null;
    }

    protected Collection<V> get(Collection<K> keys) {
        return keys.stream()
                .map(key -> {
                    V value = get(key);
                    return value;
                })
                .filter(value -> value != null)
                .toList();
    }

    protected boolean contains(K key) {
        String keyStr = key.toString();
        try (Jedis jedis = redisManager.getResource()) {
            return jedis.exists(keyStr);
        } catch (Exception e) {
            System.err.println("Redis exists operation failed: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    protected Collection<K> keySet() {
        List<K> keys = new ArrayList<>();
        String pattern = "*"; // Get all keys, could be refined with type-specific patterns
        
        try (Jedis jedis = redisManager.getResource()) {
            Set<String> keyStrings = jedis.keys(pattern);
            for (String keyStr : keyStrings) {
                String value = jedis.get(keyStr);
                if (value != null) {
                    try {
                        V deserializedValue = SerializationUtils.deserialize(value, getValueType());
                        if (deserializedValue != null) {
                            // Try to convert string key back to K type
                            keys.add((K) keyStr);
                        }
                    } catch (Exception e) {
                        // Skip invalid entries
                    }
                }
            }
        }
        return keys;
    }

    public Collection<V> values() {
        List<V> values = new ArrayList<>();
        String pattern = "*";
        
        try (Jedis jedis = redisManager.getResource()) {
            Set<String> keyStrings = jedis.keys(pattern);
            for (String keyStr : keyStrings) {
                String serializedValue = jedis.get(keyStr);
                if (serializedValue != null) {
                    try {
                        V value = SerializationUtils.deserialize(serializedValue, getValueType());
                        if (value != null) {
                            values.add(value);
                        }
                    } catch (Exception e) {
                        // Skip invalid entries
                    }
                }
            }
        }
        return values;
    }




    protected void invalidate(K key) {
        String keyStr = key.toString();
        try (Jedis jedis = redisManager.getResource()) {
            jedis.del(keyStr);
        } catch (Exception e) {
            System.err.println("Redis delete operation failed: " + e.getMessage());
        }
        
        // Also remove from count map
        for (Map<Object, Integer> countMap : typeCounts.values()) {
            countMap.remove(key);
        }
    }

    protected void invalidateAll() {
        try (Jedis jedis = redisManager.getResource()) {
            jedis.flushDB();
        } catch (Exception e) {
            System.err.println("Redis flush operation failed: " + e.getMessage());
        }
        
        // Clear all count maps
        for (Map<Object, Integer> countMap : typeCounts.values()) {
            countMap.clear();
        }
    }

    protected V remove(K key) {
        String keyStr = key.toString();
        V value = null;
        
        try (Jedis jedis = redisManager.getResource()) {
            String serializedValue = jedis.get(keyStr);
            if (serializedValue != null) {
                value = SerializationUtils.deserialize(serializedValue, getValueType());
                jedis.del(keyStr);
            }
        }
        
        // Remove from count map
        for (Map<Object, Integer> countMap : typeCounts.values()) {
            countMap.remove(key);
        }
        
        return value;
    }




    protected ConcurrentMap<Object,Object> asMap() {
        ConcurrentMap<Object, Object> map = new ConcurrentHashMap<>();
        String pattern = "*";
        
        try (Jedis jedis = redisManager.getResource()) {
            Set<String> keyStrings = jedis.keys(pattern);
            for (String keyStr : keyStrings) {
                String serializedValue = jedis.get(keyStr);
                if (serializedValue != null) {
                    try {
                        Object value = SerializationUtils.deserialize(serializedValue, Object.class);
                        if (value != null) {
                            map.put(keyStr, value);
                        }
                    } catch (Exception e) {
                        // Skip invalid entries
                    }
                }
            }
        }
        return map;
    }

    private void removeOldest(Map<K, Integer> countMap) {
        K oldestKey = countMap.keySet().iterator().next();
        String keyStr = oldestKey.toString();
        try (Jedis jedis = redisManager.getResource()) {
            jedis.del(keyStr);
        }
        countMap.remove(oldestKey);
    }




    protected int getTypeLimit(Class<?> type) {
        return typeLimits.getOrDefault(type, Integer.MAX_VALUE);
    }

    protected int getTypeSize(Class<?> type) {
        return typeCounts.getOrDefault(type, new HashMap<>()).size();
    }

    protected long expiresAfter(K key) {
        String keyStr = key.toString();
        try (Jedis jedis = redisManager.getResource()) {
            Long ttl = jedis.ttl(keyStr);
            if (ttl != null && ttl > 0) {
                return ttl * 1000; // Convert seconds to milliseconds
            }
        }
        return 0L;
    }



    protected abstract Class<K> getKeyType();
    protected abstract Class<V> getValueType();

}