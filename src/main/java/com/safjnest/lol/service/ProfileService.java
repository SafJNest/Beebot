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
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.TimeConstant;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ProfileService {

    private static final AtomicBoolean ALL_PROFILE_STATS_REFRESH_RUNNING = new AtomicBoolean(false);
    private static final TypeReference<List<MatchResult>> RECENT_MATCHES_TYPE = new TypeReference<>() {};
    private static final int MIN_PROFILE_GAMES = 5;
    private static final long STALE_BASE_MILLIS = TimeConstant.DAY * 30L;
    private static final long STALE_JITTER_DAYS = 14;
    private static final long STALE_LAST_SEEN_MILLIS = TimeConstant.DAY * 60L;

    public ApiResult<SummonerView> get(LeagueShard shard, String puuid) {
        Filter filter = Filter.canonical();
        String key = RedisKey.SUMMONER_OVERVIEW.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
        SummonerView cached = RedisClient.get(key, SummonerView.class);
        if (cached != null && isReady(cached)) {
            ProfileStatistics statistics = getStatistics(cached.summoner(), shard, filter);
            boolean refresh = isStale(puuid, statistics == null ? 0 : statistics.lastUpdate);
            SummonerView page = SummonerView.from(cached.summoner(), cached.ranks(), statistics,
                cached.overview().masteries(), cached.overview().champions(),
                getRecentMatches(puuid, shard, filter)).withMetadata(
                metadata(statistics == null ? 0 : statistics.lastUpdate, refresh, filter));
            return refresh ? ApiResult.partial(page, page.metadata()) : ApiResult.ready(page, page.metadata());
        }

        CompletableFuture<Summoner> profileFuture = SummonerService.getAsync(puuid, shard);
        if (!isReadyFuture(profileFuture))
            return ApiResult.pending(metadata(0, true, filter));

        Summoner profile = completed(profileFuture);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return ApiResult.notFound();

        ProfileStatistics statistics = getStatistics(profile, shard, filter);
        Map<GameQueueType, Rank> ranks = RankService.find(puuid, shard);
        List<Mastery> masteries = MasteryService.find(puuid, shard);
        boolean refresh = ranks == null || masteries == null
            || statistics == null || isStale(profile.puuid(), statistics.lastUpdate);

        List<MatchResult> recentMatches = statistics == null ? List.of() : getRecentMatches(profile.puuid(), shard, filter);
        SummonerView page = SummonerView.from(profile, ranks, statistics, masteries, recentMatches)
            .withMetadata(metadata(statistics == null ? 0 : statistics.lastUpdate, refresh, filter));
        if (statistics != null && !refresh) {
            RedisClient.set(RedisKey.SUMMONER_OVERVIEW, withoutRecentMatches(page), LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
            return ApiResult.ready(page, page.metadata());
        }
        return ApiResult.partial(page, page.metadata());
    }

    public ApiResult<SummonerView> get(LeagueShard shard, String gameName, String tagLine) {
        CompletableFuture<String> puuidFuture = SummonerService.getPuuidByRiotIdAsync(gameName, tagLine, shard);
        if (!isReadyFuture(puuidFuture)) return ApiResult.pending(metadata(0, true, Filter.canonical()));
        String puuid = completed(puuidFuture);
        return puuid != null ? get(shard, puuid) : ApiResult.notFound();
    }

    public ProfileStatistics getStatistics(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || shard == null || filter == null) return null;
        ProfileStatistics statistics = RedisClient.get(statisticsKey(puuid, shard, filter), ProfileStatistics.class);
        if (isCurrent(statistics)) return statistics;
        statistics = MongoDB.findProfileStatistics(puuid, filter);
        if (!isCurrent(statistics)) return null;
        cacheStatistics(puuid, shard, filter, statistics);
        return statistics;
    }

    public ProfileStatistics getStatistics(Summoner summoner, LeagueShard shard, Filter filter) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || shard == null) return null;
        ProfileStatistics statistics = getStatistics(summoner.puuid(), shard, filter);
        if (statistics == null) ComputeScheduler.startProfileStatistics(summoner, filter);
        else if (isStale(summoner.puuid(), statistics.lastUpdate)) enqueueStaleStatistics(summoner, shard, filter);
        return statistics;
    }

    public Map<String, ProfileStatistics> getStatistics(List<String> puuids, LeagueShard shard, Filter filter) {
        Map<String, ProfileStatistics> result = new HashMap<>();
        if (shard == null || filter == null || puuids == null || puuids.isEmpty()) return result;
        List<String> missing = new ArrayList<>();
        List<String> keys = new ArrayList<>(puuids.size());
        Map<String, String> puuidsByKey = new HashMap<>();
        for (String puuid : puuids) {
            String key = statisticsKey(puuid, shard, filter);
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
                cacheStatistics(entry.getKey(), shard, filter, entry.getValue());
            }
        }
        return result;
    }

    public List<MatchResult> getRecentMatches(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || filter == null) return List.of();
        String key = recentMatchesKey(puuid, shard, filter);
        List<MatchResult> cached = RedisClient.get(key, RECENT_MATCHES_TYPE);
        if (cached != null) return cached;
        List<MatchResult> result = MongoDB.findProfileRecentMatches(puuid, shard, filter, 5);
        RedisClient.set(RedisKey.SUMMONER_RECENT_MATCHES, result, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
        return result;
    }

    public ApiResult<ProfileActivity> getActivity(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null) return ApiResult.notFound();
        ProfileActivity activity = RedisClient.get(activityKey(puuid, shard, filter), ProfileActivity.class);
        if (activity == null) {
            activity = MongoDB.findProfileActivity(puuid, filter);
        }
        if (activity != null && !isStale(puuid, activityLastUpdate(activity))) {
            cacheActivity(puuid, shard, filter, activity);
            ProfileActivity response = activity.withMetadata(metadata(activityLastUpdate(activity), false, filter));
            return ApiResult.ready(response, response.metadata());
        }

        long lastUpdate = activityLastUpdate(activity);
        if (activity != null) {
            enqueueStaleActivity(puuid, shard, filter);
            ProfileActivity response = activity.withMetadata(metadata(lastUpdate, true, filter));
            return ApiResult.partial(response, response.metadata());
        }
        ComputeScheduler.startProfileActivity(puuid, shard, filter);
        return ApiResult.pending(metadata(0, true, filter));
    }

    public ApiResult<ProfileMatchups> getMatchups(LeagueShard shard, String puuid, ActivityFilter requestFilter) {
        if (shard == null || puuid == null || puuid.isBlank() || requestFilter == null) return ApiResult.notFound();
        Filter filter = requestFilter.aggregationFilter();
        ProfileMatchups matchups = getMatchups(shard, puuid, filter);
        if (matchups != null && !matchups.hasLeafMatchups()) matchups = null;
        long lastUpdate = matchups == null ? 0 : matchups.lastUpdate();
        if (matchups != null && isStale(puuid, lastUpdate)) {
            enqueueStaleMatchups(puuid, shard, filter);
            ProfileMatchups response = matchups.withMinGames(requestFilter.minGames())
                .withMetadata(metadata(lastUpdate, true, filter));
            return ApiResult.partial(response, response.metadata());
        }
        if (matchups != null) {
            ProfileMatchups response = matchups.withMinGames(requestFilter.minGames())
                .withMetadata(metadata(lastUpdate, false, filter));
            return ApiResult.ready(response, response.metadata());
        }
        CompletableFuture<Summoner> profileFuture = SummonerService.getAsync(puuid, shard);
        if (!isReadyFuture(profileFuture)) return ApiResult.pending(metadata(0, true, filter));
        Summoner profile = completed(profileFuture);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return ApiResult.notFound();
        ComputeScheduler.startProfileMatchups(puuid, shard, filter);
        return ApiResult.pending(metadata(0, true, filter));
    }

    public ProfileMatchups getMatchups(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null) return null;
        ProfileMatchups matchups = RedisClient.get(matchupsKey(puuid, shard, filter), ProfileMatchups.class);
        if (matchups != null && matchups.hasLeafMatchups()) return matchups;
        matchups = MongoDB.findProfileMatchups(puuid, filter);
        if (matchups == null || !matchups.hasLeafMatchups()) return null;
        cacheMatchups(puuid, shard, filter, matchups);
        return matchups;
    }

    public List<ProfileIndexable> getIndexables() {
        return MongoDB.findProfileIndexables();
    }

    public boolean generateStatistics(String puuid, LeagueShard shard, Filter filter, boolean rebuild) {
        if (puuid == null || shard == null || filter == null) return false;
        ProfileStatistics statistics = rebuild ? null : getStatistics(puuid, shard, filter);
        long afterTime = rebuild || statistics == null || statistics.timeEnd == filter.timeStart() ? 0 : statistics.timeEnd + 1;
        ProfileStatistics result = statistics == null ? new ProfileStatistics(filter.timeStart()) : statistics;
        MongoDB.forEachProfileStatisticsMatch(puuid, shard, filter, afterTime, currentEnd(filter),
            match -> result.accumulate(match, puuid, filter));
        result.finish();
        statistics = result;
        statistics.lastUpdate = System.currentTimeMillis();
        boolean saved = MongoDB.upsertProfileStatistics(puuid, filter, statistics);
        if (saved) {
            cacheStatistics(puuid, shard, filter, statistics);
            RedisClient.delete(recentMatchesKey(puuid, shard, filter));
            if (Filter.canonical().toSummonerKey().equals(filter.toSummonerKey()))
                CompetitiveService.refreshFromStatistics(puuid, shard, statistics);
        }
        return saved;
    }

    public boolean generateMatchups(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return false;
        ProfileAnalyzer.MatchupsAccumulator accumulator = ProfileAnalyzer.matchupsAccumulator(puuid, filter);
        MongoDB.forEachProfileStatisticsMatch(puuid, shard, filter, 0, 0, accumulator::accept);
        ProfileMatchups matchups = accumulator.finish();
        boolean saved = MongoDB.upsertProfileMatchups(puuid, filter, matchups);
        if (saved) cacheMatchups(puuid, shard, filter, matchups);
        return saved;
    }

    public boolean generateActivity(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null)
            return false;
        ProfileActivity.Accumulator accumulator = ProfileActivity.accumulator(puuid, filter);
        MongoDB.forEachProfileStatisticsMatch(puuid, shard, filter, 0, 0, accumulator::accept);
        ProfileActivity activity = accumulator.finish();
        boolean saved = MongoDB.upsertProfileActivity(puuid, filter, activity);
        if (saved) cacheActivity(puuid, shard, filter, activity);
        return saved;
    }

    public boolean refreshProfile(LeagueShard shard, String puuid) {
        if (shard == null || puuid == null || puuid.isBlank()) return false;
        Filter filter = Filter.canonical();
        ProfileAnalyzer.ProfileRefreshAccumulator accumulator = ProfileAnalyzer.refreshAccumulator(
            puuid, filter, filter, filter);
        MongoDB.forEachProfileStatisticsMatch(puuid, shard, filter, 0, 0, accumulator::accept);
        ProfileAnalyzer.ProfileRefresh refresh = accumulator.finish();
        refresh.statistics().lastUpdate = System.currentTimeMillis();
        boolean statisticsSaved = MongoDB.upsertProfileStatistics(puuid, filter, refresh.statistics());
        boolean activitySaved = MongoDB.upsertProfileActivity(puuid, filter, refresh.activity());
        boolean matchupsSaved = MongoDB.upsertProfileMatchups(puuid, filter, refresh.matchups());
        if (!statisticsSaved || !activitySaved || !matchupsSaved) return false;
        cacheStatistics(puuid, shard, filter, refresh.statistics());
        cacheActivity(puuid, shard, filter, refresh.activity());
        cacheMatchups(puuid, shard, filter, refresh.matchups());
        RedisClient.delete(recentMatchesKey(puuid, shard, filter));
        CompetitiveService.refreshFromStatistics(puuid, shard, refresh.statistics());
        return true;
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
                if (generateStatistics(puuid, shard, Filter.canonical(), rebuild)) {
                    invalidate(puuid, shard);
                    refreshed++;
                }
            return refreshed;
        } finally {
            ALL_PROFILE_STATS_REFRESH_RUNNING.set(false);
        }
    }

    public static void invalidate(String puuid, LeagueShard shard) {
        if (shard == null || puuid == null || puuid.isBlank()) return;
        RedisClient.delete(RedisKey.SUMMONER_OVERVIEW.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid));
    }

    public static void markManuallySeen(String puuid) {
        MongoDB.touchSummonerLastSeen(puuid);
    }

    public static void refreshProfile(Summoner summoner, LeagueShard shard) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || shard == null) return;
        ComputeScheduler.refreshProfile(summoner, shard);
    }

    // ============================================================================

    private static boolean isCurrent(ProfileStatistics statistics) {
        return statistics != null && statistics.hasLeafStatistics();
    }

    static boolean isStale(String puuid, long lastUpdate) {
        return isStale(puuid, lastUpdate, System.currentTimeMillis());
    }

    static boolean isStale(String puuid, long lastUpdate, long now) {
        if (lastUpdate <= 0 || puuid == null || puuid.isBlank()) return true;
        long lastSeenAt = MongoDB.findSummonerLastSeen(puuid);
        return isStale(puuid, lastUpdate, lastSeenAt, now);
    }

    static boolean isStale(String puuid, long lastUpdate, long lastSeenAt, long now) {
        if (lastUpdate <= 0 || puuid == null || puuid.isBlank()) return true;
        if (lastSeenAt <= 0 || now - lastSeenAt > STALE_LAST_SEEN_MILLIS) return false;
        long jitter = Math.floorMod(puuid.hashCode(), (int) (STALE_JITTER_DAYS + 1)) * TimeConstant.DAY;
        return now - lastUpdate >= STALE_BASE_MILLIS + jitter;
    }

    private static long activityLastUpdate(ProfileActivity activity) {
        return activity == null || activity.coverage() == null ? 0 : activity.coverage().calculatedAt();
    }

    private static ResponseMetadata metadata(long lastUpdate, boolean refresh, Filter filter) {
        return new ResponseMetadata(null, lastUpdate > 0 ? lastUpdate : null, refresh, filter);
    }

    private static void enqueueStaleStatistics(Summoner summoner, LeagueShard shard, Filter filter) {
        if (summoner == null || shard == null || filter == null || !isStaleEligible(summoner.puuid())) return;
        ComputeScheduler.startStaleProfileStatistics(summoner, filter);
    }

    private static void enqueueStaleActivity(String puuid, LeagueShard shard, Filter filter) {
        if (isStaleEligible(puuid)) ComputeScheduler.startStaleProfileActivity(puuid, shard, filter);
    }

    private static void enqueueStaleMatchups(String puuid, LeagueShard shard, Filter filter) {
        if (isStaleEligible(puuid)) ComputeScheduler.startStaleProfileMatchups(puuid, shard, filter);
    }

    private static boolean isStaleEligible(String puuid) {
        long lastSeenAt = MongoDB.findSummonerLastSeen(puuid);
        return lastSeenAt > 0 && System.currentTimeMillis() - lastSeenAt <= STALE_LAST_SEEN_MILLIS;
    }

    private static void cacheStatistics(String puuid, LeagueShard shard, Filter filter, ProfileStatistics statistics) {
        RedisClient.set(RedisKey.SUMMONER_STATISTICS, statistics, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static void cacheActivity(String puuid, LeagueShard shard, Filter filter, ProfileActivity activity) {
        RedisClient.set(RedisKey.SUMMONER_ACTIVITY, activity, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static void cacheMatchups(String puuid, LeagueShard shard, Filter filter, ProfileMatchups matchups) {
        RedisClient.set(RedisKey.SUMMONER_MATCHUPS, matchups, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static long currentEnd(Filter filter) {
        return filter.timeEnd() == 0 ? System.currentTimeMillis() : Math.min(System.currentTimeMillis(), filter.timeEnd());
    }

    private static String statisticsKey(String puuid, LeagueShard shard, Filter filter) {
        return RedisKey.SUMMONER_STATISTICS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static String recentMatchesKey(String puuid, LeagueShard shard, Filter filter) {
        return RedisKey.SUMMONER_RECENT_MATCHES.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static String activityKey(String puuid, LeagueShard shard, Filter filter) {
        return RedisKey.SUMMONER_ACTIVITY.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static String matchupsKey(String puuid, LeagueShard shard, Filter filter) {
        return RedisKey.SUMMONER_MATCHUPS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
    }

    private static boolean isReady(SummonerView page) {
        return page.summoner() != null
            && page.summoner().puuid() != null
            && !page.summoner().puuid().isBlank()
            && page.overview() != null
            && page.overview().statistics() != null
            && page.overview().statistics().total().games >= MIN_PROFILE_GAMES;
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
