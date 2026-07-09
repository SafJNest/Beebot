package com.safjnest.lol.model;

public record SummonerRank(
    String rank,
    int lp,
    int wins,
    int losses
) {
    public static SummonerRank unranked() {
        return new SummonerRank("UNRANKED", 0, 0, 0);
    }
}
