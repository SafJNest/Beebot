package com.safjnest.lol.model.statistics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;

import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class Stats<T> {
    @JsonIgnore
    public T reference;
    public long games;
    public long wins;
    public long blueGames;
    public long blueWins;
    public long redGames;
    public long redWins;
    public long kills;
    public long deaths;
    public long assists;
    public long damage;
    public long damageBuilding;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long damageTaken;
    public long healing;
    public long vision;
    public long ward;
    public long wardKilled;
    public long cs;
    public long gold;
    public long lpGain;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long championLevelTotal;
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
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long arenaFirst;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long arenaSecond;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long arenaThird;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long arenaPlacementSum;
    public long playtime;
    public long lastPlayedAt;
    public double killParticipationSum;
    public double deathShareSum;

    @JsonIgnore public double winrate;
    @JsonIgnore public double kda;
    @JsonIgnore public double avgKills;
    @JsonIgnore public double avgDeaths;
    @JsonIgnore public double avgAssists;
    @JsonIgnore public double avgDamage;
    @JsonIgnore public double avgDamageBuilding;
    @JsonIgnore public Double avgDamageTaken;
    @JsonIgnore public double avgHealing;
    @JsonIgnore public double avgVision;
    @JsonIgnore public double avgWard;
    @JsonIgnore public double avgWardKilled;
    @JsonIgnore public double avgCs;
    @JsonIgnore public double avgGold;
    @JsonIgnore public double avgLpGain;
    @JsonIgnore public Double avgLevel;
    @JsonIgnore public Double avgArenaPlacement;
    @JsonIgnore public Double avgKillParticipation;
    @JsonIgnore public Double avgDeathShare;

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

    void accumulate(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills, boolean arena) {
        add(participant, timeStart, timeEnd, teamKills, enemyTeamKills, arena, false);
    }

    private void add(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills,
                     boolean arena, boolean calculate) {
        if (participant == null) return;
        addValues(participant.win, participant.team, kda(participant.kda), participant.damage, participant.damageBuilding,
            participant.damageTaken, participant.healing, participant.visionScore, participant.ward,
            participant.wardKilled, participant.cs, participant.goldEarned, participant.rankProgress == null || participant.rankProgress.gain == null ? 0 : participant.rankProgress.gain,
            participant.championLevel, participant.doubles, participant.triples, participant.quadruples,
            participant.pentas, participant.q, participant.w, participant.e, participant.r, participant.d,
            participant.f, participant.subTeamPlacement, timeStart, timeEnd, teamKills, enemyTeamKills, arena, calculate);
    }

    public void merge(Stats<?> other) {
        games += other.games;
        wins += other.wins;
        blueGames += other.blueGames;
        blueWins += other.blueWins;
        redGames += other.redGames;
        redWins += other.redWins;
        kills += other.kills;
        deaths += other.deaths;
        assists += other.assists;
        damage += other.damage;
        damageBuilding += other.damageBuilding;
        damageTaken = sum(damageTaken, other.damageTaken);
        healing += other.healing;
        vision += other.vision;
        ward += other.ward;
        wardKilled += other.wardKilled;
        cs += other.cs;
        gold += other.gold;
        lpGain += other.lpGain;
        championLevelTotal = sum(championLevelTotal, other.championLevelTotal);
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
        deathShareSum += other.deathShareSum;
        recalculate();
    }

    public void recalculate() {
        winrate = percent(wins, games);
        kda = deaths > 0 ? rounded((double) (kills + assists) / deaths) : kills + assists;
        avgKills = average(kills); avgDeaths = average(deaths); avgAssists = average(assists);
        avgDamage = average(damage); avgDamageBuilding = average(damageBuilding); avgDamageTaken = average(damageTaken);
        avgHealing = average(healing); avgVision = average(vision); avgWard = average(ward); avgWardKilled = average(wardKilled);
        avgCs = average(cs); avgGold = average(gold); avgLpGain = average(lpGain); avgLevel = average(championLevelTotal);
        avgArenaPlacement = avgArenaPlacement(); avgKillParticipation = avgKillParticipation(); avgDeathShare = avgDeathShare();
    }

    @JsonIgnore
    public long losses() {
        return games - wins;
    }

    @JsonIgnore
    public double winrate() {
        return percent(wins, games);
    }

    @JsonIgnore
    public double kda() {
        return deaths > 0 ? rounded((double) (kills + assists) / deaths) : kills + assists;
    }

    @JsonIgnore
    public double average(long value) {
        return games > 0 ? rounded((double) value / games) : 0;
    }

    @JsonIgnore
    public Double average(Long value) {
        return value == null ? null : average(value.longValue());
    }

    @JsonIgnore
    public Double avgKillParticipation() {
        return games > 0 ? rounded(killParticipationSum / games) : null;
    }

    @JsonIgnore
    public Double avgDeathShare() {
        return games > 0 ? rounded(deathShareSum / games) : null;
    }

    @JsonIgnore
    public Double avgArenaPlacement() {
        return games > 0 ? rounded((double) arenaPlacementSum / games) : null;
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
        TeamType team,
        int[] kdaValues,
        int damage,
        int damageBuilding,
        Integer damageTaken,
        int healing,
        int vision,
        int ward,
        int wardKilled,
        int cs,
        int gold,
        int lpGain,
        Integer championLevel,
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
        if (team == TeamType.BLUE) {
            blueGames++;
            if (win) blueWins++;
        } else if (team == TeamType.RED) {
            redGames++;
            if (win) redWins++;
        }
        kills += kdaValues[0];
        deaths += kdaValues[1];
        assists += kdaValues[2];
        this.damage += damage;
        this.damageBuilding += damageBuilding;
        this.damageTaken = sum(this.damageTaken, damageTaken == null ? null : damageTaken.longValue());
        this.healing += healing;
        this.vision += vision;
        this.ward += ward;
        this.wardKilled += wardKilled;
        this.cs += cs;
        this.gold += gold;
        this.lpGain += lpGain;
        championLevelTotal = sum(championLevelTotal, championLevel == null ? null : championLevel.longValue());
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
        }
        if (enemyTeamKills > 0) {
            deathShareSum += ((double) kdaValues[1] / enemyTeamKills) * 100;
        }
        if (calculate) recalculate();
    }

    private static Long sum(Long current, Long value) {
        if (value == null) return current;
        if (current == null) return value;
        return current + value;
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }
}
