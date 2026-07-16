package com.safjnest.lol.model;

public record ChampionView(
    Champion champion,
    ChampionStatistics stats,
    Build build
) {

    public record Champion(
        int id,
        String name,
        String image
    ) {}
}
