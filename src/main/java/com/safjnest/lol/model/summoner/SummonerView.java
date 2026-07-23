package com.safjnest.lol.model.summoner;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;

public record SummonerView(
    Summoner summoner,
    List<Rank> ranks,
    SummonerOverview overview
) {
    public static SummonerView from(
        Summoner summoner,
        List<Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries
    ) {
        return from(summoner, ranks, SummonerOverview.from(statistics, masteries));
    }

    public static SummonerView from(
        Summoner summoner,
        List<Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        Map<Integer, SummonerOverview.Champion> champions
    ) {
        return from(summoner, ranks, statistics, masteries, champions, List.of());
    }

    public static SummonerView from(
        Summoner summoner,
        List<Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        List<MatchResult> recentMatches
    ) {
        return from(summoner, ranks, statistics, masteries, null, recentMatches);
    }

    public static SummonerView from(
        Summoner summoner,
        List<Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        Map<Integer, SummonerOverview.Champion> champions,
        List<MatchResult> recentMatches
    ) {
        return from(summoner, ranks, SummonerOverview.from(statistics, masteries, champions, recentMatches));
    }

    public static SummonerView from(Summoner summoner, List<Rank> ranks, SummonerOverview overview) {
        return new SummonerView(summoner, ranks != null ? List.copyOf(ranks) : List.of(),
            overview != null ? overview : SummonerOverview.from(null, List.of()));
    }
}
