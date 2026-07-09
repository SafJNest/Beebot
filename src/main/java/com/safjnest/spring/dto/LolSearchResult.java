package com.safjnest.spring.dto;

public record LolSearchResult(
    String puuid,
    String riotId,
    String name,
    String tag,
    String region,
    String rank,
    int lp,
    int wins,
    int losses,
    double winrate
) {}
