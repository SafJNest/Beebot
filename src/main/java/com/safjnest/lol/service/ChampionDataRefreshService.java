package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ChampionDataRefreshService {

    private final BuildService buildService = new BuildService();
    private final ChampionStatsService championStatsService = new ChampionStatsService();

    public boolean refresh(Filter filter) {
        if (filter == null || filter.champion() == 0) return false;

        List<Build> builds = buildService.recomputeAll(filter);
        Filter statsFilter = new Filter()
            .setPatch(filter.patch())
            .setQueue(filter.queue())
            .setRank(filter.rank())
            .setRegion(filter.region())
            .setLane(filter.lane());
        Map<Integer, ChampionStatistics> stats = championStatsService.recomputeAll(statsFilter);
        boolean refreshed = builds != null && !builds.isEmpty()
            && stats != null && stats.containsKey(filter.champion());
        if (refreshed) ChampionPageService.invalidate(filter);
        return refreshed;
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
                List<Build> computed = buildService.recomputeAll(filter);
                if (computed == null || computed.isEmpty()) emptyBuilds++;
                else builds += computed.size();
            } catch (Exception e) {
                BotLogger.warning("[LPTracker] Failed refreshing build filter " + filter.toKey());
                e.printStackTrace();
            }
        }

        int stats = 0;
        int emptyStats = 0;
        for (Filter filter : statFilters) {
            try {
                Map<Integer, ChampionStatistics> computed = championStatsService.recomputeAll(filter);
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
        QueryResult result = LeagueDB.getChampionBuildRefreshFilters(patch);
        for (QueryRecord row : result)
            addBuildFilter(filters, getBuildFilter(row), patch);

        result = LeagueDB.getStoredChampionBuildFilters();
        for (QueryRecord row : result) {
            try { addBuildFilter(filters, Filter.fromKey(row.get("filter")), patch); }
            catch (Exception e) {}
        }
        return new ArrayList<>(filters.values());
    }

    private List<Filter> getStatFilters(String patch) {
        Map<String, Filter> filters = new LinkedHashMap<>();
        QueryResult result = LeagueDB.getChampionStatsRefreshFilters(patch);
        for (QueryRecord row : result) {
            Filter statFilter = getStatFilter(row);
            addStatFilter(filters, statFilter, patch);
            if (GameQueueTypeUtils.hasLane(statFilter.queue()))
                addStatFilter(filters, getStatFilter(row).setLane(null), patch);
        }

        result = LeagueDB.getStoredChampionStatsFilters();
        for (QueryRecord row : result) {
            try { addStatFilter(filters, Filter.fromGenericKey(row.get("filter")), patch); }
            catch (Exception e) {}
        }
        return new ArrayList<>(filters.values());
    }

    private Filter getBuildFilter(QueryRecord row) {
        Filter filter = getStatFilter(row)
            .setChampion(row.getAsInt("champion"));
        if (GameQueueTypeUtils.hasLane(filter.queue()))
            filter.setLane(row.getAsLaneType("lane"));
        else
            filter.setLane(null);
        return filter;
    }

    private Filter getStatFilter(QueryRecord row) {
        Filter filter = new Filter()
            .setPatch(row.get("patch"))
            .setQueue(row.getAsGameQueueType("queue"))
            .setRank(getRank(row.get("rank")))
            .setRegion(row.getAsLeagueShard("region"));
        if (GameQueueTypeUtils.hasLane(filter.queue())) filter.setLane(row.getAsLaneType("lane"));
        return filter;
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

    private TierType getRank(String rank) {
        try { return rank != null ? TierType.valueOf(rank) : null; }
        catch (Exception e) { return null; }
    }
}
