package com.safjnest.core.cache;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public abstract class CacheAdapter<K, V> extends AbstractCache<K, V> {

    protected String PREFIX;

    protected Class<K> key;
    protected Class<V> value;

    protected CacheAdapter() {
        key = getKeyType();
        value = getValueType();

        this.PREFIX = key.getName();
    }

    protected CacheAdapter(Class<K> key, Class<V> value) {
        this.key = key;
        this.value = value;
        this.PREFIX = key.getName();
    }

    @SuppressWarnings("unchecked")
    protected K getPrefixedKey(K k) {
        return (K) (PREFIX + "-" + k);
    }

    

    @Override
    public void put(K key, V value) {
        super.put(getPrefixedKey(key), value);
    }

    @Override
    public V get(K key) {
        return super.get(getPrefixedKey(key));
    }

    @Override
    public Collection<V> get(Collection<K> keys) {
        return keys.stream()
            .map(this::get)
            .toList();
    }

    @Override
    public void invalidate(K key) {
        super.invalidate(getPrefixedKey(key));
    }

    @Override
    public V remove(K key) {
        return super.remove(getPrefixedKey(key));
    }

    public boolean contains(K key) {
        return super.get(getPrefixedKey(key)) != null;
    }


    @SuppressWarnings("unchecked")
    public Collection<K> keySet() {
        return super.keySet().stream()
            .map(k -> (K) k.toString().substring(PREFIX.length() + 1))
            .toList();
    }

    @SuppressWarnings("unchecked")
    public ConcurrentMap<K,V> asTypedMap() {
        ConcurrentMap<K,V> map = new ConcurrentHashMap<>();
        super.asMap().forEach((key, value) -> {
            if (getValueType().isInstance(value)) {
                map.put((K) key, (V) value);
            }
        });
        return map;
    }


    @Override
    public long expiresAfter(K key) {
        return super.expiresAfter(getPrefixedKey(key));
    }

    public int getSize() {
        return super.getTypeSize(getValueType());
    }

    public int getMaxSize() {
        return super.getTypeLimit(getValueType());
    }


    
    
    @SuppressWarnings("unchecked")
    protected Class<K> getKeyType() {
        if (key != null) 
            return key;
        key = (Class<K>) ((ParameterizedType) getClass()
            .getGenericSuperclass())
            .getActualTypeArguments()[0];
        return key;
    }

    @SuppressWarnings("unchecked")
    protected Class<V> getValueType() {
        if (value != null) 
            return value;
        value = (Class<V>) ((ParameterizedType) getClass()
            .getGenericSuperclass())
            .getActualTypeArguments()[1];
        return value;
    }

}

