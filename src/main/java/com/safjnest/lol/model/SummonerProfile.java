package com.safjnest.lol.model;

public record SummonerProfile(
    int summonerId,
    String puuid,
    String riotId,
    String region,
    int level,
    int icon
) {}
