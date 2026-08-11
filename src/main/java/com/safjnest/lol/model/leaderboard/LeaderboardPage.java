package com.safjnest.lol.model.leaderboard;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.summoner.SummonerLeaderboard;

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
