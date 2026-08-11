package com.safjnest.lol.model.summoner;

public record SummonerLeaderboard(
    long position,
    SummonerView summoner
) {}
