package com.safjnest.core;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.safjnest.model.UserData;

public class UserDataHandler extends CacheHandler<String, UserData> {

    private static final String KEY = "user_data";
    private static final UserDataHandler instance = new UserDataHandler();

    public UserDataHandler() {
        setExpireTime(12, TimeUnit.HOURS);
        setTypeLimit(50);
    }

    public static void put(UserData userData) {
        instance.put(KEY + "-" + userData.getId(), userData);
    }

    public static UserData get(String id) {
        return instance.getInternal(KEY + "-" + id);
    }

    public static Map<String, UserData> asMap() {
        return instance.asMap(UserData.class);
    }

    @Override
    protected Class<UserData> getValueType() {
        return UserData.class;
    }
}
