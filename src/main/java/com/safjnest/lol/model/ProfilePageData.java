package com.safjnest.lol.model;

import java.util.List;

public record ProfilePageData(
    SummonerProfile profile,
    SummonerRank rank,
    Summary summary,
    List<RoleStat> roles,
    List<ProfileChampionStats> topChampions,
    List<ProfileMatch> recentMatches
) {
    public record Summary(
        String form,
        String mainRole,
        double avgKda,
        int avgDamage
    ) {}

    public record RoleStat(
        String role,
        int games,
        double rate
    ) {}
}
