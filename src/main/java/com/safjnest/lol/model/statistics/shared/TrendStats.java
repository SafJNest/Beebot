package com.safjnest.lol.model.statistics.shared;

public class TrendStats extends WinLossStats {

    public String previousPatch;

    public TrendStats() {}

    public TrendStats(String previousPatch, long games, long wins) {
        this.previousPatch = previousPatch;
        this.games = games;
        this.wins = wins;
    }
}
