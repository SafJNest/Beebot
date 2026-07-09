package com.safjnest.lol.model;

public record SummonerSearchResult(
    String puuid,
    String riotId,
    String region,
    String rank,
    int lp,
    int wins,
    int losses
) {}
