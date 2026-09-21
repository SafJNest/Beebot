package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public final class OPTUtils {
    private static final int MIN_GAMES = 20;
    private static final double BASE_THRESHOLD = 0.50;
    private static final double INITIAL_THRESHOLD = 0.30;
    private static final double DECAY_GAMES = 250.0;
    private static final double MIN_GAP = 0.15;

    private OPTUtils() {}

    public static void refresh(Map<Integer, Map<CanonicalQueue, Map<String, ProfileLeafStats>>> champions) {
        if (champions == null) return;
        Map<CanonicalQueue, Map<Integer, ChampionScope>> scopes = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<CanonicalQueue, Map<String, ProfileLeafStats>>> champion : champions.entrySet()) {
            Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues = champion.getValue();
            if (queues == null) continue;
            for (Map.Entry<CanonicalQueue, Map<String, ProfileLeafStats>> queue : queues.entrySet()) {
                if (queue.getValue() == null) continue;
                for (Map.Entry<String, ProfileLeafStats> lane : queue.getValue().entrySet()) {
                    ProfileLeafStats statistics = lane.getValue();
                    if (statistics == null) continue;
                    statistics.isOtp = null;
                    if (!playableLane(lane.getKey())) continue;
                    scopes.computeIfAbsent(queue.getKey(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(champion.getKey(), ignored -> new ChampionScope())
                        .add(statistics);
                }
            }
        }
        for (Map<Integer, ChampionScope> values : scopes.values()) markOtp(values);
    }

    public static boolean isOtp(long totalGames, long topGames, long secondGames) {
        if (totalGames < MIN_GAMES || topGames <= 0) return false;
        double topShare = (double) topGames / totalGames;
        double secondShare = (double) secondGames / totalGames;
        return topShare >= threshold(totalGames) && topShare - secondShare >= MIN_GAP;
    }

    public static double threshold(long totalGames) {
        return BASE_THRESHOLD + INITIAL_THRESHOLD * Math.exp(-totalGames / DECAY_GAMES);
    }

    private static void markOtp(Map<Integer, ChampionScope> values) {
        long totalGames = 0;
        ChampionScope top = null;
        long topGames = 0;
        long secondGames = 0;
        for (ChampionScope value : values.values()) {
            totalGames += value.games;
            if (value.games > topGames) {
                secondGames = topGames;
                topGames = value.games;
                top = value;
            } else if (value.games > secondGames) {
                secondGames = value.games;
            }
        }
        if (top != null && isOtp(totalGames, topGames, secondGames))
            for (ProfileLeafStats leaf : top.leaves) leaf.isOtp = Boolean.TRUE;
    }

    private static boolean playableLane(String value) {
        try { return LaneTypeUtils.playables().contains(LaneType.valueOf(value)); }
        catch (RuntimeException ignored) { return false; }
    }

    private static class ChampionScope {
        private final List<ProfileLeafStats> leaves = new ArrayList<>();
        private long games;

        private void add(ProfileLeafStats value) {
            games += value.games;
            leaves.add(value);
        }
    }
}
