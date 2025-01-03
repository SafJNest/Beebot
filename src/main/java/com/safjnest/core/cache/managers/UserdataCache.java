package com.safjnest.core.cache.managers;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.UserData;

public class UserdataCache extends CacheAdapter<String, UserData> {

    public UserdataCache() {
        super();
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(50);
    }

    public void put(UserData userData) {
        super.put(userData.getId(), userData);
    }

    public UserData getUser(String id) {
        return super.get(id);
    }

    public ConcurrentMap<String, UserData> asTypedMap() {
        return super.asTypedMap();
    }
}
