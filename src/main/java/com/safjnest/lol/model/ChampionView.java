package com.safjnest.lol.model;

public record ChampionView(
    Champion champion,
    ChampionStatistics stats,
    Build build,
    ResponseMetadata metadata
) {

    public ChampionView(Champion champion, ChampionStatistics stats, Build build) {
        this(champion, stats, build, null);
    }

    public ChampionView withMetadata(ResponseMetadata value) {
        return new ChampionView(champion, stats, build, value);
    }

    public record Champion(
        int id,
        String name,
        String image
    ) {}
}
