package com.safjnest.lol.model.leaderboard;

import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;

public record LeaderboardRow(
    Summoner summoner,
    Rank rank
) {}
