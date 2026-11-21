package com.safjnest.bot.cache.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.safjnest.bot.cache.CacheAdapter;
import com.safjnest.bot.model.UserData;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;

public class UserCache extends CacheAdapter<String, UserData> {

    private static final UserCache instance = new UserCache();

    private final BotDB database;

    public UserCache() {
        this(BotDB.get());
    }

    public UserCache(BotDB database) {
        super(2000, 12, TimeUnit.HOURS);

        this.database = database;
    }

    public static UserCache getInstance() {
        return instance;
    }

    public static void put(UserData userData) {
        instance.put(userData.getId(), userData);
    }

    public static UserData getUser(String id) {
        return instance.getOrLoad(id);
    }

    public static List<UserData> getUsers(Collection<String> ids) {
        return instance.getOrLoadAll(ids);
    }

    public static <R> R withUser(String id, Function<? super UserData, ? extends R> operation) {
        return instance.with(id, operation);
    }

    public static <R> R withUsers(Collection<String> ids, Function<? super List<UserData>, ? extends R> operation) {
        return instance.withAll(ids, operation);
    }

    @Override
    protected Map<String, UserData> loadValues(Collection<String> ids) {
        Map<String, List<QueryRecord>> aliases = new HashMap<>();

        for (QueryRecord row : database.readUserAliases(ids)) {
            aliases.computeIfAbsent(row.get("user_id"), key -> new ArrayList<>()).add(row);
        }

        Map<String, UserData> result = new LinkedHashMap<>();

        for (String id : ids)
            result.put(id, UserData.fromRows(id, aliases.getOrDefault(id, List.of()), database));

        return result;
    }
}
