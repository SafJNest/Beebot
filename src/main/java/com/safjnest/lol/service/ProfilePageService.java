package com.safjnest.lol.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.tracker.DatabaseTracker;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfilePageService {

    private static final AtomicBoolean ALL_PROFILE_STATS_REFRESH_RUNNING = new AtomicBoolean(false);
    private static final int MIN_PROFILE_GAMES = 5;

    private final ProfileStatisticsService statisticsService = new ProfileStatisticsService();

    public ApiResult<SummonerView> get(LeagueShard shard, String puuid) {
        String key = RedisKey.PROFILE_PAGE.of(shard.name(), puuid);
        SummonerView cached = RedisClient.get(key, SummonerView.class);
        if (cached != null && isReady(cached)) {
            return ApiResult.ready(withRecentMatches(cached, shard, Filter.summoner()));
        }

        CompletableFuture<Summoner> profileFuture = LeagueService.getAsyncSummoner(puuid, shard);
        CompletableFuture<List<Rank>> ranksFuture = LeagueService.getAsyncRanks(puuid, shard);
        CompletableFuture<List<Mastery>> masteriesFuture = LeagueService.getAsyncMasteries(puuid, shard);
        if (!isReadyFuture(profileFuture) || !isReadyFuture(ranksFuture) || !isReadyFuture(masteriesFuture)) {
            return ApiResult.pending();
        }

        Summoner profile = completed(profileFuture);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return ApiResult.notFound();

        Filter filter = Filter.summoner();
        ProfileStatistics databaseStatistics = statisticsService.get(profile.puuid(), filter);
        List<Rank> profileRanks = completed(ranksFuture);
        List<Mastery> profileMasteries = completed(masteriesFuture);
        if (databaseStatistics == null) DatabaseTracker.startProfileStatistics(profile, filter);

        List<MatchResult> recentMatches = databaseStatistics == null
            ? List.of()
            : statisticsService.getRecentMatches(profile.puuid(), shard, filter);
        SummonerView page = SummonerView.from(profile, profileRanks, databaseStatistics, profileMasteries, recentMatches);
        if (databaseStatistics != null) {
            RedisClient.set(RedisKey.PROFILE_PAGE, withoutRecentMatches(page), shard.name(), puuid);
            return ApiResult.ready(page);
        }
        return ApiResult.partial(page);
    }

    public ApiResult<SummonerView> get(LeagueShard shard, String gameName, String tagLine) {
        CompletableFuture<String> puuidFuture = LeagueService.getAsyncPuuidByRiotId(gameName, tagLine, shard);
        if (!isReadyFuture(puuidFuture)) return ApiResult.pending();
        String puuid = completed(puuidFuture);
        return puuid != null ? get(shard, puuid) : ApiResult.notFound();
    }

    public boolean refresh(LeagueShard shard, String puuid, boolean rebuild) {
        Summoner profile = LeagueService.getSavedSummoner(puuid, shard);
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return false;

        Filter filter = Filter.summoner();
        boolean refreshed = statisticsService.refresh(puuid, shard, filter, rebuild);
        if (refreshed) LeagueService.invalidateProfilePage(puuid, shard);
        return refreshed;
    }

    public int refreshAll(LeagueShard shard, boolean rebuild) {
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        if (season == null || !ALL_PROFILE_STATS_REFRESH_RUNNING.compareAndSet(false, true)) return -1;
        try {
            int refreshed = 0;
            for (String puuid : LeagueService.getProfileSeasonPuuids(shard, season.start(), season.end())) {
                if (refresh(shard, puuid, rebuild)) refreshed++;
            }
            return refreshed;
        } finally {
            ALL_PROFILE_STATS_REFRESH_RUNNING.set(false);
        }
    }

    // ============================================================================

    private boolean isReady(SummonerView page) {
        return page.summoner() != null
            && page.summoner().puuid() != null
            && !page.summoner().puuid().isBlank()
            && page.overview() != null
            && page.overview().statistics() != null
            && page.overview().statistics().total != null
            && page.overview().statistics().total.games >= MIN_PROFILE_GAMES;
    }

    private SummonerView withRecentMatches(SummonerView page, LeagueShard shard, Filter filter) {
        List<MatchResult> recentMatches = statisticsService.getRecentMatches(
            page.summoner().puuid(), shard, filter);
        return SummonerView.from(
            page.summoner(),
            page.ranks(),
            page.overview().statistics(),
            page.overview().masteries(),
            page.overview().champions(),
            recentMatches
        );
    }

    private static SummonerView withoutRecentMatches(SummonerView page) {
        return SummonerView.from(
            page.summoner(),
            page.ranks(),
            page.overview().statistics(),
            page.overview().masteries(),
            page.overview().champions(),
            List.of()
        );
    }

    private static boolean isReadyFuture(CompletableFuture<?> future) {
        return future != null && future.isDone() && !future.isCompletedExceptionally();
    }

    private static <T> T completed(CompletableFuture<T> future) {
        return future.join();
    }
}
