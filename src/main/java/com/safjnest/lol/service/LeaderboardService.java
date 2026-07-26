package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerLeaderboard;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.nosql.MongoDB;
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
    private static final int TTL_DISTRIBUTION = 60 * 5;

    private final ProfileStatisticsService profileStatisticsService = new ProfileStatisticsService();

    public ApiResult<LeaderboardPage> getLeaderboard(
        TierType rank, GameQueueType queue, LeagueShard region, int page, int limit
    ) {
        if (page < 1) throw new IllegalArgumentException("page must be greater than 0");
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("limit must be between 1 and 50");

        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        String selectedRegion = defaultRegion(region);
        String rankKey = rank == null ? ALL_RANKS : rank.name();
        long version = cacheVersion();
        String key = RedisKey.LEADERBOARD_PAGE.of(
            version, rankKey, selectedQueue.name(), selectedRegion, page, limit
        );
        LeaderboardPage cached = RedisClient.get(key, LeaderboardPage.class);
        if (cached != null) return ApiResult.ready(cached);

        long offset = (long) (page - 1) * limit;
        MongoDB.LeaderboardQuery query = MongoDB.findLeaderboardPage(
            rank, selectedQueue, selectedRegion, offset, limit
        );
        long total = query.total();
        List<Summoner> summoners = query.summoners();
        long pages = total == 0 ? 0 : (total + limit - 1) / limit;

        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        List<String> puuids = new ArrayList<>(summoners.size());
        for (Summoner summoner : summoners) puuids.add(summoner.puuid());
        Map<String, ProfileStatistics> statisticsBySummoner = profileStatisticsService.getByPuuid(puuids, season);
        for (Summoner summoner : summoners) {
            if (statisticsBySummoner.containsKey(summoner.puuid())) continue;
            Tracker.startProfileStatistics(summoner, season);
        }

        List<SummonerLeaderboard> leaderboardSummoners = new ArrayList<>(summoners.size());
        for (int index = 0; index < summoners.size(); index++) {
            Summoner summoner = summoners.get(index);
            Rank rankValue = summoner.ranks().isEmpty() ? Rank.unranked() : summoner.ranks().get(0);
            ProfileStatistics statistics = statisticsBySummoner.get(summoner.puuid());
            SummonerView view = SummonerView.from(
                summoner,
                List.of(rankValue),
                statistics,
                statistics == null ? List.of() : summoner.masteries()
            );
            leaderboardSummoners.add(new SummonerLeaderboard(offset + index + 1, view));
        }

        LeaderboardPage response = new LeaderboardPage(page, limit, total, pages, leaderboardSummoners);
        if (statisticsBySummoner.size() == summoners.size()) RedisClient.set(key, response, TTL_LEADERBOARD);
        return ApiResult.ready(response);
    }

    public LeaderboardDistribution getRankDistribution(GameQueueType queue, LeagueShard region) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        String selectedRegion = defaultRegion(region);
        String key = RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(
            cacheVersion(), selectedQueue.name(), selectedRegion
        );
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB.findRankDistribution(selectedQueue, selectedRegion);
        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(key, response, TTL_DISTRIBUTION);
        return response;
    }

    public LeaderboardDistribution getTopRegions(GameQueueType queue, TierType rank) {
        requireRank(rank);
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        String key = RedisKey.LEADERBOARD_TOP_REGIONS.of(
            cacheVersion(), selectedQueue.name(), rank.name()
        );
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB.findTopRegions(selectedQueue, rank);
        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(key, response, TTL_DISTRIBUTION);
        return response;
    }

    public static void invalidateCache() {
        RedisClient.increment(RedisKey.LEADERBOARD_VERSION.of());
    }

    // ============================================================================

    private static long cacheVersion() {
        Long version = RedisClient.get(RedisKey.LEADERBOARD_VERSION.of(), Long.class);
        return version == null ? 0 : version;
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
}
