package com.safjnest.lol.model.leaderboard;

import java.util.List;

import com.safjnest.lol.model.summoner.SummonerLeaderboard;

public record LeaderboardPage(
    int page,
    int pageSize,
    long total,
    long pages,
    List<SummonerLeaderboard> summoners
) {}
