package com.safjnest.lol.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfilePageService {

    private static final AtomicBoolean ALL_PROFILE_STATS_REFRESH_RUNNING = new AtomicBoolean(false);
    private static final int TTL_PROFILE_PAGE = 60 * 5;

    private final ProfileStatisticsService statisticsService = new ProfileStatisticsService();

    public SummonerView get(LeagueShard shard, String puuid) {
        String key = RedisKey.PROFILE_PAGE.of(shard.name(), puuid);
        SummonerView cached = RedisClient.get(key, SummonerView.class);
        if (cached != null) return cached;

        Summoner profile = LeagueService.getProfileBase(puuid, shard);
        if (profile == null) return null;

        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        CompletableFuture<List<Rank>> ranks = CompletableFuture.supplyAsync(() -> ranks(profile, shard));
        CompletableFuture<List<Mastery>> masteries = CompletableFuture.supplyAsync(() -> masteries(profile));
        CompletableFuture<ProfileStatistics> statistics = CompletableFuture.supplyAsync(() -> statistics(profile, season));

        ProfileStatistics aggregate = statistics.join();
        SummonerView page = SummonerView.from(profile, ranks.join(), aggregate, masteries.join());
        if (aggregate != null) RedisClient.set(key, page, TTL_PROFILE_PAGE);
        return page;
    }

    public SummonerView get(LeagueShard shard, String gameName, String tagLine) {
        String puuid = LeagueService.getPuuidByRiotId(gameName, tagLine, shard);
        return puuid != null ? get(shard, puuid) : null;
    }

    public boolean refresh(LeagueShard shard, String puuid, boolean rebuild) {
        Summoner profile = LeagueService.getProfileBase(puuid, shard);
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        if (profile == null || profile.summonerId() == 0) return false;

        boolean refreshed = statisticsService.refresh(profile.summonerId(), season, rebuild);
        if (refreshed) RedisClient.delete(RedisKey.PROFILE_PAGE.of(shard.name(), puuid));
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

    private List<Rank> ranks(Summoner profile, LeagueShard shard) {
        return profile.summonerId() != 0
            ? LeagueService.getProfileRanks(profile.summonerId())
            : LeagueService.getProfileRanks(profile.puuid(), shard);
    }

    private ProfileStatistics statistics(Summoner profile, SeasonUtils.SeasonRange season) {
        if (profile.summonerId() == 0 || season == null) return null;

        ProfileStatistics statistics = statisticsService.get(profile.summonerId(), season);
        if (statistics == null) Tracker.enqueueProfileStatistics(profile.summonerId(), season);
        return statistics;
    }

    private List<Mastery> masteries(Summoner profile) {
        return profile.summonerId() == 0 ? List.of() : LeagueService.getProfileMasteries(profile.summonerId());
    }
}
