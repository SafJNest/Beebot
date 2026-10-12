package com.safjnest.lol.service;

import com.safjnest.r4j.RiotGateway;
import java.util.Map;
import java.util.function.Supplier;

import com.R4B.api.dto.riot.Item;
import com.R4B.api.dto.riot.StaticChampion;
import com.R4B.api.dto.riot.StaticSummonerSpell;
import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.JsonCodec;

public final class StaticDataService {

    private static final TypeReference<Map<Integer, Item>> ITEMS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<Integer, StaticChampion>> CHAMPIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<Integer, StaticSummonerSpell>> SUMMONER_SPELLS_TYPE = new TypeReference<>() {};

    private StaticDataService() {
    }

    public static Map<Integer, Item> getItems() {
        return get(RedisKey.DDRAGON_ITEMS, ITEMS_TYPE, () -> RiotGateway.items());
    }

    public static Item getItem(int id) {
        return getItems().get(id);
    }

    public static Map<Integer, StaticChampion> getChampions() {
        return get(RedisKey.DDRAGON_CHAMPIONS, CHAMPIONS_TYPE,
            () -> RiotGateway.champions());
    }

    public static StaticChampion getChampion(int id) {
        return getChampions().get(id);
    }

    public static Map<Integer, StaticSummonerSpell> getSummonerSpells() {
        return get(RedisKey.DDRAGON_SUMMONER_SPELLS, SUMMONER_SPELLS_TYPE,
            () -> RiotGateway.summonerSpells());
    }

    public static StaticSummonerSpell getSummonerSpell(int id) {
        return getSummonerSpells().get(id);
    }

    public static String getText(RedisKey key, String version, Supplier<String> loader) {
        String redisKey = key.of(version);
        String cached = RedisClient.get(redisKey);
        if (cached != null) return cached;

        String value = loader.get();
        if (value != null) RedisClient.setCached(key, value, version);
        return value;
    }

    // ============================================================================

    private static <T> T get(RedisKey key, TypeReference<T> type, DataLoader<T> loader) {
        return get(key, PatchUtils.getPatch(), type, loader);
    }

    private static <T> T get(RedisKey key, String version, TypeReference<T> type, DataLoader<T> loader) {
        String redisKey = key.of(version);
        T cached = RedisClient.get(redisKey, type);
        if (cached != null) return cached;

        T data = loader.load();
        if (data != null) RedisClient.setCached(key, JsonCodec.toJson(data), version);
        return data;
    }

    @FunctionalInterface
    private interface DataLoader<T> {
        T load();
    }
}
