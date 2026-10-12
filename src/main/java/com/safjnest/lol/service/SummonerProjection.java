package com.safjnest.lol.service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.utils.ChampionUtils;
import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.ProfileStatistics;
import com.R4B.api.dto.summoner.*;
import com.R4B.api.dto.summoner.SummonerOverview.Champion;
import com.R4B.lol.GameQueueType;

public final class SummonerProjection {

    public static SummonerOverview from(ProfileStatistics statistics, List<Mastery> masteries) {
        return from(statistics, masteries, null);
    }

    public static SummonerOverview from(ProfileStatistics statistics, List<Mastery> masteries, Map<Integer, Champion> champions) {
        ProfileStatistics aggregate = statistics != null ? statistics : new ProfileStatistics();
        ProfileStatisticsAccumulator.of(aggregate).finish();
        Map<Integer, Champion> championMap = new HashMap<>();
        if (champions != null) championMap.putAll(champions);
        Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> championStats = ProfileStatisticsAccumulator.of(aggregate).championStats();
        for (Integer championId : championStats.keySet()) championMap.putIfAbsent(championId, champion(championId));

        return new SummonerOverview(
            aggregate,
            masteries != null ? List.copyOf(masteries) : List.of(),
            Map.copyOf(championMap)
        );
    }

    public static SummonerView view(Summoner summoner, Map<GameQueueType, Rank> ranks, ProfileStatistics statistics, List<Mastery> masteries) {
        return view(summoner, ranks, SummonerProjection.from(statistics, masteries));
    }

    public static SummonerView view(
        Summoner summoner,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        Map<Integer, SummonerOverview.Champion> champions
    ) {
        return view(summoner, ranks, SummonerProjection.from(statistics, masteries, champions));
    }

    public static SummonerView view(Summoner summoner, Map<GameQueueType, Rank> ranks, SummonerOverview overview) {
        return new SummonerView(summoner, ranks != null ? Map.copyOf(ranks) : Map.of(),
            overview != null ? overview : SummonerProjection.from(null, List.of()));
    }

    // ============================================================================

    private static Champion champion(int championId) {
        var champion = ChampionUtils.getChampion(championId);
        return new Champion(
            champion != null ? champion.getName() : String.valueOf(championId),
            ChampionUtils.getChampionProfilePic(championId)
        );
    }
}
