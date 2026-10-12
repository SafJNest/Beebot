package com.safjnest.lol.service;

import com.safjnest.r4j.RiotGateway;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.lol.*;
import com.safjnest.lol.queue.QueueHandler;
import com.hive.job.ResultType;
import com.safjnest.r4j.scheduler.RiotScheduler;
import com.hive.job.JobPriority;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

public final class MasteryService {

    private static final TypeReference<List<Mastery>> MASTERIES_TYPE = new TypeReference<List<Mastery>>() {};
    private static final ProfileRecordService PROFILE_RECORD_SERVICE = new ProfileRecordService();

    private MasteryService() {
    }

    public static List<Mastery> find(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        List<Mastery> cached = cache(puuid, shard);
        if (cached != null) return cached;

        List<Mastery> stored = query(puuid, shard);
        if (stored != null) RedisClient.set(RedisKey.SUMMONER_MASTERIES, stored, LeagueShard.cacheRegion(shard), shard.name(), puuid);
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

    public static Mastery getByChampion(String puuid, LeagueShard shard, int championId) {
        for (Mastery mastery : get(puuid, shard)) {
            if (mastery.championId() == championId) return mastery;
        }
        return null;
    }

    public static CompletableFuture<List<Mastery>> refreshAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, JobPriority.IMMEDIATE);
    }

    public static CompletableFuture<List<Mastery>> refreshBackgroundAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, JobPriority.BACKGROUND);
    }

    // ============================================================================

    private static List<Mastery> cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.SUMMONER_MASTERIES.of(LeagueShard.cacheRegion(shard), shard.name(), puuid), MASTERIES_TYPE);
    }

    private static List<Mastery> query(String puuid, LeagueShard shard) {
        return MongoDB.findMasteries(puuid, shard);
    }

    private static List<Mastery> cacheRiotMasteries(String puuid, LeagueShard shard) {
        return RiotGateway.cachedMasteries(shard, puuid);
    }

    private static CompletableFuture<List<Mastery>> refreshAsync(
            String puuid,
            LeagueShard shard,
            JobPriority priority) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return QueueHandler.thenApply(refreshRiotMasteriesFromRiotAsync(puuid, shard, priority), entries -> {
            List<Mastery> masteries = toMasteries(entries);
            save(puuid, shard, masteries, false);
            return masteries;
        });
    }

    private static CompletableFuture<List<Mastery>> fetch(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return QueueHandler.thenCompose(SummonerService.getSummonerAsync(puuid, shard), summoner -> {
            if (summoner == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Summoner is not available for mastery persistence"));
            return QueueHandler.thenApply(getRiotMasteriesAsync(puuid, shard), entries -> {
                List<Mastery> masteries = toMasteries(entries);
                save(puuid, shard, masteries, true);
                return masteries;
            });
        });
    }

    private static CompletableFuture<List<Mastery>> getRiotMasteriesAsync(String puuid, LeagueShard shard) {
        List<Mastery> cached = cacheRiotMasteries(puuid, shard);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return getRiotMasteriesFromRiotAsync(puuid, shard);
    }

    private static CompletableFuture<List<Mastery>> getRiotMasteriesFromRiotAsync(String puuid, LeagueShard shard) {
        return fetchRiotMasteriesAsync(puuid, shard, "masteries");
    }

    private static CompletableFuture<List<Mastery>> refreshRiotMasteriesFromRiotAsync(
            String puuid,
            LeagueShard shard,
            JobPriority priority) {
        return fetchRiotMasteriesAsync(puuid, shard, "masteries-refresh", priority);
    }

    private static CompletableFuture<List<Mastery>> fetchRiotMasteriesAsync(
        String puuid,
        LeagueShard shard,
        String operation
    ) {
        return fetchRiotMasteriesAsync(puuid, shard, operation, JobPriority.IMMEDIATE);
    }

    private static CompletableFuture<List<Mastery>> fetchRiotMasteriesAsync(
        String puuid,
        LeagueShard shard,
        String operation,
        JobPriority priority
    ) {
        return switch (priority) {
            case IMMEDIATE -> QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ':' + operation + ':' + puuid,
                operation + " puuid=" + puuid, ResultType.list(Mastery.class), ignored -> fetchMasteries(shard, puuid));
            case NORMAL -> QueueHandler.normal(RiotScheduler.class, shard, shard.name() + ':' + operation + ':' + puuid,
                operation + " puuid=" + puuid, ResultType.list(Mastery.class), ignored -> fetchMasteries(shard, puuid));
            case BACKGROUND -> QueueHandler.background(RiotScheduler.class, shard, shard.name() + ':' + operation + ':' + puuid,
                operation + " puuid=" + puuid, ResultType.list(Mastery.class), ignored -> fetchMasteries(shard, puuid));
        };
    }

    private static List<Mastery> fetchMasteries(LeagueShard shard, String puuid) {
            List<Mastery> masteries = RiotGateway.masteries(shard, puuid);
            if (masteries == null) throw new IllegalStateException("Riot returned no mastery result");
            return masteries;
    }

    private static void save(String puuid, LeagueShard shard, List<Mastery> masteries, boolean invalidateProfile) {
        if (!valid(puuid, shard) || masteries == null) return;
        if (!MongoDB.upsertMasteries(puuid, shard, masteries)) return;
        if (!PROFILE_RECORD_SERVICE.updateMasteries(puuid, shard, masteries)) {
            throw new IllegalStateException("Mastery records could not be updated");
        }
        RedisClient.set(RedisKey.SUMMONER_MASTERIES, masteries, LeagueShard.cacheRegion(shard), shard.name(), puuid);
        if (invalidateProfile) ProfileService.invalidate(puuid, shard);
    }
    private static List<Mastery> toMasteries(List<Mastery> entries) { return entries == null ? new ArrayList<>() : new ArrayList<>(entries); }
}
