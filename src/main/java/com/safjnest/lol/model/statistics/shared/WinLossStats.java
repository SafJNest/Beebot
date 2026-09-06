package com.safjnest.lol.model.statistics.shared;

public class WinLossStats {

    public long games;
    public long wins;

    public WinLossStats() {}

    public WinLossStats(long games, long wins) {
        this.games = games;
        this.wins = wins;
    }

    public double winrate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    public void merge(WinLossStats other) {
        if (other == null) return;
        games += other.games;
        wins += other.wins;
    }
}
