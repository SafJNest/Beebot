package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.ChampionIndexable;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public final class ChampionIndexableService {

    private static final double MIN_INDEXABLE_PROPORTION = 0.10D;

    private ChampionIndexableService() {}

    public static List<ChampionIndexable> get() {
        String patch = PatchUtils.getPatch();
        return patch == null ? List.of() : MongoDB.findChampionIndexables(patch);
    }

    public static List<ChampionIndexable> refresh() {
        String patch = PatchUtils.getPatch();
        if (patch == null || patch.isBlank()) return List.of();

        Map<Integer, Map<LaneType, Integer>> gamesByChampion = MongoDB.findChampionRoleGames(patch);
        List<ChampionIndexable> values = new ArrayList<>();
        for (Map.Entry<Integer, Map<LaneType, Integer>> entry : gamesByChampion.entrySet()) {
            int totalGames = 0;
            for (int games : entry.getValue().values()) totalGames += games;

            List<LaneType> roles = new ArrayList<>(LaneTypeUtils.playables());
            roles.sort(Comparator
                .comparingInt((LaneType role) -> -entry.getValue().getOrDefault(role, 0))
                .thenComparingInt(LaneTypeUtils::playableOrder));
            for (LaneType role : roles) {
                int games = entry.getValue().getOrDefault(role, 0);
                values.add(new ChampionIndexable(
                    entry.getKey(), role, games, isIndexable(games, totalGames), 0L));
            }
        }

        MongoDB.upsertChampionIndexables(patch, values);
        return MongoDB.findChampionIndexables(patch);
    }

    public static boolean isIndexable(int games, int totalGames) {
        return totalGames > 0 && games > 0
            && (double) games / totalGames >= MIN_INDEXABLE_PROPORTION;
    }

    // ============================================================================
}
