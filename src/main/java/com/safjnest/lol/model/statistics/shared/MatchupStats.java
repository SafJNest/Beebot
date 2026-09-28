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
    public double kdaSum;
    public long kdaGames;
    public double goldPerMinuteSum;
    public long goldPerMinuteGames;
    public double deathShareSum;
    public long deathShareGames;
    public long xpDiff;
    public long xpDiffGames;
    public long killDiff;
    public long killDiffGames;
    public long levelDiff;
    public long levelDiffGames;
    public long plateDiff;
    public long plateDiffGames;

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
        kdaSum += other.kdaSum;
        kdaGames += other.kdaGames;
        goldPerMinuteSum += other.goldPerMinuteSum;
        goldPerMinuteGames += other.goldPerMinuteGames;
        deathShareSum += other.deathShareSum;
        deathShareGames += other.deathShareGames;
        xpDiff += other.xpDiff;
        xpDiffGames += other.xpDiffGames;
        killDiff += other.killDiff;
        killDiffGames += other.killDiffGames;
        levelDiff += other.levelDiff;
        levelDiffGames += other.levelDiffGames;
        plateDiff += other.plateDiff;
        plateDiffGames += other.plateDiffGames;
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

    public Double kda() { return kdaGames == 0 ? null : kdaSum / kdaGames; }
    public Double goldPerMinute() { return goldPerMinuteGames == 0 ? null : goldPerMinuteSum / goldPerMinuteGames; }
    public Double deathShare() { return deathShareGames == 0 ? null : deathShareSum / deathShareGames; }
    public Double xpDiffAt15() { return xpDiffGames == 0 ? null : (double) xpDiff / xpDiffGames; }
    public Double killDiffAt15() { return killDiffGames == 0 ? null : (double) killDiff / killDiffGames; }
    public Double levelDiffAt15() { return levelDiffGames == 0 ? null : (double) levelDiff / levelDiffGames; }
    public Double plateDiffAt15() { return plateDiffGames == 0 ? null : (double) plateDiff / plateDiffGames; }
}
