package com.safjnest.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.HashMap;

public abstract class AbstractCache<K, V> {
    private static final Cache<Object, Object> cache;

    protected static final Map<Class<?>, Integer> typeLimits = new HashMap<>();
    protected static final Map<Class<?>, Map<Object, Integer>> typeCounts = new HashMap<>();
    protected static final Map<Class<?>, Long> expireTimes = new HashMap<>();

    static {
        cache = Caffeine.newBuilder()
                        .expireAfter(new Expiry<Object, Object>() {
                            @Override
                            public long expireAfterCreate(Object key, Object value, long currentTime) {
                                Class<?> type = value.getClass();
                                return expireTimes.getOrDefault(type, TimeUnit.MINUTES.toNanos(10));
                            }

                            @Override
                            public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                                return currentDuration;
                            }

                            @Override
                            public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                                Class<?> type = value.getClass();
                                return expireTimes.getOrDefault(type, TimeUnit.MINUTES.toNanos(10));
                            }
                        })
                .build();
    }

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
        cache.put(key, value);
        countMap.put(key, 1);
    }
    
    private void removeOldest(Map<K, Integer> countMap) {
        K oldestKey = countMap.keySet().iterator().next();
        cache.invalidate(oldestKey);
        countMap.remove(oldestKey);
    }

    protected V get(K key) {
        Object value = cache.getIfPresent(key);
        if (value != null && getValueType().isInstance(value)) {
            return getValueType().cast(value);
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

    @SuppressWarnings("unchecked")
    protected Collection<K> keySet() {
        return cache.asMap().entrySet().stream()
                .filter(entry -> getValueType().isInstance(entry.getValue()))
                .map(entry -> (K) entry.getKey())
                .toList();
    }

    protected void invalidate(K key) {
        cache.invalidate(key);
    }

    protected V remove(K key) {
        Object value = cache.getIfPresent(key);
        if (value != null && getValueType().isInstance(value)) {
            cache.invalidate(key);
            return getValueType().cast(value);
        }
        return null;
    }

    public Collection<V> values() {
        return cache.asMap().values().stream()
                .filter(getValueType()::isInstance)
                .map(getValueType()::cast)
                .toList();
    }

    protected void invalidateAll() {
        cache.invalidateAll();
    }

    protected boolean contains(K key) {
        return cache.getIfPresent(key) != null;
    }

    protected long expiresAfter(K key) {
        return cache.policy().expireVariably()
                .flatMap(policy -> {
                    var duration = policy.getExpiresAfter(key);
                    return duration.map(d -> d.toMillis());
                })
                .orElse(0L);
    }

    protected int getTypeLimit(Class<?> type) {
        return typeLimits.getOrDefault(type, Integer.MAX_VALUE);
    }

    protected int getTypeSize(Class<?> type) {
        return typeCounts.getOrDefault(type, new HashMap<>()).size();
    }

    protected ConcurrentMap<Object,Object> asMap() {
        return cache.asMap();
    }

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

    protected abstract Class<K> getKeyType();
    protected abstract Class<V> getValueType();

}