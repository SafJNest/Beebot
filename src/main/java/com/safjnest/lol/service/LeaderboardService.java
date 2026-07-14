package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
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
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class LeaderboardService {

    private static final String GLOBAL_REGION = "GLOBAL";
    public static final int PAGE_SIZE = 50;

    private static final int TTL_LEADERBOARD = 60 * 5;
    private static final int TTL_DISTRIBUTION = 60 * 5;
    private static final List<TierType> COMPETITIVE_TIERS = List.of(
        TierType.CHALLENGER, TierType.GRANDMASTER, TierType.MASTER, TierType.DIAMOND, TierType.EMERALD,
        TierType.PLATINUM, TierType.GOLD, TierType.SILVER, TierType.BRONZE, TierType.IRON
    );

    private final ProfileStatisticsService profileStatisticsService = new ProfileStatisticsService();

    public ApiResult<LeaderboardPage> getLeaderboard(TierType rank, GameQueueType queue, LeagueShard region, int page) {
        requireRank(rank);
        if (page < 1) throw new IllegalArgumentException("page must be greater than 0");

        GameQueueType selectedQueue = defaultQueue(queue);
        String selectedRegion = defaultRegion(region);
        String key = RedisKey.LEADERBOARD_PAGE.of(rank.name(), canonicalQueue(selectedQueue).name(), selectedRegion, page);
        LeaderboardPage cached = RedisClient.get(key, LeaderboardPage.class);
        if (cached != null) return ApiResult.ready(cached);

        long total = LeagueDB.countLeaderboard(rank.name(), queueValues(selectedQueue), selectedRegion);
        long pages = total == 0 ? 0 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        long offset = (long) (page - 1) * PAGE_SIZE;
        QueryResult rows = total > 0 && page <= pages
            ? LeagueDB.getLeaderboard(rank.name(), queueValues(selectedQueue), selectedRegion, offset, PAGE_SIZE)
            : new QueryResult();

        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        List<Integer> summonerIds = new ArrayList<>(rows.size());
        for (QueryRecord row : rows) summonerIds.add(row.getAsInt("summoner_id"));
        Map<Integer, ProfileStatistics> statisticsBySummoner = profileStatisticsService.get(summonerIds, season);
        List<SummonerLeaderboard> summoners = new ArrayList<>(rows.size());
        boolean cacheable = true;
        for (int i = 0; i < rows.size(); i++) {
            QueryRecord row = rows.get(i);
            Summoner summoner = new Summoner(
                row.getAsInt("summoner_id"), row.get("puuid"), row.get("riot_id"), row.get("region"),
                row.getAsInt("level"), row.getAsInt("icon")
            );
            Rank summonerRank = new Rank(
                canonicalQueue(selectedQueue), row.getAsTier("rank"), row.getAsInt("lp"),
                row.getAsInt("wins"), row.getAsInt("losses")
            );
            ProfileStatistics statistics = statisticsBySummoner.get(summoner.summonerId());
            if (statistics == null) {
                cacheable = false;
                Tracker.enqueueProfileStatistics(summoner.summonerId(), season);
            }
            SummonerView view = SummonerView.from(summoner, List.of(summonerRank), statistics, List.of());
            summoners.add(new SummonerLeaderboard(offset + i + 1, view));
        }

        LeaderboardPage response = new LeaderboardPage(page, PAGE_SIZE, total, pages, summoners);
        if (!cacheable) return ApiResult.partial(response);
        RedisClient.set(key, response, TTL_LEADERBOARD);
        return ApiResult.ready(response);
    }

    public LeaderboardDistribution getRankDistribution(GameQueueType queue, LeagueShard region) {
        GameQueueType selectedQueue = canonicalQueue(defaultQueue(queue));
        String selectedRegion = defaultRegion(region);
        String key = RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(selectedQueue.name(), selectedRegion);
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        Map<String, Long> counts = new HashMap<>();
        for (QueryRecord row : LeagueDB.getLeaderboardDistribution(selectedQueue.name(), selectedRegion)) {
            counts.put(row.get("rank"), row.getAsLong("players"));
        }

        List<LeaderboardDistribution.Entry> entries = new ArrayList<>();
        for (TierType tier : COMPETITIVE_TIERS) {
            entries.add(new LeaderboardDistribution.Entry(tier.name(), counts.getOrDefault(tier.name(), 0L)));
        }

        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(key, response, TTL_DISTRIBUTION);
        return response;
    }

    public LeaderboardDistribution getTopRegions(GameQueueType queue, TierType rank) {
        requireRank(rank);
        GameQueueType selectedQueue = canonicalQueue(defaultQueue(queue));
        String key = RedisKey.LEADERBOARD_TOP_REGIONS.of(selectedQueue.name(), rank.name());
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = new ArrayList<>();
        for (QueryRecord row : LeagueDB.getLeaderboardTopRegions(selectedQueue.name(), rank.name())) {
            entries.add(new LeaderboardDistribution.Entry(row.get("region"), row.getAsLong("players")));
        }

        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(key, response, TTL_DISTRIBUTION);
        return response;
    }

    public static boolean rebuildDistribution() {
        boolean rebuilt = LeagueDB.rebuildLeaderboardDistribution();
        if (rebuilt) clearDistributionCache();
        return rebuilt;
    }

    // ============================================================================

    private static List<String> queueValues(GameQueueType queue) {
        if (queue == GameQueueType.TEAM_BUILDER_RANKED_SOLO || queue == GameQueueType.RANKED_SOLO_5X5) {
            return List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), GameQueueType.RANKED_SOLO_5X5.name());
        }
        return List.of(queue.name());
    }

    private static void requireRank(TierType rank) {
        if (rank == null) throw new IllegalArgumentException("rank is required");
    }

    private static GameQueueType defaultQueue(GameQueueType queue) {
        return queue == null ? GameQueueType.TEAM_BUILDER_RANKED_SOLO : queue;
    }

    private static String defaultRegion(LeagueShard region) {
        return region == null ? GLOBAL_REGION : region.name();
    }

    private static GameQueueType canonicalQueue(GameQueueType queue) {
        return queue == GameQueueType.RANKED_SOLO_5X5 ? GameQueueType.TEAM_BUILDER_RANKED_SOLO : queue;
    }

    private static void clearDistributionCache() {
        List<String> regions = new ArrayList<>();
        regions.add(GLOBAL_REGION);
        for (LeagueShard shard : LeagueShard.values()) {
            if (shard != LeagueShard.UNKNOWN) regions.add(shard.name());
        }

        for (GameQueueType queue : GameQueueType.values()) {
            GameQueueType selectedQueue = canonicalQueue(queue);
            if (selectedQueue != queue) continue;
            for (String region : regions) {
                RedisClient.delete(RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(selectedQueue.name(), region));
            }
            for (TierType rank : COMPETITIVE_TIERS) {
                RedisClient.delete(RedisKey.LEADERBOARD_TOP_REGIONS.of(selectedQueue.name(), rank.name()));
            }
        }
    }
}
