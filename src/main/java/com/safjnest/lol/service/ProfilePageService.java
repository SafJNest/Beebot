package com.safjnest.lol.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ProfileMastery;
import com.safjnest.lol.model.ProfileChampion;
import com.safjnest.lol.model.ProfilePageData;
import com.safjnest.lol.model.ProfileStatistics;
import com.safjnest.lol.model.SummonerProfile;
import com.safjnest.lol.model.SummonerRank;
import com.safjnest.lol.model.Stats;
import com.safjnest.lol.utils.ChampionUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

/** Domain facade for a complete profile page. It never exposes partial profile helpers. */
public class ProfilePageService {

    private static final AtomicBoolean ALL_PROFILE_STATS_REFRESH_RUNNING = new AtomicBoolean(false);

    private final ProfileStatisticsService statisticsService = new ProfileStatisticsService();

    public ProfilePageData get(LeagueShard shard, String puuid) {
        SummonerProfile profile = LeagueService.getProfileBase(puuid, shard);
        if (profile == null) return null;

        LeagueHandler.SeasonRange season = LeagueHandler.getCurrentSeasonRange();
        CompletableFuture<List<SummonerRank>> ranks = CompletableFuture.supplyAsync(() -> ranks(profile, shard));
        CompletableFuture<List<ProfileMastery>> masteries = CompletableFuture.supplyAsync(() -> masteries(profile));
        CompletableFuture<ProfileStatistics> statistics = CompletableFuture.supplyAsync(() -> statistics(profile, season));

        ProfileStatistics aggregate = statistics.join();
        return new ProfilePageData(profile, ranks.join(), aggregate, masteries.join(), champions(aggregate));
    }

    public ProfilePageData get(LeagueShard shard, String gameName, String tagLine) {
        String puuid = LeagueService.getPuuidByRiotId(gameName, tagLine, shard);
        return puuid != null ? get(shard, puuid) : null;
    }

    public boolean refresh(LeagueShard shard, String puuid, boolean rebuild) {
        SummonerProfile profile = LeagueService.getProfileBase(puuid, shard);
        LeagueHandler.SeasonRange season = LeagueHandler.getCurrentSeasonRange();
        return profile != null && profile.summonerId() != 0 && statisticsService.refresh(profile.summonerId(), season, rebuild);
    }

    public int refreshAll(LeagueShard shard, boolean rebuild) {
        LeagueHandler.SeasonRange season = LeagueHandler.getCurrentSeasonRange();
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

    private List<SummonerRank> ranks(SummonerProfile profile, LeagueShard shard) {
        return profile.summonerId() != 0
            ? LeagueService.getProfileRanks(profile.summonerId())
            : LeagueService.getProfileRanks(profile.puuid(), shard);
    }

    private ProfileStatistics statistics(SummonerProfile profile, LeagueHandler.SeasonRange season) {
        return profile.summonerId() == 0 || season == null
            ? new ProfileStatistics(season != null ? season.start() : 0)
            : statisticsService.get(profile.summonerId(), season);
    }

    private List<ProfileMastery> masteries(SummonerProfile profile) {
        return profile.summonerId() == 0 ? List.of() : LeagueService.getProfileMasteries(profile.summonerId());
    }

    private Map<Integer, ProfileChampion> champions(ProfileStatistics statistics) {
        Map<Integer, ProfileChampion> champions = new HashMap<>();
        for (Stats<Integer> stat : statistics.championStats) champions.put(stat.reference, champion(stat.reference));
        statistics.recentMatches.forEach(match -> champions.putIfAbsent(match.championId(), champion(match.championId())));
        return champions;
    }

    private ProfileChampion champion(int championId) {
        var champion = ChampionUtils.getChampion(championId);
        return new ProfileChampion(champion != null ? champion.getName() : String.valueOf(championId), ChampionUtils.getChampionProfilePic(championId));
    }
}
