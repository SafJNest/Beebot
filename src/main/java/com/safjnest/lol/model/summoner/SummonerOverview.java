package com.safjnest.lol.model.summoner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;
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
        List<MatchResult> matches = recentMatches != null ? List.copyOf(recentMatches) : List.of();
        Map<Integer, Champion> championMap = new HashMap<>();
        if (champions != null) championMap.putAll(champions);
        for (Stats<Integer> stat : aggregate.championStats) championMap.putIfAbsent(stat.reference, champion(stat.reference));
        for (MatchResult match : matches) championMap.putIfAbsent(match.championId(), champion(match.championId()));
        StringBuilder form = new StringBuilder();
        for (MatchResult match : matches) form.append(match.win() ? 'W' : 'L');

        Champion mostPlayed = null;
        Stats<Integer> best = null;
        for (Stats<Integer> stat : aggregate.championStats) {
            if (best == null || stat.games > best.games || stat.games == best.games && stat.reference < best.reference) best = stat;
        }
        if (best != null) mostPlayed = championMap.get(best.reference);

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
