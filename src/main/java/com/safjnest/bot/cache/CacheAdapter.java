package com.safjnest.bot.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

public abstract class CacheAdapter<K, V> {

    private final Cache<K, V> cache;
    private final Map<K, Entry<V>> active = new HashMap<>();
    private final Object monitor = new Object();
    private final int maximumSize;
    private final Duration expiration;
    private final boolean expireAfterAccess;

    protected CacheAdapter(int maximumSize, long duration, TimeUnit unit) {
        this(maximumSize, duration, unit, Ticker.systemTicker(), true);
    }

    protected CacheAdapter(int maximumSize, long duration, TimeUnit unit, Ticker ticker, boolean expireAfterAccess) {
        this.maximumSize = maximumSize;
        this.expiration = Duration.ofNanos(unit.toNanos(duration));
        this.expireAfterAccess = expireAfterAccess;

        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(maximumSize).ticker(ticker);
        if (expireAfterAccess)
            builder.expireAfterAccess(expiration);
        else
            builder.expireAfterWrite(expiration);

        this.cache = builder.build();
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        synchronized (monitor) {
            Entry<V> entry = active.get(key);

            if (entry == null)
                cache.put(key, value);
            else
                entry.replacement = value;
        }
    }

    public V get(K key) {
        synchronized (monitor) {
            Entry<V> entry = active.get(key);

            if (entry != null && entry.loaded.isDone() && !entry.loaded.isCompletedExceptionally())
                return entry.value;

            return cache.getIfPresent(key);
        }
    }

    public Collection<V> get(Collection<K> keys) {
        List<V> result = new ArrayList<>();
        for (K key : keys)
            result.add(get(key));

        return Collections.unmodifiableList(result);
    }

    public V getOrLoad(K key) {
        List<V> values = getOrLoadAll(List.of(key));

        return values.isEmpty() ? null : values.get(0);
    }

    public List<V> getOrLoadAll(Collection<K> keys) {
        return getOrLoadAll(keys, this::loadValues);
    }

    protected List<V> getOrLoadAll(Collection<K> keys, Function<List<K>, Map<K, V>> loader) {
        return withAll(keys, loader, Function.identity());
    }

    // Bulk scopes retain entries; mutations still need the individual owner's lock.
    public <R> R withAll(Collection<K> keys, Function<? super List<V>, ? extends R> operation) {
        return withAll(keys, this::loadValues, operation);
    }

    public V getOrCreate(K key, Function<? super K, ? extends V> factory) {
        return operate(key, Objects.requireNonNull(factory), Function.identity());
    }

    public <R> R with(K key, Function<? super V, ? extends R> operation) {
        return operate(key, null, operation);
    }

    public <R> R withOrCreate(K key, Function<? super K, ? extends V> factory,
            Function<? super V, ? extends R> operation) {
        return operate(key, Objects.requireNonNull(factory), operation);
    }

    public void invalidate(K key) {
        synchronized (monitor) {
            cache.invalidate(key);
            Entry<V> entry = active.get(key);

            if (entry != null) {
                entry.invalidated = true;
                entry.replacement = null;
            }
        }
    }

    public V remove(K key) {
        synchronized (monitor) {
            V value = get(key);
            invalidate(key);

            return value;
        }
    }

    public boolean contains(K key) {
        return get(key) != null;
    }

    public Collection<K> keySet() {
        return Set.copyOf(asTypedMap().keySet());
    }

    public ConcurrentMap<K, V> asTypedMap() {
        synchronized (monitor) {
            ConcurrentMap<K, V> result = new ConcurrentHashMap<>(cache.asMap());

            for (Map.Entry<K, Entry<V>> item : active.entrySet()) {
                Entry<V> entry = item.getValue();
                if (!entry.invalidated && entry.value != null)
                    result.put(item.getKey(), entry.value);
            }

            return result;
        }
    }

    public Collection<V> values() {
        return List.copyOf(asTypedMap().values());
    }

    public int getSize() {
        cache.cleanUp();

        return asTypedMap().size();
    }

    public int getMaxSize() {
        return maximumSize;
    }

    public long expiresAfter(K key) {
        Duration age = (expireAfterAccess ? cache.policy().expireAfterAccess() : cache.policy().expireAfterWrite())
            .flatMap(policy -> policy.ageOf(key)).orElse(null);

        return age == null ? 0L : Math.max(0L, expiration.minus(age).toMillis());
    }

