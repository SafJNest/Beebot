package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerLeaderboard;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.tracker.DatabaseTracker;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class LeaderboardService {

    private static final String GLOBAL_REGION = "GLOBAL";
    private static final String ALL_RANKS = "ALL";
    public static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = DEFAULT_PAGE_SIZE;
    private static final List<TierType> PROFILE_REBUILD_RANKS = List.of(TierType.GRANDMASTER, TierType.CHALLENGER);
    private static final List<GameQueueType> PROFILE_REBUILD_QUEUES = List.of(
        GameQueueType.RANKED_SOLO_5X5,
        GameQueueType.RANKED_FLEX_SR
    );

    private final ProfileService profileService = new ProfileService();

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
        Map<String, ProfileStatistics> statisticsBySummoner = new HashMap<>();
        Map<LeagueShard, List<String>> puuidsByShard = new HashMap<>();
        for (Summoner summoner : summoners) {
            LeagueShard shard = LeagueShard.valueOf(summoner.region());
            puuidsByShard.computeIfAbsent(shard, ignored -> new ArrayList<>()).add(summoner.puuid());
        }
        for (Map.Entry<LeagueShard, List<String>> entry : puuidsByShard.entrySet())
            statisticsBySummoner.putAll(profileService.getStatistics(entry.getValue(), entry.getKey(), season));
        for (Summoner summoner : summoners) {
            if (statisticsBySummoner.containsKey(summoner.puuid())) continue;
            DatabaseTracker.startProfileStatistics(summoner, season);
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
        if (statisticsBySummoner.size() == summoners.size()) RedisClient.set(
            RedisKey.LEADERBOARD_PAGE,
            response,
            version, rankKey, selectedQueue.name(), selectedRegion, page, limit
        );
        return ApiResult.ready(response);
    }

    public LeaderboardDistribution getRankDistribution(GameQueueType queue, LeagueShard region) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        String selectedRegion = defaultRegion(region);
        long version = cacheVersion();
        String key = RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(
            version, selectedQueue.name(), selectedRegion
        );
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB.findRankDistribution(selectedQueue, selectedRegion);
        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(
            RedisKey.LEADERBOARD_RANK_DISTRIBUTION,
            response,
            version, selectedQueue.name(), selectedRegion
        );
        return response;
    }

    public void rebuildHighEloAndTrackedProfileStatistics() {
        Filter filter = Filter.summoner();
        Set<String> processedPuuids = new HashSet<>();
        int candidates = 0;
        int submitted = 0;

        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            for (GameQueueType queue : PROFILE_REBUILD_QUEUES) {
                for (TierType rank : PROFILE_REBUILD_RANKS) {
                    long offset = 0;
                    while (true) {
                        MongoDB.LeaderboardQuery page = MongoDB.findLeaderboardPage(
                            rank, queue, shard.name(), offset, DEFAULT_PAGE_SIZE
                        );
                        if (page.summoners().isEmpty()) break;

                        candidates += page.summoners().size();
                        submitted += rebuildProfilePage(page.summoners(), filter, processedPuuids);
                        offset += page.summoners().size();
                        if (offset >= page.total()) break;
                    }
                }
            }
        }

        List<Summoner> tracked = MongoDB.findTrackedSummonerModels();
        candidates += tracked.size();
        submitted += rebuildProfilePage(tracked, filter, processedPuuids);

    }

    public LeaderboardDistribution getTopRegions(GameQueueType queue, TierType rank) {
        requireRank(rank);
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(defaultQueue(queue));
        long version = cacheVersion();
        String key = RedisKey.LEADERBOARD_TOP_REGIONS.of(
            version, selectedQueue.name(), rank.name()
        );
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB.findTopRegions(selectedQueue, rank);
        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(
            RedisKey.LEADERBOARD_TOP_REGIONS,
            response,
            version, selectedQueue.name(), rank.name()
        );
        return response;
    }

    public static void rebuild() {
        MongoDB.rebuildLeaderboardAggregates();
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

    private int rebuildProfilePage(List<Summoner> summoners, Filter filter, Set<String> processedPuuids) {
        List<Summoner> selected = new ArrayList<>(summoners.size());
        for (Summoner summoner : summoners) {
            if (summoner != null && summoner.puuid() != null && processedPuuids.add(summoner.puuid()))
                selected.add(summoner);
        }

        List<CompletableFuture<Boolean>> refreshes = new ArrayList<>();
        for (Summoner summoner : selected) {
            refreshes.add(DatabaseTracker.startProfileStatistics(summoner, filter, true));
        }

        for (int index = 0; index < refreshes.size(); index++) {
            CompletableFuture<Boolean> refresh = refreshes.get(index);
            try {
                if (!refresh.join()) processedPuuids.remove(selected.get(index).puuid());
            } catch (RuntimeException exception) {
                processedPuuids.remove(selected.get(index).puuid());
                BotLogger.error("High elo/tracked profile statistics rebuild failed: " + exception.getMessage());
            }
        }
        return refreshes.size();
    }

    private static GameQueueType defaultQueue(GameQueueType queue) {
        return queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue;
    }

    private static String defaultRegion(LeagueShard region) {
        return region == null ? GLOBAL_REGION : region.name();
    }
}
