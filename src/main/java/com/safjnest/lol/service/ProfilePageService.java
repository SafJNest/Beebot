package com.safjnest.lol.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.safjnest.lol.model.ApiResult;
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
    private static final int MIN_PROFILE_GAMES = 5;
    private static final int TTL_PROFILE_PAGE = 60 * 5;

    private final ProfileStatisticsService statisticsService = new ProfileStatisticsService();

    public ApiResult<SummonerView> get(LeagueShard shard, String puuid) {
        String key = RedisKey.PROFILE_PAGE.of(shard.name(), puuid);
        SummonerView cached = RedisClient.get(key, SummonerView.class);
        if (cached != null && isReady(cached)) return ApiResult.ready(cached);

        Summoner profile = LeagueService.getProfileBaseFromDatabase(puuid, shard);
        if (profile == null || profile.summonerId() == 0) {
            ProfileBootstrapService.enqueue(shard, puuid);
            return ApiResult.pending();
        }

        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        ProfileStatistics databaseStatistics = statisticsService.getDatabase(profile.summonerId(), season);
        List<Rank> databaseRanks = databaseStatistics != null
            ? LeagueService.getProfileRanksFromDatabase(profile.summonerId())
            : List.of();

        List<Rank> profileRanks = !databaseRanks.isEmpty() ? databaseRanks : ranks(profile, shard);
        List<Mastery> profileMasteries = masteries(profile);
        ProfileStatistics aggregate = databaseStatistics != null
            ? databaseStatistics
            : statisticsService.getRedis(profile.summonerId(), season);
        if (databaseStatistics == null) Tracker.enqueueProfileStatistics(profile.summonerId(), season);

        SummonerView page = SummonerView.from(profile, profileRanks, aggregate, profileMasteries);
        if (databaseStatistics != null && !databaseRanks.isEmpty()) {
            RedisClient.set(key, page, TTL_PROFILE_PAGE);
            return ApiResult.ready(page);
        }
        return ApiResult.partial(page);
    }

    public ApiResult<SummonerView> get(LeagueShard shard, String gameName, String tagLine) {
        String puuid = LeagueService.getPuuidByRiotId(gameName, tagLine, shard);
        return puuid != null ? get(shard, puuid) : ApiResult.notFound();
    }

    public boolean refresh(LeagueShard shard, String puuid, boolean rebuild) {
        Summoner profile = LeagueService.getProfileBaseFromDatabase(puuid, shard);
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

    private List<Mastery> masteries(Summoner profile) {
        return profile.summonerId() == 0 ? List.of() : LeagueService.getProfileMasteries(profile.summonerId());
    }

    private boolean isReady(SummonerView page) {
        return page.summoner() != null
            && page.summoner().summonerId() > 0
            && page.ranks() != null
            && !page.ranks().isEmpty()
            && page.overview() != null
            && page.overview().statistics() != null
            && page.overview().statistics().total != null
            && page.overview().statistics().total.games >= MIN_PROFILE_GAMES;
    }
}
