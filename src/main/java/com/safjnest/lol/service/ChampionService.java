package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionTierList;
import com.safjnest.lol.model.ChampionTierSource;
import com.safjnest.lol.model.ChampionView;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ChampionIndexable;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.queue.ComputeRequestDispatcher;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.TimeConstant;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class ChampionService {

    public record MatrixRefreshResult(int combinations, int skipped, int generated, int empty, int persistedChampions) {}

    public ApiResult<ChampionView> get(
        String championValue,
        String patch,
        TierType rank,
        LeagueShard region,
        GameQueueType queue,
        LaneType role
    ) {
        StaticChampion champion = ChampionUtils.findChampion(championValue);
        if (champion == null) return ApiResult.notFound();
        GameQueueType selectedQueue = queue != null ? queue : GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        Filter filter = new Filter().setChampion(champion.getId()).setRank(rank).setRegion(region)
            .setQueue(selectedQueue).setLane(role);
        if (patch != null) filter.setPatch(patch);
        String key = RedisKey.CHAMPION_PAGE.of(filter.champion(), filter.pageKey());
        long statsLastUpdate = MongoDB.findChampionStatisticsLastUpdate(filter);
        long buildLastUpdate = MongoDB.findChampionBuildLastUpdate(filter);
        if (isStale(statsLastUpdate) || isStale(buildLastUpdate)) {
            RedisClient.delete(key);
            RedisClient.delete(RedisKey.CHAMPION_STATS.of(filter.champion(), filter.genericKey()));
        }
        ChampionView cached;
        try {
            cached = RedisClient.get(key, ChampionView.class);
        } catch (RuntimeException exception) {
            RedisClient.delete(key);
            cached = null;
        }
        if (cached != null) {
            ChampionView page = cached.withMetadata(metadata(statsLastUpdate, buildLastUpdate, false, filter));
            return ApiResult.ready(page, page.metadata());
        }
        return compose(champion, filter, statsLastUpdate, buildLastUpdate);
    }

    public ChampionStatistics getStatistics(Filter filter) {
        return ChampionAnalyzer.get(filter);
    }

    public ChampionStatistics getStatistics(Filter filter, boolean allowCompute) {
        return ChampionAnalyzer.get(filter, allowCompute);
    }

    public Map<Integer, ChampionStatistics> getStatisticsAll(Filter filter) {
        return ChampionAnalyzer.getAll(filter);
    }

    public Build getBuild(Filter filter) {
        return ChampionBuildEngine.getAggregate(filter);
    }

    public Build getBuild(Filter filter, boolean allowCompute) {
        return ChampionBuildEngine.getAggregate(filter, allowCompute);
    }

    public boolean hasBuild(Filter filter) {
        return filter != null && getBuild(filter, false) != null;
    }

    public List<ChampionIndexable> getIndexables() {
        String patch = PatchUtils.getPatch();
        return patch == null ? List.of() : MongoDB.findChampionIndexables(patch);
    }

    public boolean refresh(Filter filter) {
        if (filter == null || filter.champion() == 0) return false;
        boolean build = refreshBuild(filter);
        Map<Integer, ChampionStatistics> stats = refreshStatistics(filter);
        return build && stats.containsKey(filter.champion());
    }

    public boolean refreshBuild(Filter filter) {
        if (filter == null || filter.champion() == 0) return false;
        List<Build> builds = ChampionBuildEngine.recomputeAll(filter);
        boolean refreshed = builds != null && !builds.isEmpty();
        if (refreshed) invalidate(filter);
        return refreshed;
    }

    public Map<Integer, ChampionStatistics> refreshStatistics(Filter filter) {
        if (filter == null) return Map.of();
        Map<Integer, ChampionStatistics> statistics = new LinkedHashMap<>(
            ChampionAnalyzer.recomputeAll(statisticsFilter(filter)));
        if (filter.champion() != 0 && !statistics.containsKey(filter.champion())) {
            ChampionStatistics empty = ChampionAnalyzer.empty(filter);
            MongoDB.upsertChampionStatistics(empty);
            statistics.put(filter.champion(), empty);
        }
        invalidateTierList(filter);
        return statistics;
    }

    public ApiResult<ChampionTierList> getTierList(
        String patch,
        TierType rank,
        LeagueShard region,
        GameQueueType queue
    ) {
        GameQueueType selectedQueue = queue != null ? queue : GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        Filter base = new Filter().setChampion(0).setLane(null).setRank(rank).setRegion(region).setQueue(selectedQueue);
        if (patch != null) base.setPatch(patch);
        String key = tierListKey(base);
        ChampionTierList cached;
        try {
            cached = RedisClient.get(key, ChampionTierList.class);
        } catch (RuntimeException exception) {
            RedisClient.delete(key);
            cached = null;
        }
        if (cached != null) return ApiResult.ready(cached, cached.metadata());

        List<Filter> filters = tierFilters(base);
        Map<String, ChampionTierSource> sources = MongoDB.findChampionTierSources(filters);
        List<Filter> ready = new ArrayList<>();
        long lastUpdate = 0;
        boolean refresh = false;
        for (Filter filter : filters) {
            ChampionTierSource source = sources.get(filter.genericKey());
            if (source == null || !source.ready() || isStale(source.lastUpdate())) {
                refresh = true;
                continue;
            }
            ready.add(filter);
            lastUpdate = oldest(lastUpdate, source.lastUpdate());
        }
        ResponseMetadata metadata = new ResponseMetadata(null, lastUpdate > 0 ? lastUpdate : null, refresh, base);
        ChampionTierList result = new ChampionTierList(ChampionTierAnalyzer.analyze(ready, sources), metadata);
        if (refresh) {
            ComputeRequestDispatcher.enqueueChampionStatsMatrix(base.patch(), base.queue());
            return ApiResult.partial(result, metadata);
        }
        RedisClient.set(RedisKey.CHAMPION_TIER_LIST, result, base.genericKey());
        return ApiResult.ready(result, metadata);
    }

    public MatrixRefreshResult refreshStatisticsMatrix(String patch, GameQueueType queue) {
        return refreshStatisticsMatrix(patch, queue, List.of());
    }

    public MatrixRefreshResult refreshStatisticsMatrix(String patch, GameQueueType queue, List<Filter> buildFilters) {
        List<Filter> combinations = matrixFilters(patch, queue);
        Set<String> readyKeys = new HashSet<>();
        for (Filter filter : combinations)
            if (MongoDB.hasChampionStatisticsReady(filter)
                    && !isStale(MongoDB.findChampionStatisticsLastUpdate(filter))) {
                readyKeys.add(filter.genericKey());
            }
        List<Filter> missing = missingMatrixFilters(combinations, readyKeys);
        ChampionAnalyzer.MatrixResult result = ChampionAnalyzer.recomputeMatrix(missing, buildFilters);
        invalidateTierLists(missing);
        if (missing.isEmpty()) for (Filter filter : buildFilters) refreshBuild(filter);
        for (Filter filter : buildFilters) invalidate(filter);
        return new MatrixRefreshResult(combinations.size(), combinations.size() - missing.size(),
            result.filters() - result.emptyFilters(), result.emptyFilters(), result.persistedChampions());
    }

    public List<ChampionIndexable> refreshIndexables() {
        String patch = PatchUtils.getPatch();
        if (patch == null || patch.isBlank()) return List.of();
        Map<Integer, Map<LaneType, Integer>> gamesByChampion = MongoDB.findChampionRoleGames();
        List<ChampionIndexable> values = new ArrayList<>();
        for (Map.Entry<Integer, Map<LaneType, Integer>> entry : gamesByChampion.entrySet()) {
            int totalGames = 0;
            for (int games : entry.getValue().values()) totalGames += games;
            List<LaneType> roles = new ArrayList<>(LaneTypeUtils.playables());
            roles.sort(java.util.Comparator.comparingInt(
                (LaneType role) -> -entry.getValue().getOrDefault(role, 0)
            ).thenComparingInt(LaneTypeUtils::playableOrder));
            for (LaneType role : roles) {
                int games = entry.getValue().getOrDefault(role, 0);
                values.add(new ChampionIndexable(entry.getKey(), role, games,
                    isIndexable(games, totalGames), 0L));
            }
        }
        MongoDB.upsertChampionIndexables(patch, values);
        return MongoDB.findChampionIndexables(patch);
    }

    public void refresh() {
        String patch = new Filter().patch();
        for (Filter filter : getBuildFilters(patch)) {
            try {
                refreshBuild(filter);
            } catch (RuntimeException exception) {
                BotLogger.warning("[LPTracker] Failed refreshing build filter " + filter.toKey());
            }
        }
        Set<GameQueueType> queues = new LinkedHashSet<>();
        List<String> patches = PatchUtils.getRecentPatches(3);
        for (String statsPatch : patches) queues.addAll(getStatQueues(statsPatch));
        for (String statsPatch : patches) for (GameQueueType queue : queues) refreshStatisticsMatrix(statsPatch, queue);
    }

    public static List<Filter> matrixFilters(String patch, GameQueueType queue) {
        if (patch == null || patch.isBlank() || queue == null) return List.of();
        List<Filter> filters = new ArrayList<>();
        List<LeagueShard> regions = new ArrayList<>();
        regions.add(null);
        regions.addAll(LeagueShardUtils.getActives());
        List<TierType> ranks = new ArrayList<>();
        ranks.add(null);
        ranks.addAll(TierDivisionUtils.getHigherTiers(TierType.IRON));
        List<LaneType> lanes = new ArrayList<>();
        lanes.add(null);
        if (GameQueueTypeUtils.hasLane(queue)) lanes.addAll(LaneTypeUtils.playables());
        for (LeagueShard region : regions) for (TierType rank : ranks) for (LaneType lane : lanes)
            filters.add(new Filter().setChampion(0).setLane(lane).setQueue(queue).setRank(rank)
                .setPatch(patch).setRegion(region));
        return filters;
    }

    public static List<Filter> missingMatrixFilters(List<Filter> combinations, Set<String> readyKeys) {
        if (combinations == null || combinations.isEmpty()) return List.of();
        List<Filter> missing = new ArrayList<>();
        for (Filter filter : combinations)
            if (filter != null && (readyKeys == null || !readyKeys.contains(filter.genericKey()))) missing.add(filter);
        return missing;
    }

    public static void invalidate(Filter filter) {
        if (filter != null) RedisClient.delete(RedisKey.CHAMPION_PAGE.of(filter.champion(), filter.pageKey()));
    }

    public static void invalidateTierList(Filter filter) {
        if (filter != null) RedisClient.delete(tierListKey(filter));
    }

    // ============================================================================

    private ApiResult<ChampionView> compose(
        StaticChampion champion,
        Filter filter,
        long statsLastUpdate,
        long buildLastUpdate
    ) {
        ChampionStatistics stats = getStatistics(filter, false);
        Build build = getBuild(filter, false);
        boolean statisticsPending = stats == null || isStale(statsLastUpdate);
        boolean buildPending = build == null || isStale(buildLastUpdate);
        if (statisticsPending || buildPending) {
            ComputeRequestDispatcher.startChampionData(filter, statisticsPending, buildPending);
            return ApiResult.pending(metadata(statsLastUpdate, buildLastUpdate, true, filter));
        }
        ChampionView page = new ChampionView(new ChampionView.Champion(champion.getId(), champion.getName(),
            ChampionUtils.getChampionProfilePic(champion.getId())), stats, build)
            .withMetadata(metadata(statsLastUpdate, buildLastUpdate, false, filter));
        RedisClient.set(RedisKey.CHAMPION_PAGE, page.withMetadata(null), filter.champion(), filter.pageKey());
        return ApiResult.ready(page, page.metadata());
    }

    private List<Filter> getBuildFilters(String patch) {
        Map<String, Filter> filters = new LinkedHashMap<>();
        for (Filter filter : MongoDB.findChampionBuildRefreshFilters(patch)) addBuildFilter(filters, filter, patch);
        for (Filter filter : MongoDB.findStoredChampionBuildFilters()) addBuildFilter(filters, filter, patch);
        return new ArrayList<>(filters.values());
    }

    private Set<GameQueueType> getStatQueues(String patch) {
        Set<GameQueueType> queues = new LinkedHashSet<>();
        for (Filter filter : MongoDB.findChampionStatisticsRefreshFilters(patch))
            if (filter.queue() != null) queues.add(filter.queue());
        return queues;
    }

    private void addBuildFilter(Map<String, Filter> filters, Filter filter, String patch) {
        if (filter == null || filter.champion() == 0 || !patch.equals(filter.patch())) return;
        if (!GameQueueTypeUtils.hasLane(filter.queue())) filter.setLane(null);
        filters.put(filter.toKey(), filter);
    }

    private static Filter statisticsFilter(Filter filter) {
        return new Filter().setPatch(filter.patch()).setQueue(filter.queue()).setRank(filter.rank())
            .setRegion(filter.region()).setLane(filter.lane());
    }

    private static List<Filter> tierFilters(Filter base) {
        if (base == null || base.queue() == null) return List.of();
        List<Filter> result = new ArrayList<>();
        if (!GameQueueTypeUtils.hasLane(base.queue())) {
            result.add(tierFilter(base, null));
            return result;
        }
        for (LaneType lane : LaneTypeUtils.playables()) result.add(tierFilter(base, lane));
        return result;
    }

    private static Filter tierFilter(Filter source, LaneType lane) {
        return new Filter().setChampion(0).setPatch(source.patch()).setQueue(source.queue()).setRank(source.rank())
            .setRegion(source.region()).setLane(lane);
    }

    private static String tierListKey(Filter filter) {
        return RedisKey.CHAMPION_TIER_LIST.of(tierFilter(filter, null).genericKey());
    }

    private static void invalidateTierLists(List<Filter> filters) {
        if (filters == null || filters.isEmpty()) return;
        Set<String> keys = new HashSet<>();
        for (Filter filter : filters) if (filter != null) keys.add(tierListKey(filter));
        for (String key : keys) RedisClient.delete(key);
    }

    private static boolean isStale(long lastUpdate) {
        return lastUpdate <= 0 || System.currentTimeMillis() - lastUpdate >= TimeConstant.WEEK;
    }

    private static ResponseMetadata metadata(long statsLastUpdate, long buildLastUpdate, boolean refresh, Filter filter) {
        long lastUpdate = oldest(statsLastUpdate, buildLastUpdate);
        return new ResponseMetadata(null, lastUpdate > 0 ? lastUpdate : null, refresh, filter);
    }

    private static long oldest(long first, long second) {
        if (first <= 0) return second;
        if (second <= 0) return first;
        return Math.min(first, second);
    }

    static boolean isIndexable(int games, int totalGames) {
        return totalGames > 0 && games > 0 && (double) games / totalGames >= 0.10D;
    }
}
