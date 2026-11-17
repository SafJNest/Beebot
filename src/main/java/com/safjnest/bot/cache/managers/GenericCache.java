package com.safjnest.bot.cache.managers;

import java.util.concurrent.TimeUnit;

import com.safjnest.bot.cache.CacheAdapter;

public class GenericCache<K, V> extends CacheAdapter<K, V> {

    public GenericCache(int size, long duration, TimeUnit unit, Class<K> key, Class<V> value) {
        super(size, duration, unit);
    }

}
