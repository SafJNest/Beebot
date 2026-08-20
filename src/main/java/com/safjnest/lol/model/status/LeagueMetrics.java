package com.safjnest.lol.model.status;

import java.util.Map;

public record LeagueMetrics(
    long gamesAnalyzed,
    long totalSummoners,
    long totalMasteries,
    Map<String, Long> ranksByQueue
) {
}
