package com.safjnest.lol.model.statistics;

import com.safjnest.lol.model.match.MatchResult;

public class Stats<T> {
    public T reference;
    public long games;
    public long wins;
    public long kills;
    public long deaths;
    public long assists;
    public long damage;
    public long vision;
    public long cs;
    public long gold;
    public long playtime;
    public long lastPlayedAt;
    public double killParticipationSum;
    public long killParticipationGames;

    public double winrate;
    public double kda;
    public double avgKills;
    public double avgDeaths;
    public double avgAssists;
    public double avgDamage;
    public double avgVision;
    public double avgCs;
    public Double avgKillParticipation;

    public Stats() {}

    public Stats(T reference) {
        this.reference = reference;
    }

    public void add(MatchResult match) {
        int[] kdaValues = kda(match.kda());
        games++;
        if (match.win()) wins++;
        kills += kdaValues[0];
        deaths += kdaValues[1];
        assists += kdaValues[2];
        damage += match.damage();
        vision += match.vision();
        cs += match.cs();
        gold += match.gold();
        playtime += Math.max(0, match.timeEnd() - match.timeStart());
        lastPlayedAt = Math.max(lastPlayedAt, match.timeStart());
        if (match.teamKills() > 0) {
            killParticipationSum += ((double) (kdaValues[0] + kdaValues[2]) / match.teamKills()) * 100;
            killParticipationGames++;
        }
        recalculate();
    }

    public void merge(Stats<?> other) {
        games += other.games;
        wins += other.wins;
        kills += other.kills;
        deaths += other.deaths;
        assists += other.assists;
        damage += other.damage;
        vision += other.vision;
        cs += other.cs;
        gold += other.gold;
        playtime += other.playtime;
        lastPlayedAt = Math.max(lastPlayedAt, other.lastPlayedAt);
        killParticipationSum += other.killParticipationSum;
        killParticipationGames += other.killParticipationGames;
        recalculate();
    }

    public void recalculate() {
        winrate = percent(wins, games);
        kda = deaths > 0 ? rounded((double) (kills + assists) / deaths) : kills + assists;
        avgKills = average(kills);
        avgDeaths = average(deaths);
        avgAssists = average(assists);
        avgDamage = average(damage);
        avgVision = average(vision);
        avgCs = average(cs);
        avgKillParticipation = killParticipationGames > 0 ? rounded(killParticipationSum / killParticipationGames) : null;
    }

    public long losses() {
        return games - wins;
    }

    private double average(long value) {
        return games > 0 ? rounded((double) value / games) : 0;
    }

    private static double percent(long part, long total) {
        return total > 0 ? rounded((double) part / total * 100) : 0;
    }

    private static double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int[] kda(String value) {
        String[] values = value == null ? new String[0] : value.split("/");
        if (values.length != 3) return new int[3];
        return new int[] { integer(values[0]), integer(values[1]), integer(values[2]) };
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }
}
