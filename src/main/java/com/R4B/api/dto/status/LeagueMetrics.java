package com.R4B.api.dto.status;

import java.util.Map;

import com.R4B.api.dto.*;

public record LeagueMetrics(
    long gamesAnalyzed,
    long totalSummoners,
    long totalMasteries,
    Map<String, Long> ranksByQueue
) {
}
