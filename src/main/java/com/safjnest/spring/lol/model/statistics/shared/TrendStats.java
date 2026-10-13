package com.safjnest.spring.lol.model.statistics.shared;

import com.R4B.api.dto.*;
public class TrendStats extends WinLossStats {

    public TrendStats() {}

    public TrendStats(long games, long wins) {
        this.games = games;
        this.wins = wins;
    }
}
