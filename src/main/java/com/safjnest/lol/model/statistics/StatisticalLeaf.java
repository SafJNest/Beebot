package com.safjnest.lol.model.statistics;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatisticalLeaf(long games, long wins, long placementSum, long placementGames,
        Map<Integer, Long> placements) {

    public StatisticalLeaf {
        placements = Map.copyOf(placements);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public double winrate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Double averagePlacement() {
        return placementGames == 0 ? null : (double) placementSum / placementGames;
    }
}
