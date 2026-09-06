package com.safjnest.lol.model.statistics.shared;

public class MatchupStats extends WinLossStats {

    public long goldDiff;
    public long goldDiffGames;

    public long csDiff;
    public long csDiffGames;

    public long soloKills;
    public long kills;

    public double kp;
    public long kpGames;

    public long metricGames;

    public MatchupStats() {}

    public void merge(MatchupStats other) {
        if (other == null) return;
        super.merge(other);
        goldDiff += other.goldDiff;
        goldDiffGames += other.goldDiffGames;
        csDiff += other.csDiff;
        csDiffGames += other.csDiffGames;
        soloKills += other.soloKills;
        kills += other.kills;
        kp += other.kp;
        kpGames += other.kpGames;
        metricGames += other.metricGames;
    }

    public Double goldDiffAt15() {
        return goldDiffGames == 0 ? null : (double) goldDiff / goldDiffGames;
    }

    public Double csDiffAt15() {
        return csDiffGames == 0 ? null : (double) csDiff / csDiffGames;
    }

    public Double soloKillRate() {
        if (kills > 0) return (double) soloKills / kills;
        return metricGames > 0 ? 0d : null;
    }

    public Double killParticipation() {
        return kpGames == 0 ? null : kp / kpGames;
    }
}