    public void clear() {
        synchronized (monitor) {
            cache.invalidateAll();
            for (Entry<V> entry : active.values()) {
                entry.invalidated = true;
                entry.replacement = null;
            }
        }
    }

    protected Map<K, V> loadValues(Collection<K> keys) {
        return Map.of();
    }

    /* -------------------------------------------------------------------------- */

    private <R> R withAll(Collection<K> keys, Function<List<K>, Map<K, V>> loader,
            Function<? super List<V>, ? extends R> operation) {
        Objects.requireNonNull(operation);
        List<K> requested = List.copyOf(keys);
        List<K> missing = new ArrayList<>();
        Map<K, Entry<V>> entries = reserve(requested, missing);

        try {
            load(entries, missing, loader);
            List<V> result = new ArrayList<>();
            for (K key : requested) {
                V value = await(entries.get(key));
                if (value != null)
                    result.add(value);
            }

            return operation.apply(result);
        } finally {
            release(entries);
        }
    }

    private Map<K, Entry<V>> reserve(Collection<K> keys, List<K> missing) {
        Map<K, Entry<V>> result = new LinkedHashMap<>();

        synchronized (monitor) {
            for (K key : keys) {
                if (result.containsKey(key))
                    continue;

                Entry<V> entry = active.get(key);
                if (entry == null) {
                    entry = new Entry<>();
                    entry.value = cache.getIfPresent(key);
                    if (entry.value == null)
                        missing.add(key);
                    else
                        entry.loaded.complete(null);

                    active.put(key, entry);
                }

                entry.references++;
                result.put(key, entry);
            }
        }

        return result;
    }

    private void load(Map<K, Entry<V>> entries, List<K> missing) {
        load(entries, missing, this::loadValues);
    }

    private void load(Map<K, Entry<V>> entries, List<K> missing, Function<List<K>, Map<K, V>> loader) {
        if (missing.isEmpty())
            return;

        try {
            Map<K, V> values = Objects.requireNonNull(loader.apply(List.copyOf(missing)), "Loader returned null");

            synchronized (monitor) {
                for (K key : missing) {
                    Entry<V> entry = entries.get(key);
                    entry.value = values.get(key);
                    if (!entry.invalidated && entry.replacement == null && entry.value != null)
                        cache.put(key, entry.value);
                }
            }

            for (K key : missing)
                entries.get(key).loaded.complete(null);
        } catch (RuntimeException | Error exception) {
            for (K key : missing)
                entries.get(key).loaded.completeExceptionally(exception);

            throw exception;
        }
    }

    private V await(Entry<V> entry) {
        try {
            entry.loaded.join();

            return entry.value;
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause)
                throw cause;

            if (exception.getCause() instanceof Error cause)
                throw cause;

            throw exception;
        }
    }

    private <R> R operate(K key, Function<? super K, ? extends V> factory,
            Function<? super V, ? extends R> operation) {
        Objects.requireNonNull(operation);
        List<K> missing = new ArrayList<>();
        Map<K, Entry<V>> entries = reserve(List.of(key), missing);
        Entry<V> entry = entries.get(key);

        try {
            load(entries, missing);
            V value = await(entry);

            if (value == null && factory != null) {
                synchronized (entry) {
                    if (entry.value == null)
                        entry.value = Objects.requireNonNull(factory.apply(key), "Factory returned null");

                    value = entry.value;
                }
            }

            if (value == null)
                throw new NoSuchElementException("No value for cache key " + key);

            synchronized (value) {
                return operation.apply(value);
            }
        } finally {
            release(entries);
        }
    }

    private void release(Map<K, Entry<V>> entries) {
        synchronized (monitor) {
            for (Map.Entry<K, Entry<V>> item : entries.entrySet()) {
                K key = item.getKey();
                Entry<V> entry = item.getValue();
                if (--entry.references != 0)
                    continue;

                if (entry.replacement != null)
                    cache.put(key, entry.replacement);
                else if (!entry.invalidated && entry.value != null && !entry.loaded.isCompletedExceptionally()
                        && cache.getIfPresent(key) == null)
                    cache.put(key, entry.value);

                active.remove(key);
            }
        }
    }

    private static final class Entry<V> {
        private final CompletableFuture<Void> loaded = new CompletableFuture<>();
        private volatile V value;
        private int references;
        private boolean invalidated;
        private V replacement;
    }
}
