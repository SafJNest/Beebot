package com.safjnest.lol.model.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class Stats<T> {
    public T reference;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Map<GameQueueType, Map<String, Stats<Void>>>> context = new ArrayList<>();
    public long games;
    public long wins;
    public long kills;
    public long deaths;
    public long assists;
    public long damage;
    public long damageBuilding;
    public long damageTaken;
    public long healing;
    public long vision;
    public long ward;
    public long wardKilled;
    public long cs;
    public long gold;
    public long lpGain;
    public long level;
    public long doubles;
    public long triples;
    public long quadruples;
    public long pentas;
    public long q;
    public long w;
    public long e;
    public long r;
    public long d;
    public long f;
    public long arenaFirst;
    public long arenaSecond;
    public long arenaThird;
    public long arenaPlacementSum;
    public long playtime;
    public long lastPlayedAt;
    public double killParticipationSum;
    public long killParticipationGames;
    public double deathShareSum;
    public long deathShareGames;

    public double winrate;
    public double kda;
    public double avgKills;
    public double avgDeaths;
    public double avgAssists;
    public double avgDamage;
    public double avgDamageBuilding;
    public double avgDamageTaken;
    public double avgHealing;
    public double avgVision;
    public double avgWard;
    public double avgWardKilled;
    public double avgCs;
    public double avgGold;
    public double avgLpGain;
    public double avgLevel;
    public double avgArenaPlacement;
    public Double avgKillParticipation;
    public Double avgDeathShare;

    public Stats() {}

    public Stats(T reference) {
        this.reference = reference;
    }

    public void add(MatchResult match) {
        Participant participant = new Participant();
        participant.win = match.win();
        participant.kda = match.kda();
        participant.damage = match.damage();
        participant.visionScore = match.vision();
        participant.cs = match.cs();
        participant.goldEarned = match.gold();
        add(participant, match.timeStart(), match.timeEnd(), match.teamKills(), 0, false);
    }

    public void add(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills, boolean arena) {
        add(participant, timeStart, timeEnd, teamKills, enemyTeamKills, arena, true);
    }

    void addRaw(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills, boolean arena) {
        add(participant, timeStart, timeEnd, teamKills, enemyTeamKills, arena, false);
    }

    private void add(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills,
                     boolean arena, boolean calculate) {
        if (participant == null) return;
        addValues(participant.win, kda(participant.kda), participant.damage, participant.damageBuilding,
            participant.damageTaken, participant.healing, participant.visionScore, participant.ward,
            participant.wardKilled, participant.cs, participant.goldEarned, participant.gain,
            participant.level, participant.doubles, participant.triples, participant.quadruples,
            participant.pentas, participant.q, participant.w, participant.e, participant.r, participant.d,
            participant.f, participant.subTeamPlacement, timeStart, timeEnd, teamKills, enemyTeamKills, arena, calculate);
    }

    public void merge(Stats<?> other) {
        games += other.games;
        wins += other.wins;
        kills += other.kills;
        deaths += other.deaths;
        assists += other.assists;
        damage += other.damage;
        damageBuilding += other.damageBuilding;
        damageTaken += other.damageTaken;
        healing += other.healing;
        vision += other.vision;
        ward += other.ward;
        wardKilled += other.wardKilled;
        cs += other.cs;
        gold += other.gold;
        lpGain += other.lpGain;
        level += other.level;
        doubles += other.doubles;
        triples += other.triples;
        quadruples += other.quadruples;
        pentas += other.pentas;
        q += other.q;
        w += other.w;
        e += other.e;
        r += other.r;
        d += other.d;
        f += other.f;
        arenaFirst += other.arenaFirst;
        arenaSecond += other.arenaSecond;
        arenaThird += other.arenaThird;
        arenaPlacementSum += other.arenaPlacementSum;
        playtime += other.playtime;
        lastPlayedAt = Math.max(lastPlayedAt, other.lastPlayedAt);
        killParticipationSum += other.killParticipationSum;
        killParticipationGames += other.killParticipationGames;
        deathShareSum += other.deathShareSum;
        deathShareGames += other.deathShareGames;
        recalculate();
    }

    public void recalculate() {
        winrate = percent(wins, games);
        kda = deaths > 0 ? rounded((double) (kills + assists) / deaths) : kills + assists;
        avgKills = average(kills);
        avgDeaths = average(deaths);
        avgAssists = average(assists);
        avgDamage = average(damage);
        avgDamageBuilding = average(damageBuilding);
        avgDamageTaken = average(damageTaken);
        avgHealing = average(healing);
        avgVision = average(vision);
        avgWard = average(ward);
        avgWardKilled = average(wardKilled);
        avgCs = average(cs);
        avgGold = average(gold);
        avgLpGain = average(lpGain);
        avgLevel = average(level);
        avgArenaPlacement = average(arenaPlacementSum);
        avgKillParticipation = killParticipationGames > 0 ? rounded(killParticipationSum / killParticipationGames) : null;
        avgDeathShare = deathShareGames > 0 ? rounded(deathShareSum / deathShareGames) : null;
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

    private void addValues(
        boolean win,
        int[] kdaValues,
        int damage,
        int damageBuilding,
        int damageTaken,
        int healing,
        int vision,
        int ward,
        int wardKilled,
        int cs,
        int gold,
        int lpGain,
        int level,
        int doubles,
        int triples,
        int quadruples,
        int pentas,
        int q,
        int w,
        int e,
        int r,
        int d,
        int f,
        int arenaPlacement,
        long timeStart,
        long timeEnd,
        int teamKills,
        int enemyTeamKills,
        boolean arena,
        boolean calculate
    ) {
        games++;
        if (win) wins++;
        kills += kdaValues[0];
        deaths += kdaValues[1];
        assists += kdaValues[2];
        this.damage += damage;
        this.damageBuilding += damageBuilding;
        this.damageTaken += damageTaken;
        this.healing += healing;
        this.vision += vision;
        this.ward += ward;
        this.wardKilled += wardKilled;
        this.cs += cs;
        this.gold += gold;
        this.lpGain += lpGain;
        this.level += level;
        this.doubles += doubles;
        this.triples += triples;
        this.quadruples += quadruples;
        this.pentas += pentas;
        this.q += q;
        this.w += w;
        this.e += e;
        this.r += r;
        this.d += d;
        this.f += f;
        if (arena) {
            arenaFirst += arenaPlacement == 1 ? 1 : 0;
            arenaSecond += arenaPlacement == 2 ? 1 : 0;
            arenaThird += arenaPlacement == 3 ? 1 : 0;
            arenaPlacementSum += arenaPlacement;
        }
        playtime += Math.max(0, timeEnd - timeStart);
        lastPlayedAt = Math.max(lastPlayedAt, timeStart);
        if (teamKills > 0) {
            killParticipationSum += ((double) (kdaValues[0] + kdaValues[2]) / teamKills) * 100;
            killParticipationGames++;
        }
        if (enemyTeamKills > 0) {
            deathShareSum += ((double) kdaValues[1] / enemyTeamKills) * 100;
            deathShareGames++;
        }
        if (calculate) recalculate();
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }
}
