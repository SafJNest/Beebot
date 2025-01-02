package com.safjnest.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

public abstract class CacheHandler<K, V> {
    private static final Cache<Object, Object> cache;
    private final Map<Class<?>, Integer> typeLimits;
    private final Map<Class<?>, Map<K, Integer>> typeCounts;
    private final Map<Class<?>, Long> expireTimes;

    static {
        System.out.println("CacheHandler static block");
        cache = Caffeine.newBuilder()
                .build();
    }

    public CacheHandler() {
        this.typeLimits = new LinkedHashMap<>();
        this.typeCounts = new LinkedHashMap<>();
        this.expireTimes = new LinkedHashMap<>();
    }

    // Set type limit
    protected void setTypeLimit(int limit) {
        this.typeLimits.put(getValueType(), limit);
        this.typeCounts.put(getValueType(), new LinkedHashMap<>());
    }

    // Set expiration time
    protected void setExpireTime(long duration, TimeUnit unit) {
        this.expireTimes.put(getValueType(), unit.toNanos(duration));
    }

    protected void put(K key, V value) {
        Class<?> type = value.getClass();
        Map<K, Integer> countMap = typeCounts.computeIfAbsent(type, k -> new LinkedHashMap<>());
        int limit = typeLimits.getOrDefault(type, Integer.MAX_VALUE);
        if (countMap.size() >= limit) {
            removeOldest(countMap);
        }
        cache.put(key, value);
        countMap.put(key, 1);
    }
    
    private void removeOldest(Map<K, Integer> countMap) {
        K oldestKey = countMap.keySet().iterator().next();
        cache.invalidate(oldestKey);
        countMap.remove(oldestKey);
    }

    // Get object from cache
    protected V getInternal(K key) {
        Object value = cache.getIfPresent(key);
        if (value != null && getValueType().isInstance(value)) {
            return getValueType().cast(value);
        }
        return null;
    }

    // Invalidate an object
    protected void invalidate(K key) {
        cache.invalidate(key);
    }

    // Invalidate all objects
    protected void invalidateAll() {
        cache.invalidateAll();
    }

    // Check if an object exists
    protected boolean contains(K key) {
        return cache.getIfPresent(key) != null;
    }

    // Get all objects of a specific type
    @SuppressWarnings("unchecked")
    protected Map<K, V> asMap(Class<V> type) {
        HashMap<K, V> map = new HashMap<>();
        cache.asMap().forEach((key, value) -> {
            if (type.isInstance(value)) {
                map.put((K) key, (V) value);
            }
        });
        return map;
    }

    protected abstract Class<V> getValueType();
}