package com.safjnest.lol.service;

import java.util.concurrent.CompletableFuture;

import com.safjnest.lol.model.ActivityFilter;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.tracker.DatabaseTracker;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileMatchupsPageService {

    private final ProfileMatchupsService matchupsService = new ProfileMatchupsService();

    public ApiResult<ProfileMatchups> get(LeagueShard shard, String puuid, ActivityFilter requestFilter) {
        if (shard == null || puuid == null || puuid.isBlank() || requestFilter == null) return ApiResult.notFound();

        CompletableFuture<Summoner> profileFuture = LeagueService.getAsyncSummoner(puuid, shard);
        if (!isReadyFuture(profileFuture)) return ApiResult.pending();
        Summoner profile = profileFuture.join();
        if (profile == null || profile.puuid() == null || profile.puuid().isBlank()) return ApiResult.notFound();

        Filter filter = requestFilter.aggregationFilter();
        ProfileMatchups matchups = matchupsService.get(puuid, filter);
        if (matchups == null) {
            DatabaseTracker.startProfileMatchups(puuid, shard, filter);
            return ApiResult.pending();
        }
        return ApiResult.ready(matchups.withMinGames(requestFilter.minGames()));
    }

    // ============================================================================

    private static boolean isReadyFuture(CompletableFuture<?> future) {
        return future != null && future.isDone() && !future.isCompletedExceptionally();
    }
}
