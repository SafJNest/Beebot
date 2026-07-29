package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ChampionDataRefreshService {

    public record MatrixRefreshResult(
        int combinations,
        int skipped,
        int generated,
        int empty,
        int persistedChampions
    ) {}

    public boolean refresh(Filter filter) {
        if (filter == null || filter.champion() == 0) return false;

        boolean builds = refreshBuild(filter);
        Map<Integer, ChampionStatistics> stats = refreshStats(filter);
        return builds && stats.containsKey(filter.champion());
    }

    public boolean refreshBuild(Filter filter) {
        if (filter == null || filter.champion() == 0) return false;
        List<Build> builds = BuildService.recomputeAll(filter);
        boolean refreshed = builds != null && !builds.isEmpty();
        if (refreshed) ChampionPageService.invalidate(filter);
        return refreshed;
    }

    public Map<Integer, ChampionStatistics> refreshStats(Filter filter) {
        if (filter == null) return Map.of();
        Map<Integer, ChampionStatistics> statistics = new LinkedHashMap<>(
            ChampionStatsService.recomputeAll(statsFilter(filter)));
        if (filter.champion() != 0 && !statistics.containsKey(filter.champion())) {
            ChampionStatistics empty = ChampionStatsService.empty(filter);
            MongoDB.upsertChampionStatistics(empty);
            statistics.put(filter.champion(), empty);
        }
        return statistics;
    }

    public MatrixRefreshResult refreshStatsMatrix(String patch, GameQueueType queue) {
        List<Filter> combinations = matrixFilters(patch, queue);
        Set<String> readyKeys = new HashSet<>();
        for (Filter filter : combinations) {
            if (isReady(filter)) readyKeys.add(filter.genericKey());
        }
        List<Filter> missing = missingMatrixFilters(combinations, readyKeys);

        ChampionStatsService.MatrixResult result = ChampionStatsService.recomputeMatrix(missing);
        return new MatrixRefreshResult(
            combinations.size(),
            combinations.size() - missing.size(),
            result.filters() - result.emptyFilters(),
            result.emptyFilters(),
            result.persistedChampions());
    }

    static List<Filter> matrixFilters(String patch, GameQueueType queue) {
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

        for (LeagueShard region : regions) {
            for (TierType rank : ranks) {
                for (LaneType lane : lanes) {
                    filters.add(new Filter()
                        .setChampion(0)
                        .setLane(lane)
                        .setQueue(queue)
                        .setRank(rank)
                        .setPatch(patch)
                        .setRegion(region));
                }
            }
        }
        return filters;
    }

    static List<Filter> missingMatrixFilters(List<Filter> combinations, Set<String> readyKeys) {
        if (combinations == null || combinations.isEmpty()) return List.of();
        List<Filter> missing = new ArrayList<>();
        for (Filter filter : combinations) {
            if (filter != null && (readyKeys == null || !readyKeys.contains(filter.genericKey()))) missing.add(filter);
        }
        return missing;
    }

    public void refresh() {
        String patch = new Filter().patch();
        List<Filter> buildFilters = getBuildFilters(patch);
        List<Filter> statFilters = getStatFilters(patch);

        BotLogger.info("[LPTracker] Refreshing champion data for patch " + patch + " (" + buildFilters.size() + " build filters, " + statFilters.size() + " stat filters)");

        int builds = 0;
        int emptyBuilds = 0;
        for (Filter filter : buildFilters) {
            try {
                if (!refreshBuild(filter)) emptyBuilds++;
                else builds++;
            } catch (Exception e) {
                BotLogger.warning("[LPTracker] Failed refreshing build filter " + filter.toKey());
                e.printStackTrace();
            }
        }

        int stats = 0;
        int emptyStats = 0;
        for (Filter filter : statFilters) {
            try {
                Map<Integer, ChampionStatistics> computed = refreshStats(filter);
                if (computed == null || computed.isEmpty()) emptyStats++;
                else stats += computed.size();
            } catch (Exception e) {
                BotLogger.warning("[LPTracker] Failed refreshing champion stats filter " + filter.genericKey());
                e.printStackTrace();
            }
        }

        BotLogger.info("[LPTracker] Refreshed champion data for patch " + patch + ": " + builds + " builds (" + emptyBuilds + " empty filters), " + stats + " champion stats (" + emptyStats + " empty filters)");
    }

    // ============================================================================

    private List<Filter> getBuildFilters(String patch) {
        Map<String, Filter> filters = new LinkedHashMap<>();
        for (Filter filter : MongoDB.findChampionBuildRefreshFilters(patch))
            addBuildFilter(filters, filter, patch);
        for (Filter filter : MongoDB.findStoredChampionBuildFilters())
            addBuildFilter(filters, filter, patch);
        return new ArrayList<>(filters.values());
    }

    private List<Filter> getStatFilters(String patch) {
        Map<String, Filter> filters = new LinkedHashMap<>();
        for (Filter statFilter : MongoDB.findChampionStatisticsRefreshFilters(patch)) {
            addStatFilter(filters, statFilter, patch);
            if (GameQueueTypeUtils.hasLane(statFilter.queue())) addStatFilter(filters, statFilter.setLane(null), patch);
        }
        for (Filter statFilter : MongoDB.findStoredChampionStatisticsFilters())
            addStatFilter(filters, statFilter, patch);
        return new ArrayList<>(filters.values());
    }

    private void addBuildFilter(Map<String, Filter> filters, Filter filter, String patch) {
        if (filter == null || filter.champion() == 0 || !patch.equals(filter.patch())) return;
        if (!GameQueueTypeUtils.hasLane(filter.queue()))
            filter.setLane(null);
        filters.put(filter.toKey(), filter);
    }

    private void addStatFilter(Map<String, Filter> filters, Filter filter, String patch) {
        if (filter == null || !patch.equals(filter.patch())) return;
        filters.put(filter.genericKey(), filter);
    }

    private static Filter statsFilter(Filter filter) {
        return new Filter()
            .setPatch(filter.patch())
            .setQueue(filter.queue())
            .setRank(filter.rank())
            .setRegion(filter.region())
            .setLane(filter.lane());
    }

    private static boolean isReady(Filter filter) {
        return MongoDB.hasChampionStatisticsReady(filter);
    }

}
