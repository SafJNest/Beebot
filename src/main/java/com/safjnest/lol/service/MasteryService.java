package com.safjnest.lol.service;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;

public final class MasteryService {

    private static final TypeReference<List<Mastery>> MASTERIES_TYPE = new TypeReference<List<Mastery>>() {};
    private static final TypeReference<List<ChampionMastery>> RIOT_MASTERIES_TYPE =
        new TypeReference<List<ChampionMastery>>() {};

    private static final no.stelar7.api.r4j.impl.R4J RIOT_API = com.safjnest.lol.LeagueHandler.getRiotApi();

    private MasteryService() {
    }

    public static List<Mastery> find(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        List<Mastery> cached = cache(puuid, shard);
        if (cached != null) return cached;

        List<Mastery> stored = query(puuid, shard);
        if (stored != null) RedisClient.set(RedisKey.SUMMONER_MASTERIES, stored, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
        return stored;
    }

    public static CompletableFuture<List<Mastery>> getAsync(String puuid, LeagueShard shard) {
        List<Mastery> saved = find(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetch(puuid, shard);
    }

    public static List<Mastery> get(String puuid, LeagueShard shard) {
        try {
            return getAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static CompletableFuture<List<Mastery>> refreshAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, R4JQueue.Priority.HIGH);
    }

    public static CompletableFuture<List<Mastery>> refreshBackgroundAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, R4JQueue.Priority.LOW);
    }

    private static CompletableFuture<List<Mastery>> refreshAsync(
            String puuid,
            LeagueShard shard,
            R4JQueue.Priority priority) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return refreshRiotMasteriesFromRiotAsync(puuid, shard, priority).thenApplyAsync(entries -> {
            List<Mastery> masteries = toMasteries(entries);
            save(puuid, shard, masteries, false);
            return masteries;
        });
    }

    public static Mastery getByChampion(String puuid, LeagueShard shard, int championId) {
        for (Mastery mastery : get(puuid, shard)) {
            if (mastery.championId() == championId) return mastery;
        }
        return null;
    }

    // ============================================================================

    private static CompletableFuture<List<Mastery>> fetch(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return SummonerService.getAsync(puuid, shard).thenCompose(summoner -> {
            if (summoner == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Summoner is not available for mastery persistence"));
            return getRiotMasteriesAsync(puuid, shard).thenApplyAsync(entries -> {
                List<Mastery> masteries = toMasteries(entries);
                save(puuid, shard, masteries, true);
                return masteries;
            });
        });
    }

    private static CompletableFuture<List<ChampionMastery>> getRiotMasteriesAsync(String puuid, LeagueShard shard) {
        List<ChampionMastery> cached = cacheRiotMasteries(puuid, shard);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return getRiotMasteriesFromRiotAsync(puuid, shard);
    }

    private static CompletableFuture<List<ChampionMastery>> getRiotMasteriesFromRiotAsync(String puuid, LeagueShard shard) {
        return fetchRiotMasteriesAsync(puuid, shard, "masteries");
    }

    private static CompletableFuture<List<ChampionMastery>> refreshRiotMasteriesFromRiotAsync(String puuid, LeagueShard shard) {
        return refreshRiotMasteriesFromRiotAsync(puuid, shard, R4JQueue.Priority.HIGH);
    }

    private static CompletableFuture<List<ChampionMastery>> refreshRiotMasteriesFromRiotAsync(
            String puuid,
            LeagueShard shard,
            R4JQueue.Priority priority) {
        return fetchRiotMasteriesAsync(puuid, shard, "masteries-refresh", priority);
    }

    private static CompletableFuture<List<ChampionMastery>> fetchRiotMasteriesAsync(
        String puuid,
        LeagueShard shard,
        String operation
    ) {
        return fetchRiotMasteriesAsync(puuid, shard, operation, R4JQueue.Priority.HIGH);
    }

    private static CompletableFuture<List<ChampionMastery>> fetchRiotMasteriesAsync(
        String puuid,
        LeagueShard shard,
        String operation,
        R4JQueue.Priority priority
    ) {
        return R4JQueue.<List<ChampionMastery>>submit(shard, operation, puuid, priority, () -> {
            List<ChampionMastery> masteries = RIOT_API.getLoLAPI().getMasteryAPI().getChampionMasteries(shard, puuid);
            if (masteries == null) throw new IllegalStateException("Riot returned no mastery result");
            RedisClient.set(RedisKey.R4J_CHAMPION_MASTERIES, masteries, shard.name(), puuid);
            return masteries;
        });
    }

    private static List<Mastery> cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.SUMMONER_MASTERIES.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid), MASTERIES_TYPE);
    }

    private static List<Mastery> query(String puuid, LeagueShard shard) {
        return MongoDB.findMasteries(puuid, shard);
    }

    private static List<ChampionMastery> cacheRiotMasteries(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.R4J_CHAMPION_MASTERIES.of(shard.name(), puuid), RIOT_MASTERIES_TYPE);
    }

    private static void save(String puuid, LeagueShard shard, List<Mastery> masteries) {
        save(puuid, shard, masteries, true);
    }

    private static void save(String puuid, LeagueShard shard, List<Mastery> masteries, boolean invalidateProfile) {
        if (!valid(puuid, shard) || masteries == null) return;
        MongoDB.upsertMasteries(puuid, shard, masteries);
        RedisClient.set(RedisKey.SUMMONER_MASTERIES, masteries, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
        if (invalidateProfile) ProfileService.invalidate(puuid, shard);
    }

    private static List<Mastery> toMasteries(List<ChampionMastery> entries) {
        List<Mastery> masteries = new ArrayList<>();
        if (entries == null) return masteries;

        for (ChampionMastery entry : entries) {
            if (entry != null) masteries.add(new Mastery(
                entry.getChampionId(), entry.getChampionLevel(), entry.getChampionPoints()));
        }
        return masteries;
    }

}
