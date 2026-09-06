package com.safjnest.lol.model.statistics.shared;

public class TrendStats extends WinLossStats {

    public TrendStats() {}

    public TrendStats(long games, long wins) {
        this.games = games;
        this.wins = wins;
    }
}
