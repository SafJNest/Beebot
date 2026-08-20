package com.safjnest.lol.model.status;

import java.util.Map;

public record LeagueMetrics(
    long gameQueue,
    long profileQueue,
    long gamesAnalyzed,
    long totalSummoners,
    long totalMasteries,
    Map<String, Long> ranksByQueue
) {
}
