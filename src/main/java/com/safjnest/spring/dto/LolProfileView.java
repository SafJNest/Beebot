package com.safjnest.spring.dto;

import java.util.List;

public record LolProfileView(
    Profile profile,
    Summary summary,
    List<RoleStat> roles,
    List<TopChampion> topChampions,
    List<RecentMatch> recentMatches
) {
    public record Profile(
        String puuid,
        String riotId,
        String name,
        String tag,
        String region,
        int level,
        int icon,
        String iconUrl,
        String rank,
        int lp,
        int wins,
        int losses,
        double winrate
    ) {}

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

    public record TopChampion(
        int championId,
        String champion,
        String image,
        int games,
        int wins,
        int losses,
        double winrate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double avgKda,
        double avgCs,
        int avgDamage,
        int masteryLevel,
        int masteryPoints
    ) {}

    public record RecentMatch(
        String gameId,
        boolean win,
        String result,
        int championId,
        String champion,
        String image,
        String role,
        String kda,
        double kdaRatio,
        int cs,
        String queue,
        long durationMs,
        String duration,
        long timeStart,
        String ago,
        int damage,
        int gold,
        int vision,
        List<Integer> items,
        List<Integer> summonerSpells
    ) {}
}
