package com.safjnest.lol.model.summoner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.utils.ChampionUtils;

public record SummonerOverview(
    ProfileStatistics statistics,
    List<Mastery> masteries,
    Map<Integer, Champion> champions
) {
    public record Champion(
        String name,
        String image
    ) {}

    public static SummonerOverview from(ProfileStatistics statistics, List<Mastery> masteries) {
        return from(statistics, masteries, null);
    }

    public static SummonerOverview from(ProfileStatistics statistics, List<Mastery> masteries, Map<Integer, Champion> champions) {
        ProfileStatistics aggregate = statistics != null ? statistics : new ProfileStatistics();
        aggregate.finish();
        Map<Integer, Champion> championMap = new HashMap<>();
        if (champions != null) championMap.putAll(champions);
        Map<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> championStats = aggregate.championStats();
        for (Integer championId : championStats.keySet()) championMap.putIfAbsent(championId, champion(championId));

        return new SummonerOverview(
            aggregate,
            masteries != null ? List.copyOf(masteries) : List.of(),
            Map.copyOf(championMap)
        );
    }

    private static Champion champion(int championId) {
        var champion = ChampionUtils.getChampion(championId);
        return new Champion(
            champion != null ? champion.getName() : String.valueOf(championId),
            ChampionUtils.getChampionProfilePic(championId)
        );
    }
}
