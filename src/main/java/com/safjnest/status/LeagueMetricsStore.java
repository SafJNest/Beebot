package com.safjnest.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.status.LeagueMetrics;
import com.safjnest.nosql.MongoDB;
import com.safjnest.lol.service.AggregateService;
import com.safjnest.redis.RedisKey;

public final class LeagueMetricsStore {

    private static final AtomicLong GAMES_ANALYZED = new AtomicLong();
    private static final AtomicLong TOTAL_SUMMONERS = new AtomicLong();
    private static final AtomicLong TOTAL_MASTERIES = new AtomicLong();
    private static final AtomicLong TRACKED_SUMMONERS = new AtomicLong();
    private static final AtomicReference<Map<String, Long>> RANKS_BY_QUEUE = new AtomicReference<>(Map.of());

    private LeagueMetricsStore() {}

    public static void seed() {
        refresh();
    }

    public static void refresh() {
        refreshLong(RedisKey.STATUS_GAMES_ANALYZED, GAMES_ANALYZED, MongoDB::estimatedMatchCount);
        refreshLong(RedisKey.STATUS_TOTAL_SUMMONERS, TOTAL_SUMMONERS, MongoDB::estimatedSummonerCount);
        refreshLong(RedisKey.STATUS_TOTAL_MASTERIES, TOTAL_MASTERIES, MongoDB::totalMasteriesCount);
        refreshLong(RedisKey.STATUS_TRACKED_SUMMONERS, TRACKED_SUMMONERS, LeagueMetricsStore::trackedSummonerSource);
        refreshRanksByQueue();
    }

    public static LeagueMetrics snapshot() {
        return new LeagueMetrics(
            GAMES_ANALYZED.get(),
            TOTAL_SUMMONERS.get(),
            TOTAL_MASTERIES.get(),
            RANKS_BY_QUEUE.get()
        );
    }

    public static Object calculate(RedisKey key) {
        return switch (key) {
            case STATUS_GAMES_ANALYZED -> MongoDB.estimatedMatchCount();
            case STATUS_TOTAL_SUMMONERS -> MongoDB.estimatedSummonerCount();
            case STATUS_TOTAL_MASTERIES -> MongoDB.totalMasteriesCount();
            case STATUS_TRACKED_SUMMONERS -> trackedSummonerSource();
            case STATUS_RANKS_BY_QUEUE -> MongoDB.rankTotalsByQueue();
            default -> throw new IllegalArgumentException("Unsupported metric aggregate: " + key);
        };
    }

    public static long trackedSummoners() {
        return TRACKED_SUMMONERS.get();
    }

    // ============================================================================

    private static long trackedSummonerSource() {
        return MongoDB.findTrackedSummonerModels().size();
    }

    private static void refreshLong(RedisKey key, AtomicLong target, LongSupplier source) {
        try {
            long value = AggregateService.get(key, Long.class, () -> Math.max(0, source.getAsLong()));
            target.set(value);
        } catch (Exception ignored) {
        }
    }

    private static void refreshRanksByQueue() {
        try {
            Map<String, Long> value = AggregateService.get(RedisKey.STATUS_RANKS_BY_QUEUE,
                new TypeReference<Map<String, Long>>() {}, MongoDB::rankTotalsByQueue);
            RANKS_BY_QUEUE.set(value);
        } catch (Exception ignored) {
        }
    }
}
