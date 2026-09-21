package com.safjnest.lol.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ChampionTierSource(
    boolean ready,
    long lastUpdate,
    Map<Integer, Champion> champions
) {

    public ChampionTierSource {
        champions = champions == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(champions));
    }

    public record Champion(
        ChampionTierList.Statistics statistics,
        List<Matchup> matchups
    ) {
        public Champion {
            matchups = matchups == null ? List.of() : List.copyOf(matchups);
        }
    }

    public record Matchup(int champion, int games, int wins) {}
}
