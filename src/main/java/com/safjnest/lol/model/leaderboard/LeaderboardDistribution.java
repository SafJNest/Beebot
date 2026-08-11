package com.safjnest.lol.model.leaderboard;

import java.util.List;

public record LeaderboardDistribution(List<Entry> entries) {
    public record Entry(
        String key,
        long players
    ) {}
}
