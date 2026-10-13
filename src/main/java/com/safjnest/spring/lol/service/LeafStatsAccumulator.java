package com.safjnest.spring.lol.service;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;

public final class LeafStatsAccumulator {
    private final LeafStats data;
    private LeafStatsAccumulator(LeafStats data) { this.data = data; }
    public static LeafStatsAccumulator of(LeafStats data) { return new LeafStatsAccumulator(data); }

    public void merge(LeafStats other) {
        if (other == null) return;
        data.games += other.games;
        data.wins += other.wins;
        data.kills += other.kills;
        data.deaths += other.deaths;
        data.assists += other.assists;
    }
}
