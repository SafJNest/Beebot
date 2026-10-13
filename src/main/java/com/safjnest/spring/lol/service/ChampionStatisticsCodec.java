package com.safjnest.spring.lol.service;
import java.util.*;

import com.R4B.api.dto.*;
import com.R4B.api.dto.ChampionStatistics.*;
import com.safjnest.utils.JsonCodec;

public final class ChampionStatisticsCodec {

    public static String toJson(ChampionStatistics data) {
        return JsonCodec.toJson(data);
    }

    public static ChampionStatistics fromJson(String json) {
        try {
            ChampionStatistics statistics = JsonCodec.fromJson(json, ChampionStatistics.class);
            if (statistics == null) return null;
            if (statistics.filter() != null && !(statistics.filter() instanceof Filter)) return null;
            if (statistics.overview() == null || statistics.laneStats() == null
                    || statistics.matchups() == null || statistics.laneSynergies() == null
                    || statistics.powerCurve() == null) return null;
            for (LaneStat lane : statistics.laneStats()) if (lane == null) return null;
            for (Map.Entry<Integer, Matchup> entry : statistics.matchups().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) return null;
            }
            return statistics;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
