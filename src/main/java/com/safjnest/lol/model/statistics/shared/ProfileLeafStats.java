package com.safjnest.lol.model.statistics.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.lol.model.match.Participant;

import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class ProfileLeafStats extends LeafStats {

    @JsonIgnore
    public Object reference;

    public long blueGames;
    public long blueWins;
    public long redGames;
    public long redWins;

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

    @JsonIgnore
    public Boolean isOtp;

    public ProfileLeafStats() {}

    public void accumulate(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills, boolean arena) {
        if (participant == null) return;
        int[] values = kda(participant.kda);
        games++;
        if (participant.win) wins++;
        if (participant.team == TeamType.BLUE) {
            blueGames++;
            if (participant.win) blueWins++;
        } else if (participant.team == TeamType.RED) {
            redGames++;
            if (participant.win) redWins++;
        }
        kills += values[0]; deaths += values[1]; assists += values[2];
        damage += participant.damage; damageBuilding += participant.damageBuilding;
        damageTaken = sum(damageTaken, participant.damageTaken == null ? null : participant.damageTaken.longValue());
        healing += participant.healing; vision += participant.visionScore; ward += participant.ward; wardKilled += participant.wardKilled;
        cs += participant.cs; gold += participant.goldEarned;
        lpGain += participant.rankProgress == null || participant.rankProgress.gain == null ? 0 : participant.rankProgress.gain;
        championLevelTotal = sum(championLevelTotal, participant.championLevel == null ? null : participant.championLevel.longValue());
        doubles += participant.doubles; triples += participant.triples; quadruples += participant.quadruples; pentas += participant.pentas;
        q += participant.q; w += participant.w; e += participant.e; r += participant.r; d += participant.d; f += participant.f;
        if (arena) {
            arenaFirst += participant.subTeamPlacement == 1 ? 1 : 0;
            arenaSecond += participant.subTeamPlacement == 2 ? 1 : 0;
            arenaThird += participant.subTeamPlacement == 3 ? 1 : 0;
            arenaPlacementSum += participant.subTeamPlacement;
        }
        playtime += Math.max(0, timeEnd - timeStart);
        lastPlayedAt = Math.max(lastPlayedAt, timeStart);
        if (teamKills > 0) killParticipationSum += (double) (values[0] + values[2]) / teamKills * 100;
        if (enemyTeamKills > 0) deathShareSum += (double) values[1] / enemyTeamKills * 100;
    }

    public double winratePercent() {
        return games == 0 ? 0 : (double) wins / games * 100;
    }

    public double kdaVal() {
        return deaths == 0 ? kills + assists : (double) (kills + assists) / deaths;
    }

    public double avgKills() { return games == 0 ? 0 : (double) kills / games; }
    public double avgDeaths() { return games == 0 ? 0 : (double) deaths / games; }
    public double avgAssists() { return games == 0 ? 0 : (double) assists / games; }
    public double avgDamage() { return games == 0 ? 0 : (double) damage / games; }
    public double avgDamageBuilding() { return games == 0 ? 0 : (double) damageBuilding / games; }
    public double avgHealing() { return games == 0 ? 0 : (double) healing / games; }
    public double avgVision() { return games == 0 ? 0 : (double) vision / games; }
    public double avgWard() { return games == 0 ? 0 : (double) ward / games; }
    public double avgWardKilled() { return games == 0 ? 0 : (double) wardKilled / games; }
    public double avgCs() { return games == 0 ? 0 : (double) cs / games; }
    public double avgGold() { return games == 0 ? 0 : (double) gold / games; }
    public double avgLpGain() { return games == 0 ? 0 : (double) lpGain / games; }
    public double avgArenaPlacement() { return games == 0 ? 0 : (double) arenaPlacementSum / games; }
    public double avgKillParticipation() { return games == 0 ? 0 : killParticipationSum / games; }
    public double avgDeathShare() { return games == 0 ? 0 : deathShareSum / games; }

    @Override
    public void merge(LeafStats other) {
        super.merge(other);
        if (other instanceof ProfileLeafStats o) {
            blueGames += o.blueGames;
            blueWins += o.blueWins;
            redGames += o.redGames;
            redWins += o.redWins;
            damage += o.damage;
            damageBuilding += o.damageBuilding;
            damageTaken = sum(damageTaken, o.damageTaken);
            healing += o.healing;
            vision += o.vision;
            ward += o.ward;
            wardKilled += o.wardKilled;
            cs += o.cs;
            gold += o.gold;
            lpGain += o.lpGain;
            championLevelTotal = sum(championLevelTotal, o.championLevelTotal);
            doubles += o.doubles;
            triples += o.triples;
            quadruples += o.quadruples;
            pentas += o.pentas;
            q += o.q;
            w += o.w;
            e += o.e;
            r += o.r;
            d += o.d;
            f += o.f;
            arenaFirst += o.arenaFirst;
            arenaSecond += o.arenaSecond;
            arenaThird += o.arenaThird;
            arenaPlacementSum += o.arenaPlacementSum;
            playtime += o.playtime;
            lastPlayedAt = Math.max(lastPlayedAt, o.lastPlayedAt);
            killParticipationSum += o.killParticipationSum;
            deathShareSum += o.deathShareSum;
        }
    }

    private static Long sum(Long a, Long b) {
        if (b == null) return a;
        if (a == null) return b;
        return a + b;
    }

    private static int[] kda(String value) {
        String[] values = value == null ? new String[0] : value.split("/");
        if (values.length != 3) return new int[3];
        return new int[]{integer(values[0]), integer(values[1]), integer(values[2])};
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
