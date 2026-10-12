package com.R4B.api.dto.leaderboard;

import java.util.List;

import com.R4B.api.dto.*;
import com.R4B.api.dto.summoner.SummonerLeaderboard;

public record LeaderboardPage(
    int page,
    int pageSize,
    long total,
    long pages,
    List<SummonerLeaderboard> summoners,
    ResponseMetadata metadata
) {

    public LeaderboardPage(int page, int pageSize, long total, long pages, List<SummonerLeaderboard> summoners) {
        this(page, pageSize, total, pages, summoners, null);
    }

    public LeaderboardPage withMetadata(ResponseMetadata value) {
        return new LeaderboardPage(page, pageSize, total, pages, summoners, value);
    }
}
