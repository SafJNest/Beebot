package com.safjnest.lol.model.statistics.shared;

import com.fasterxml.jackson.annotation.JsonInclude;

public class LeafStats {

    public long games;
    public long wins;

    public long kills;
    public long deaths;
    public long assists;

    public LeafStats() {}

    public LeafStats(long games, long wins, long kills, long deaths, long assists) {
        this.games = games;
        this.wins = wins;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
    }

    public long losses() {
        return games - wins;
    }

    public double winrate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    public double kda() {
        return deaths == 0 ? kills + assists : (double) (kills + assists) / deaths;
    }

    public void merge(LeafStats other) {
        if (other == null) return;
        games += other.games;
        wins += other.wins;
        kills += other.kills;
        deaths += other.deaths;
        assists += other.assists;
    }
}
