package com.safjnest.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.status.LeagueMetrics;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.JsonCodec;

public final class LeagueMetricsStore {

    private static final AtomicLong GAMES_ANALYZED = new AtomicLong();
    private static final AtomicLong TOTAL_SUMMONERS = new AtomicLong();
    private static final AtomicLong TOTAL_MASTERIES = new AtomicLong();
    private static final AtomicLong TRACKED_SUMMONERS = new AtomicLong();
    private static final AtomicReference<Map<String, Long>> RANKS_BY_QUEUE = new AtomicReference<>(Map.of());

    private LeagueMetricsStore() {}

    public static void seed() {
        refresh(true);
    }

    public static void refresh() {
        refresh(false);
    }

    public static LeagueMetrics snapshot() {
        return new LeagueMetrics(
            GAMES_ANALYZED.get(),
            TOTAL_SUMMONERS.get(),
            TOTAL_MASTERIES.get(),
            RANKS_BY_QUEUE.get()
        );
    }

    // ============================================================================

    private static void refresh(boolean force) {
        refreshLong(RedisKey.STATUS_GAMES_ANALYZED, GAMES_ANALYZED, MongoDB::estimatedMatchCount, force);
        refreshLong(RedisKey.STATUS_TOTAL_SUMMONERS, TOTAL_SUMMONERS, MongoDB::estimatedSummonerCount, force);
        refreshLong(RedisKey.STATUS_TOTAL_MASTERIES, TOTAL_MASTERIES, MongoDB::totalMasteriesCount, force);
        refreshLong(RedisKey.STATUS_TRACKED_SUMMONERS, TRACKED_SUMMONERS, LeagueMetricsStore::trackedSummonerSource, force);
        refreshRanksByQueue(force);
    }

    public static long trackedSummoners() {
        return TRACKED_SUMMONERS.get();
    }

    private static long trackedSummonerSource() {
        return MongoDB.findTrackedSummonerModels().size();
    }

    private static void refreshLong(RedisKey key, AtomicLong target, LongSupplier source, boolean force) {
        try {
            String redisKey = key.of();
            if (!force && RedisClient.ttl(redisKey) > 0) {
                Long cached = RedisClient.getLong(redisKey);
                if (cached != null) {
                    target.set(Math.max(0, cached));
                    return;
                }
            }
            long value = Math.max(0, source.getAsLong());
            target.set(value);
            RedisClient.setCached(redisKey, Long.toString(value), key.ttlSeconds());
        } catch (Exception ignored) {
        }
    }

    private static void refreshRanksByQueue(boolean force) {
        try {
            String redisKey = RedisKey.STATUS_RANKS_BY_QUEUE.of();
            if (!force && RedisClient.ttl(redisKey) > 0) {
                Map<String, Long> cached = RedisClient.get(redisKey, new TypeReference<Map<String, Long>>() {});
                if (cached != null) {
                    RANKS_BY_QUEUE.set(cached);
                    return;
                }
            }
            Map<String, Long> value = MongoDB.rankTotalsByQueue();
            RANKS_BY_QUEUE.set(value);
            RedisClient.setCached(redisKey, JsonCodec.toJson(value), RedisKey.STATUS_RANKS_BY_QUEUE.ttlSeconds());
        } catch (Exception ignored) {
        }
    }
}
