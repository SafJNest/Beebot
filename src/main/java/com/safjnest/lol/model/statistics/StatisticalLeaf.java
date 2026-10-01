package com.safjnest.lol.model.statistics;

import java.util.Map;

public record StatisticalLeaf(long games, long wins, long placementSum, long placementGames,
        Map<Integer, Long> placements) {

    public StatisticalLeaf {
        placements = Map.copyOf(placements);
    }

    public double winrate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    public Double averagePlacement() {
        return placementGames == 0 ? null : (double) placementSum / placementGames;
    }
}
