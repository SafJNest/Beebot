package com.safjnest.core.cache.managers;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.UserData;

public class UserdataCache extends CacheAdapter<String, UserData> {

    private static UserdataCache instance = new UserdataCache();

    public UserdataCache() {
        super();
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(50);
    }

    public static UserdataCache getInstance() {
        return instance;
    }

    public static void put(UserData userData) {
        instance.put(userData.getId(), userData);
    }

    public static UserData getUser(String id) {
        UserData userData = instance.get(id);
        if (userData == null) {
            userData = new UserData(id);
            put(userData);
        }
        return userData;
    }

    public ConcurrentMap<String, UserData> asTypedMap() {
        return super.asTypedMap();
    }
}
