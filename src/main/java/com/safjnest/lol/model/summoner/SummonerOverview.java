package com.safjnest.lol.model.summoner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.utils.ChampionUtils;

public record SummonerOverview(
    ProfileStatistics statistics,
    List<Mastery> masteries,
    Map<Integer, Champion> champions,
    String form,
    Champion mostPlayed,
    List<MatchResult> recentMatches
) {
    public record Champion(
        String name,
        String image
    ) {}

    public static SummonerOverview from(ProfileStatistics statistics, List<Mastery> masteries) {
        return from(statistics, masteries, null, List.of());
    }

    public static SummonerOverview from(ProfileStatistics statistics, List<Mastery> masteries, Map<Integer, Champion> champions) {
        return from(statistics, masteries, champions, List.of());
    }

    public static SummonerOverview from(
        ProfileStatistics statistics,
        List<Mastery> masteries,
        Map<Integer, Champion> champions,
        List<MatchResult> recentMatches
    ) {
        ProfileStatistics aggregate = statistics != null ? statistics : new ProfileStatistics();
        aggregate.finish();
        List<MatchResult> matches = recentMatches != null ? List.copyOf(recentMatches) : List.of();
        Map<Integer, Champion> championMap = new HashMap<>();
        if (champions != null) championMap.putAll(champions);
        Map<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> championStats = aggregate.championStats();
        for (Integer championId : championStats.keySet()) championMap.putIfAbsent(championId, champion(championId));
        for (MatchResult match : matches) championMap.putIfAbsent(match.championId(), champion(match.championId()));
        StringBuilder form = new StringBuilder();
        for (MatchResult match : matches) form.append(match.win() ? 'W' : 'L');

        Champion mostPlayed = null;
        Integer best = null;
        for (Map.Entry<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> entry : championStats.entrySet()) {
            if (best == null || entry.getValue().games > championStats.get(best).games
                || entry.getValue().games == championStats.get(best).games && entry.getKey() < best) best = entry.getKey();
        }
        if (best != null) mostPlayed = championMap.get(best);

        return new SummonerOverview(
            aggregate,
            masteries != null ? List.copyOf(masteries) : List.of(),
            Map.copyOf(championMap),
            form.toString(),
            mostPlayed,
            matches
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
