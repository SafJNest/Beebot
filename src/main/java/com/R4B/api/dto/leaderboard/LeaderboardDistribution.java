package com.R4B.api.dto.leaderboard;

import java.util.List;

import com.R4B.api.dto.*;

public record LeaderboardDistribution(List<Entry> entries) {
    public record Entry(
        String key,
        long players
    ) {}
}
