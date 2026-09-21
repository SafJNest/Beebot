package com.safjnest.lol.model.statistics.shared;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChampionLeafStats extends LeafStats {

    public double csm;
    public long csmGames;

    public double gpm;
    public long gpmGames;

    public TrendStats trend;

    public Map<Integer, MatchupStats> matchups = new LinkedHashMap<>();

    public Map<String, Map<Integer, WinLossStats>> synergies = new LinkedHashMap<>();

    public Map<String, WinLossStats> powerCurve = new LinkedHashMap<>();

    public ChampionLeafStats() {}

    public Double csPerMinute() {
        return csmGames == 0 ? null : csm / csmGames;
    }

    public Double goldPerMinute() {
        return gpmGames == 0 ? null : gpm / gpmGames;
    }

    public ChampionLeafStats mergedWith(ChampionLeafStats other) {
        ChampionLeafStats result = new ChampionLeafStats();
        result.merge(this);
        result.merge(other);
        return result;
    }

    @Override
    public void merge(LeafStats other) {
        super.merge(other);
        if (other instanceof ChampionLeafStats o) {
            csm += o.csm;
            csmGames += o.csmGames;
            gpm += o.gpm;
            gpmGames += o.gpmGames;
            if (o.trend != null) {
                if (trend == null) trend = new TrendStats(0, 0);
                trend.games += o.trend.games;
                trend.wins += o.trend.wins;
            }
            for (Map.Entry<Integer, MatchupStats> e : o.matchups.entrySet()) {
                MatchupStats existing = matchups.get(e.getKey());
                if (existing == null) matchups.put(e.getKey(), copy(e.getValue()));
                else existing.merge(e.getValue());
            }
            for (Map.Entry<String, Map<Integer, WinLossStats>> e : o.synergies.entrySet()) {
                Map<Integer, WinLossStats> target = synergies.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>());
                for (Map.Entry<Integer, WinLossStats> inner : e.getValue().entrySet()) {
                    WinLossStats existing = target.get(inner.getKey());
                    if (existing == null) target.put(inner.getKey(), new WinLossStats(inner.getValue().games, inner.getValue().wins));
                    else existing.merge(inner.getValue());
                }
            }
            for (Map.Entry<String, WinLossStats> e : o.powerCurve.entrySet()) {
                WinLossStats existing = powerCurve.get(e.getKey());
                if (existing == null) powerCurve.put(e.getKey(), new WinLossStats(e.getValue().games, e.getValue().wins));
                else existing.merge(e.getValue());
            }
        }
    }

    private static MatchupStats copy(MatchupStats src) {
        MatchupStats dst = new MatchupStats();
        dst.games = src.games;
        dst.wins = src.wins;
        dst.goldDiff = src.goldDiff;
        dst.goldDiffGames = src.goldDiffGames;
        dst.csDiff = src.csDiff;
        dst.csDiffGames = src.csDiffGames;
        dst.soloKills = src.soloKills;
        dst.kills = src.kills;
        dst.kp = src.kp;
        dst.kpGames = src.kpGames;
        dst.metricGames = src.metricGames;
        return dst;
    }
}
