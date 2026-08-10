package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.ActivityFilter;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ProfileIndexable;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.tracker.DatabaseTracker;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.TimeConstant;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileService {

    private static final AtomicBoolean ALL_PROFILE_STATS_REFRESH_RUNNING = new AtomicBoolean(false);
    private static final TypeReference<List<MatchResult>> RECENT_MATCHES_TYPE = new TypeReference<>() {};
    private static final int MIN_PROFILE_GAMES = 5;

    public ApiResult<SummonerView> get(LeagueShard shard, String puuid) {
        String key = RedisKey.PROFILE_PAGE.of(shard.name(), puuid);
        SummonerView cached = RedisClient.get(key, SummonerView.class);
        if (cached != null && isReady(cached)) {
            ProfileStatistics statistics = cached.overview().statistics();
            boolean refresh = isStale(statistics == null ? 0 : statistics.lastUpdate);
            if (refresh) startRefresh(cached.summoner(), shard);
            SummonerView page = withRecentMatches(cached, shard, Filter.summoner()).withMetadata(
                metadata(statistics == null ? 0 : statistics.lastUpdate, refresh, Filter.summoner()));
            return refresh ? ApiResult.partial(page, page.metadata()) : ApiResult.ready(page, page.metadata());
        }

        CompletableFuture<Summoner> profileFuture = SummonerService.getAsync(puuid, shard);
        CompletableFuture<List<Rank>> ranksFuture = RankService.getAsync(puuid, shard);
        CompletableFuture<List<Mastery>> masteriesFuture = MasteryService.getAsync(puuid, shard);
        if (!isReadyFuture(profileFuture) || !isReadyFuture(ranksFuture) || !isReadyFuture(masteriesFuture))
            return ApiResult.pending(metadata(0, true, Filter.summoner()));

        Summoner profile = completed(profileFuture);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return ApiResult.notFound();

        Filter filter = Filter.summoner();
        ProfileStatistics statistics = getStatistics(profile.puuid(), filter);
        List<Rank> ranks = completed(ranksFuture);
        List<Mastery> masteries = completed(masteriesFuture);
        boolean refresh = statistics == null || isStale(statistics.lastUpdate);
        if (refresh) startRefresh(profile, shard);

        List<MatchResult> recentMatches = statistics == null ? List.of() : getRecentMatches(profile.puuid(), shard, filter);
        SummonerView page = SummonerView.from(profile, ranks, statistics, masteries, recentMatches)
            .withMetadata(metadata(statistics == null ? 0 : statistics.lastUpdate, refresh, filter));
        if (statistics != null && !refresh) {
            RedisClient.set(RedisKey.PROFILE_PAGE, withoutRecentMatches(page), shard.name(), puuid);
            return ApiResult.ready(page, page.metadata());
        }
        return ApiResult.partial(page, page.metadata());
    }

    public ApiResult<SummonerView> get(LeagueShard shard, String gameName, String tagLine) {
        CompletableFuture<String> puuidFuture = SummonerService.getPuuidByRiotIdAsync(gameName, tagLine, shard);
        if (!isReadyFuture(puuidFuture)) return ApiResult.pending(metadata(0, true, Filter.summoner()));
        String puuid = completed(puuidFuture);
        return puuid != null ? get(shard, puuid) : ApiResult.notFound();
    }

    public ProfileStatistics getStatistics(String puuid, Filter filter) {
        if (puuid == null || filter == null) return null;
        ProfileStatistics statistics = RedisClient.get(statisticsKey(puuid, filter), ProfileStatistics.class);
        if (isCurrent(statistics)) return statistics;
        statistics = MongoDB.findProfileStatistics(puuid, filter);
        if (!isCurrent(statistics)) return null;
        cacheStatistics(puuid, filter, statistics);
        return statistics;
    }

    public ProfileStatistics getStatistics(String puuid, SeasonUtils.SeasonRange season) {
        return season == null ? null : getStatistics(puuid, Filter.summoner(season.start(), season.end()));
    }

    public Map<String, ProfileStatistics> getStatistics(List<String> puuids, Filter filter) {
        Map<String, ProfileStatistics> result = new HashMap<>();
        if (filter == null || puuids == null || puuids.isEmpty()) return result;
        List<String> missing = new ArrayList<>();
        List<String> keys = new ArrayList<>(puuids.size());
        Map<String, String> puuidsByKey = new HashMap<>();
        for (String puuid : puuids) {
            String key = statisticsKey(puuid, filter);
            keys.add(key);
            puuidsByKey.put(key, puuid);
        }
        for (Map.Entry<String, ProfileStatistics> entry : RedisClient.get(keys, ProfileStatistics.class).entrySet()) {
            String puuid = puuidsByKey.get(entry.getKey());
            if (puuid != null && isCurrent(entry.getValue())) result.put(puuid, entry.getValue());
        }
        for (String puuid : puuids) if (!result.containsKey(puuid)) missing.add(puuid);
        if (!missing.isEmpty()) {
            Map<String, ProfileStatistics> stored = MongoDB.findProfileStatistics(missing, filter);
            for (Map.Entry<String, ProfileStatistics> entry : stored.entrySet()) {
                if (!isCurrent(entry.getValue())) continue;
                result.put(entry.getKey(), entry.getValue());
                cacheStatistics(entry.getKey(), filter, entry.getValue());
            }
        }
        return result;
    }

    public Map<String, ProfileStatistics> getStatistics(List<String> puuids, SeasonUtils.SeasonRange season) {
        return season == null ? Map.of() : getStatistics(puuids, Filter.summoner(season.start(), season.end()));
    }

    public List<MatchResult> getRecentMatches(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || filter == null) return List.of();
        String key = recentMatchesKey(puuid, filter);
        List<MatchResult> cached = RedisClient.get(key, RECENT_MATCHES_TYPE);
        if (cached != null) return cached;
        List<MatchResult> result = MongoDB.findProfileRecentMatches(puuid, shard, filter, 5);
        RedisClient.set(RedisKey.PROFILE_RECENT_MATCHES, result, puuid, filter.toSummonerKey());
        return result;
    }

    public ApiResult<ProfileActivity> getActivity(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null) return ApiResult.notFound();
        ProfileActivity activity = RedisClient.get(activityKey(puuid, filter), ProfileActivity.class);
        if (activity == null) {
            activity = MongoDB.findProfileActivity(puuid, filter);
        }
        if (activity != null && !isStale(activityLastUpdate(activity))) {
            cacheActivity(puuid, filter, activity);
            ProfileActivity response = activity.withMetadata(metadata(activityLastUpdate(activity), false, filter));
            return ApiResult.ready(response, response.metadata());
        }

        long lastUpdate = activityLastUpdate(activity);
        if (isCanonicalActivity(filter)) {
            Summoner profile = SummonerService.find(puuid, shard);
            if (profile != null) startRefresh(profile, shard);
            else DatabaseTracker.startProfileActivity(puuid, shard, filter);
        } else DatabaseTracker.startProfileActivity(puuid, shard, filter);
        return ApiResult.pending(metadata(lastUpdate, true, filter));
    }

    public ApiResult<ProfileMatchups> getMatchups(LeagueShard shard, String puuid, ActivityFilter requestFilter) {
        if (shard == null || puuid == null || puuid.isBlank() || requestFilter == null) return ApiResult.notFound();
        CompletableFuture<Summoner> profileFuture = SummonerService.getAsync(puuid, shard);
        Filter filter = requestFilter.aggregationFilter();
        if (!isReadyFuture(profileFuture)) {
            Summoner profile = SummonerService.find(puuid, shard);
            if (profile != null && isCanonicalMatchups(filter)) startRefresh(profile, shard);
            return ApiResult.pending(metadata(0, true, filter));
        }
        Summoner profile = completed(profileFuture);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return ApiResult.notFound();
        ProfileMatchups matchups = getMatchups(puuid, filter);
        long lastUpdate = matchups == null ? 0 : matchups.lastUpdate();
        if (matchups == null || isStale(lastUpdate)) {
            if (isCanonicalMatchups(filter)) startRefresh(profile, shard);
            else DatabaseTracker.startProfileMatchups(puuid, shard, filter);
            return ApiResult.pending(metadata(lastUpdate, true, filter));
        }
        ProfileMatchups response = matchups.withMinGames(requestFilter.minGames())
            .withMetadata(metadata(lastUpdate, false, filter));
        return ApiResult.ready(response, response.metadata());
    }

    public ProfileMatchups getMatchups(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return null;
        ProfileMatchups matchups = RedisClient.get(matchupsKey(puuid, filter), ProfileMatchups.class);
        if (matchups != null) return matchups;
        matchups = MongoDB.findProfileMatchups(puuid, filter);
        if (matchups != null) cacheMatchups(puuid, filter, matchups);
        return matchups;
    }

    public List<ProfileIndexable> getIndexables() {
        return MongoDB.findProfileIndexables();
    }

    public boolean refresh(LeagueShard shard, String puuid, boolean rebuild) {
        Summoner profile = SummonerService.find(puuid, shard);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return false;
        boolean refreshed = refreshStatistics(puuid, shard, Filter.summoner(), rebuild);
        if (refreshed) invalidate(puuid, shard);
        return refreshed;
    }

    public boolean refreshStatistics(String puuid, LeagueShard shard, Filter filter, boolean rebuild) {
        if (puuid == null || shard == null || filter == null) return false;
        ProfileStatistics statistics = rebuild ? null : getStatistics(puuid, filter);
        long afterTime = rebuild || statistics == null || statistics.timeEnd == filter.timeStart() ? 0 : statistics.timeEnd + 1;
        List<Match> matches = MongoDB.findProfileStatisticsMatches(puuid, shard, filter, afterTime, currentEnd(filter));
        statistics = ProfileAnalyzer.updateStatistics(statistics, matches, puuid, filter);
        statistics.lastUpdate = System.currentTimeMillis();
        boolean saved = MongoDB.upsertProfileStatistics(puuid, filter, statistics);
        if (saved) {
            cacheStatistics(puuid, filter, statistics);
            RedisClient.delete(recentMatchesKey(puuid, filter));
        }
        return saved;
    }

    public boolean refreshStatistics(String puuid, LeagueShard shard, SeasonUtils.SeasonRange season, boolean rebuild) {
        return season != null && refreshStatistics(puuid, shard, Filter.summoner(season.start(), season.end()), rebuild);
    }

    public boolean refreshMatchups(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return false;
        ProfileMatchups matchups = ProfileAnalyzer.matchups(
            MongoDB.findProfileStatisticsMatches(puuid, shard, filter, 0, 0), puuid, filter);
        boolean saved = MongoDB.upsertProfileMatchups(puuid, filter, matchups);
        if (saved) cacheMatchups(puuid, filter, matchups);
        return saved;
    }

    public boolean refreshActivity(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null)
            return false;
        ProfileActivity activity = ProfileAnalyzer.activity(
            MongoDB.findProfileStatisticsMatches(puuid, shard, filter, 0, 0), puuid, filter);
        boolean saved = MongoDB.upsertProfileActivity(puuid, filter, activity);
        if (saved) cacheActivity(puuid, filter, activity);
        return saved;
    }

    public boolean refreshCanonicalAggregates(LeagueShard shard, String puuid) {
        return shard != null && puuid != null && !puuid.isBlank()
            && refreshStatistics(puuid, shard, canonicalStatisticsFilter(), true)
            && refreshActivity(shard, puuid, canonicalActivityFilter())
            && refreshMatchups(puuid, shard, canonicalMatchupsFilter());
    }

    public List<ProfileIndexable> refreshIndexables() {
        return MongoDB.refreshProfileIndexables();
    }

    public int refreshAll(LeagueShard shard, boolean rebuild) {
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        if (season == null || !ALL_PROFILE_STATS_REFRESH_RUNNING.compareAndSet(false, true)) return -1;
        try {
            int refreshed = 0;
            for (String puuid : MatchService.getSeasonPuuids(shard, season.start(), season.end()))
                if (refresh(shard, puuid, rebuild)) refreshed++;
            return refreshed;
        } finally {
            ALL_PROFILE_STATS_REFRESH_RUNNING.set(false);
        }
    }

    public static void invalidate(String puuid, LeagueShard shard) {
        if (shard == null || puuid == null || puuid.isBlank()) return;
        RedisClient.delete(RedisKey.PROFILE_PAGE.of(shard.name(), puuid));
    }

    public static void startRefresh(Summoner summoner, LeagueShard shard) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || shard == null) return;

        List<Filter> filters = MongoDB.findProfileRefreshFilters(summoner.puuid());
        MongoDB.pruneProfileNonCanonical(summoner.puuid(), canonicalStatisticsFilter(), canonicalActivityFilter(),
            canonicalMatchupsFilter());
        invalidateRefreshCaches(summoner.puuid(), shard, filters);
        DatabaseTracker.startProfileRefresh(summoner, shard);
    }

    // ============================================================================

    private static boolean isCurrent(ProfileStatistics statistics) {
        return statistics != null && statistics.hasChampionContext();
    }

    static boolean isStale(long lastUpdate) {
        return isStale(lastUpdate, System.currentTimeMillis());
    }

    static boolean isStale(long lastUpdate, long now) {
        return lastUpdate <= 0 || now - lastUpdate >= TimeConstant.WEEK;
    }

    private static long activityLastUpdate(ProfileActivity activity) {
        return activity == null || activity.coverage() == null ? 0 : activity.coverage().calculatedAt();
    }

    private static ResponseMetadata metadata(long lastUpdate, boolean refresh, Filter filter) {
        return new ResponseMetadata(null, lastUpdate > 0 ? lastUpdate : null, refresh, filter);
    }

    private static Filter canonicalStatisticsFilter() {
        return Filter.summoner();
    }

    private static Filter canonicalActivityFilter() {
        return Filter.summoner(0, 0);
    }

    private static Filter canonicalMatchupsFilter() {
        return Filter.summoner();
    }

    private static boolean isCanonicalActivity(Filter filter) {
        return filter != null && canonicalActivityFilter().toSummonerKey().equals(filter.toSummonerKey());
    }

    private static boolean isCanonicalMatchups(Filter filter) {
        return filter != null && canonicalMatchupsFilter().toSummonerKey().equals(filter.toSummonerKey());
    }

    private static void cacheStatistics(String puuid, Filter filter, ProfileStatistics statistics) {
        RedisClient.set(RedisKey.PROFILE_STATISTICS, statistics, puuid, filter.toSummonerKey());
    }

    private static void cacheActivity(String puuid, Filter filter, ProfileActivity activity) {
        RedisClient.set(RedisKey.PROFILE_ACTIVITY, activity, puuid, filter.toSummonerKey());
    }

    private static void cacheMatchups(String puuid, Filter filter, ProfileMatchups matchups) {
        RedisClient.set(RedisKey.PROFILE_MATCHUPS, matchups, puuid, filter.toSummonerKey());
    }

    private static long currentEnd(Filter filter) {
        return filter.timeEnd() == 0 ? System.currentTimeMillis() : Math.min(System.currentTimeMillis(), filter.timeEnd());
    }

    private static String statisticsKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_STATISTICS.of(puuid, filter.toSummonerKey());
    }

    private static String recentMatchesKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_RECENT_MATCHES.of(puuid, filter.toSummonerKey());
    }

    private static String activityKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_ACTIVITY.of(puuid, filter.toSummonerKey());
    }

    private static String matchupsKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_MATCHUPS.of(puuid, filter.toSummonerKey());
    }

    private static void invalidateRefreshCaches(String puuid, LeagueShard shard, List<Filter> filters) {
        List<String> keys = new ArrayList<>();
        List<Filter> values = new ArrayList<>(filters);
        values.add(canonicalStatisticsFilter());
        values.add(canonicalActivityFilter());
        values.add(canonicalMatchupsFilter());
        keys.add(RedisKey.PROFILE_PAGE.of(shard.name(), puuid));
        for (Filter filter : values) {
            keys.add(statisticsKey(puuid, filter));
            keys.add(recentMatchesKey(puuid, filter));
            keys.add(activityKey(puuid, filter));
            keys.add(matchupsKey(puuid, filter));
        }
        RedisClient.delete(keys);
    }

    private static boolean isReady(SummonerView page) {
        return page.summoner() != null
            && page.summoner().puuid() != null
            && !page.summoner().puuid().isBlank()
            && page.overview() != null
            && page.overview().statistics() != null
            && page.overview().statistics().total != null
            && page.overview().statistics().total.games >= MIN_PROFILE_GAMES;
    }

    private SummonerView withRecentMatches(SummonerView page, LeagueShard shard, Filter filter) {
        List<MatchResult> recentMatches = getRecentMatches(page.summoner().puuid(), shard, filter);
        return SummonerView.from(page.summoner(), page.ranks(), page.overview().statistics(),
            page.overview().masteries(), page.overview().champions(), recentMatches);
    }

    private static SummonerView withoutRecentMatches(SummonerView page) {
        return SummonerView.from(page.summoner(), page.ranks(), page.overview().statistics(),
            page.overview().masteries(), page.overview().champions(), List.of());
    }

    private static boolean isReadyFuture(CompletableFuture<?> future) {
        return future != null && future.isDone() && !future.isCompletedExceptionally();
    }

    private static <T> T completed(CompletableFuture<T> future) {
        return future.join();
    }
}
