package com.safjnest.lol.model;

public record ProfileChampionStats(
    int championId,
    int games,
    int wins,
    int losses,
    double avgKills,
    double avgDeaths,
    double avgAssists,
    double avgCs,
    int avgDamage,
    int masteryLevel,
    int masteryPoints
) {}
