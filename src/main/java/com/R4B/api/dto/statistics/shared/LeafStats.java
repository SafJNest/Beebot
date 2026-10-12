package com.R4B.api.dto.statistics.shared;

import com.R4B.api.dto.*;

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
}
