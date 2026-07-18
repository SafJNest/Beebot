package com.safjnest.lol.service;

import com.safjnest.mongo.MongoDB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.model.leaderboard.LeaderboardRow;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerLeaderboard;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class LeaderboardService {

    private static final String GLOBAL_REGION = "GLOBAL";
    private static final String ALL_RANKS = "ALL";
    public static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = DEFAULT_PAGE_SIZE;

    private static final int TTL_LEADERBOARD = 60 * 5;
    private static final int TTL_LEADERBOARD_COMPONENTS = 60 * 60 * 24;
    private static final int TTL_DISTRIBUTION = 60 * 5;
    private static final TypeReference<List<LeaderboardRow>> LEADERBOARD_ROWS_TYPE = new TypeReference<>() {};
    private static final List<TierType> COMPETITIVE_TIERS = List.of(
        TierType.CHALLENGER, TierType.GRANDMASTER, TierType.MASTER, TierType.DIAMOND, TierType.EMERALD,
        TierType.PLATINUM, TierType.GOLD, TierType.SILVER, TierType.BRONZE, TierType.IRON
    );

    private final ProfileStatisticsService profileStatisticsService = new ProfileStatisticsService();

    public ApiResult<LeaderboardPage> getLeaderboard(
        TierType rank, GameQueueType queue, LeagueShard region, int page, int limit
    ) {
        if (page < 1) throw new IllegalArgumentException("page must be greater than 0");
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("limit must be between 1 and 50");

        GameQueueType selectedQueue = defaultQueue(queue);
        String selectedRegion = defaultRegion(region);
        String rankKey = rank == null ? ALL_RANKS : rank.name();
        String key = RedisKey.LEADERBOARD_PAGE.of(
            rankKey, GameQueueTypeUtils.canonicalQueue(selectedQueue).name(), selectedRegion, page, limit
        );
        LeaderboardPage cached = RedisClient.get(key, LeaderboardPage.class);
        if (cached != null) return ApiResult.ready(cached);

        long offset = (long) (page - 1) * limit;
        String selectedQueueName = GameQueueTypeUtils.canonicalQueue(selectedQueue).name();
        String totalKey = RedisKey.LEADERBOARD_TOTAL.of(rankKey, selectedQueueName, selectedRegion);
        String rowsKey = RedisKey.LEADERBOARD_ROWS.of(rankKey, selectedQueueName, selectedRegion, offset, limit);

        Long cachedTotal = RedisClient.get(totalKey, Long.class);
        List<LeaderboardRow> cachedRows = RedisClient.get(rowsKey, LEADERBOARD_ROWS_TYPE);
        boolean totalReady = cachedTotal != null;
        boolean rowsReady = cachedRows != null;

        long total = totalReady ? cachedTotal : 0;
        List<LeaderboardRow> rows = rowsReady ? cachedRows : List.of();
        if (!totalReady && !rowsReady) {
            total = MongoDB.countLeaderboard(rank, selectedQueue, selectedRegion);
            rows = total > offset ? MongoDB.findLeaderboardRows(rank, selectedQueue, selectedRegion, offset, limit) : List.of();
            cacheLeaderboardComponents(totalKey, rowsKey, total, rows);
        } else {
            if (!totalReady) {
                total = MongoDB.countLeaderboard(rank, selectedQueue, selectedRegion);
                RedisClient.set(totalKey, total, TTL_LEADERBOARD_COMPONENTS);
            }
            if (!rowsReady && total > offset) {
                rows = MongoDB.findLeaderboardRows(rank, selectedQueue, selectedRegion, offset, limit);
                RedisClient.set(rowsKey, rows, TTL_LEADERBOARD_COMPONENTS);
            }
            if (total <= offset) rows = List.of();
        }

        long pages = total == 0 ? 0 : (total + limit - 1) / limit;

        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        List<String> puuids = new ArrayList<>(rows.size());
        for (LeaderboardRow row : rows) puuids.add(row.summoner().puuid());
        Map<String, ProfileStatistics> statisticsBySummoner = profileStatisticsService.getByPuuid(puuids, season);
        List<SummonerLeaderboard> summoners = new ArrayList<>(rows.size());
        boolean cacheable = true;
        for (int i = 0; i < rows.size(); i++) {
            LeaderboardRow row = rows.get(i);
            Summoner summoner = row.summoner();
            ProfileStatistics statistics = statisticsBySummoner.get(summoner.puuid());
            if (statistics == null) {
                cacheable = false;
                Tracker.startProfileStatistics(summoner, season);
            }
        }

        if (!cacheable) return ApiResult.pending();

        for (int i = 0; i < rows.size(); i++) {
            LeaderboardRow row = rows.get(i);
            SummonerView view = SummonerView.from(
                row.summoner(), List.of(row.rank()), statisticsBySummoner.get(row.summoner().puuid()), List.of()
            );
            summoners.add(new SummonerLeaderboard(offset + i + 1, view));
        }

        LeaderboardPage response = new LeaderboardPage(page, limit, total, pages, summoners);
        RedisClient.set(key, response, TTL_LEADERBOARD);
        return ApiResult.ready(response);
    }

    public LeaderboardDistribution getRankDistribution(GameQueueType queue, LeagueShard region) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        String selectedRegion = defaultRegion(region);
        String key = RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(selectedQueue.name(), selectedRegion);
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB
                .findRankDistribution(selectedQueue, selectedRegion);

        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(key, response, TTL_DISTRIBUTION);
        return response;
    }

    public LeaderboardDistribution getTopRegions(GameQueueType queue, TierType rank) {
        requireRank(rank);
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        String key = RedisKey.LEADERBOARD_TOP_REGIONS.of(selectedQueue.name(), rank.name());
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB
                .findTopRegions(selectedQueue, rank);

        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(key, response, TTL_DISTRIBUTION);
        return response;
    }

    public static boolean rebuildDistribution() {
        boolean rebuilt = MongoDB.rebuildLeaderboardDistribution();
        if (rebuilt) clearDistributionCache();
        return rebuilt;
    }

    // ============================================================================

    private static void cacheLeaderboardComponents(
        String totalKey, String rowsKey, long total, List<LeaderboardRow> rows
    ) {
        RedisClient.set(totalKey, total, TTL_LEADERBOARD_COMPONENTS);
        RedisClient.set(rowsKey, rows, TTL_LEADERBOARD_COMPONENTS);
    }

    private static void requireRank(TierType rank) {
        if (rank == null) throw new IllegalArgumentException("rank is required");
    }

    private static GameQueueType defaultQueue(GameQueueType queue) {
        return queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue;
    }

    private static String defaultRegion(LeagueShard region) {
        return region == null ? GLOBAL_REGION : region.name();
    }

    private static void clearDistributionCache() {
        List<String> regions = new ArrayList<>();
        regions.add(GLOBAL_REGION);
        for (LeagueShard shard : LeagueShard.values()) {
            if (shard != LeagueShard.UNKNOWN) regions.add(shard.name());
        }

        for (GameQueueType queue : GameQueueType.values()) {
            GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(queue);
            if (selectedQueue != queue) continue;
            for (String region : regions) {
                RedisClient.delete(RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(selectedQueue.name(), region));
                for (TierType rank : COMPETITIVE_TIERS) {
                    RedisClient.delete(RedisKey.LEADERBOARD_TOTAL.of(rank.name(), selectedQueue.name(), region));
                }
                RedisClient.delete(RedisKey.LEADERBOARD_TOTAL.of(ALL_RANKS, selectedQueue.name(), region));
            }
            for (TierType rank : COMPETITIVE_TIERS) {
                RedisClient.delete(RedisKey.LEADERBOARD_TOP_REGIONS.of(selectedQueue.name(), rank.name()));
            }
        }
    }
}
