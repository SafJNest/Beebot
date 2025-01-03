package com.safjnest.core.cache.managers;

import java.util.concurrent.TimeUnit;

import com.safjnest.core.cache.CacheAdapter;

public class GenericCache<K, V> extends CacheAdapter<K, V> {

    public GenericCache(int size, long duration, TimeUnit unit, Class<K> key, Class<V> value) {
        super(key, value);
        setExpireTime(duration, unit);
        setTypeLimit(size);
    }

    public void put(K key, V value) {
        super.put(key, value);
    }

    public V get(K key) {
        return super.get(key);
    }

    public V remove(K key) {
        return super.remove(key);
    }

    public boolean contains(K key) {
        return super.contains(key);
    }

    public int getSize() {
        return super.getSize();
    }
}
