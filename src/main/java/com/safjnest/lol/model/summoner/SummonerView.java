package com.safjnest.lol.model.summoner;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.fasterxml.jackson.annotation.JsonInclude;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public record SummonerView(
    Summoner summoner,
    Map<GameQueueType, Rank> ranks,
    SummonerOverview overview,
    @JsonInclude(JsonInclude.Include.NON_NULL) ResponseMetadata metadata
) {

    public SummonerView(Summoner summoner, Map<GameQueueType, Rank> ranks, SummonerOverview overview) {
        this(summoner, ranks, overview, null);
    }
    public static SummonerView from(
        Summoner summoner,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries
    ) {
        return from(summoner, ranks, SummonerOverview.from(statistics, masteries));
    }

    public static SummonerView from(
        Summoner summoner,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        Map<Integer, SummonerOverview.Champion> champions
    ) {
        return from(summoner, ranks, statistics, masteries, champions, List.of());
    }

    public static SummonerView from(
        Summoner summoner,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        List<MatchResult> recentMatches
    ) {
        return from(summoner, ranks, statistics, masteries, null, recentMatches);
    }

    public static SummonerView from(
        Summoner summoner,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics,
        List<Mastery> masteries,
        Map<Integer, SummonerOverview.Champion> champions,
        List<MatchResult> recentMatches
    ) {
        return from(summoner, ranks, SummonerOverview.from(statistics, masteries, champions, recentMatches));
    }

    public static SummonerView from(Summoner summoner, Map<GameQueueType, Rank> ranks, SummonerOverview overview) {
        return new SummonerView(summoner, ranks != null ? Map.copyOf(ranks) : Map.of(),
            overview != null ? overview : SummonerOverview.from(null, List.of()));
    }

    public SummonerView withMetadata(ResponseMetadata value) {
        return new SummonerView(summoner, ranks, overview, value);
    }
}
