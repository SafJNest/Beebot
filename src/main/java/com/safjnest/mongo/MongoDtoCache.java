package com.safjnest.mongo;

import java.util.Collection;
import java.util.List;

public interface MongoDtoCache<T> {

    T find(String key);

    List<T> findAll(Collection<String> keys);

    void upsert(T value);

    void upsertAll(Collection<T> values);

    boolean delete(String key);

    long deleteAll(Collection<String> keys);

    long clear();

    void ensureIndexes();
}
