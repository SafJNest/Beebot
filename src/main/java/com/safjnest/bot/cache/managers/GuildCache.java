package com.safjnest.bot.cache.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.safjnest.bot.Bot;
import com.safjnest.bot.cache.CacheAdapter;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.status.log.BotLogger;
import com.safjnest.status.log.LoggerIDpair;

import net.dv8tion.jda.api.entities.Guild;

public class GuildCache extends CacheAdapter<String, GuildData> {

    private static final GuildCache instance = new GuildCache();

    public GuildCache() {
        super(60, 12, TimeUnit.HOURS);
    }

    public static GuildCache getInstance() {
        return instance;
    }

    public static GuildData getGuild(Guild guild) {
        return getGuild(guild.getId());
    }

    public static GuildData getGuild(String id) {
        return instance.getOrLoad(id);
    }

    public static List<GuildData> getGuilds(Collection<String> ids) {
        return instance.getOrLoadAll(ids);
    }

    public static GuildData getGuildOrPut(Guild guild) {
        return getGuildOrPut(guild.getId());
    }

    public static GuildData getGuildOrPut(String id) {
        return instance.getOrCreate(id, GuildCache::createGuild);
    }

    public static GuildData putGuild(String id) {
        return getGuildOrPut(id);
    }

    public static <R> R withGuild(String id, Function<? super GuildData, ? extends R> operation) {
        return instance.with(id, operation);
    }

    public static <R> R withGuilds(Collection<String> ids, Function<? super List<GuildData>, ? extends R> operation) {
        return instance.withAll(ids, operation);
    }

    public static <R> R withGuildOrPut(String id, Function<? super GuildData, ? extends R> operation) {
        return instance.withOrCreate(id, GuildCache::createGuild, operation);
    }

    public void retrieveAllGuilds() {
        List<QueryRecord> rows = BotDB.getGuildData();
        Map<String, QueryRecord> byId = new LinkedHashMap<>();

        for (QueryRecord row : rows)
            byId.put(row.get("guild_id"), row);

        getOrLoadAll(byId.keySet(), missing -> {
            List<QueryRecord> absent = new ArrayList<>();
            for (String id : missing)
                absent.add(byId.get(id));

            return hydrate(absent);
        });
    }

    public ConcurrentMap<String, GuildData> getGuilds() {
        return asTypedMap();
    }

    public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
    }

    @Override
    protected Map<String, GuildData> loadValues(Collection<String> ids) {
        return hydrate(BotDB.getGuildData(ids));
    }

    /* -------------------------------------------------------------------------- */

    private static GuildData createGuild(String id) {
        if (!BotDB.insertGuild(id, Bot.getPrefix()))
            throw new IllegalStateException("Failed to create guild " + id);

        BotLogger.error("Missing guild in database => {0}", new LoggerIDpair(id, LoggerIDpair.IDType.GUILD));

        return new GuildData(id);
    }

    private Map<String, GuildData> hydrate(List<QueryRecord> rows) {
        if (rows.isEmpty())
            return Map.of();

        List<String> ids = new ArrayList<>();
        for (QueryRecord row : rows)
            ids.add(row.get("guild_id"));

        Map<String, List<QueryRecord>> channels = group(BotDB.getChannelData(ids));
        Map<String, List<QueryRecord>> actions = group(BotDB.getWarnings(ids));
        Map<String, GuildData> result = new LinkedHashMap<>();

        for (QueryRecord row : rows) {
            String id = row.get("guild_id");
            result.put(id, GuildData.fromRows(row, channels.getOrDefault(id, List.of()), actions.getOrDefault(id, List.of())));
        }

        return result;
    }

    private Map<String, List<QueryRecord>> group(List<QueryRecord> rows) {
        Map<String, List<QueryRecord>> result = new HashMap<>();

        for (QueryRecord row : rows)
            result.computeIfAbsent(row.get("guild_id"), key -> new ArrayList<>()).add(row);

        return result;
    }
}
