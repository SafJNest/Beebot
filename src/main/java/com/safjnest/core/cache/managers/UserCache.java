package com.safjnest.core.cache.managers;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.UserData;

public class UserCache extends CacheAdapter<String, UserData> {

    private static UserCache instance = new UserCache();

    public UserCache() {
        super();
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(50);
    }

    public static UserCache getInstance() {
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
