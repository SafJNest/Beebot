package com.safjnest.spring.lol.service;

import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.KdaUtils;
import com.R4B.lol.TeamType;

public final class ProfileLeafAccumulator {
    private final ProfileLeafStats data;
    private ProfileLeafAccumulator(ProfileLeafStats data) { this.data = data; }
    public static ProfileLeafAccumulator of(ProfileLeafStats data) { return new ProfileLeafAccumulator(data); }

    public void accumulate(Participant participant, long timeStart, long timeEnd, int teamKills, int enemyTeamKills, boolean arena) {
        if (participant == null) return;
        int[] values = KdaUtils.parse(participant.kda);
        data.games++;
        if (participant.win) data.wins++;
        if (participant.team == TeamType.BLUE) {
            data.blueGames++;
            if (participant.win) data.blueWins++;
        } else if (participant.team == TeamType.RED) {
            data.redGames++;
            if (participant.win) data.redWins++;
        }
        data.kills += values[0]; data.deaths += values[1]; data.assists += values[2];
        data.damage += participant.damage; data.damageBuilding += participant.damageBuilding;
        data.damageTaken = sum(data.damageTaken, participant.damageTaken == null ? null : participant.damageTaken.longValue());
        data.healing += participant.healing; data.vision += participant.visionScore; data.ward += participant.ward; data.wardKilled += participant.wardKilled;
        data.cs += participant.cs; data.gold += participant.goldEarned;
        data.lpGain += participant.rankProgress == null || participant.rankProgress.gain == null ? 0 : participant.rankProgress.gain;
        data.championLevelTotal = sum(data.championLevelTotal, participant.championLevel == null ? null : participant.championLevel.longValue());
        data.doubles += participant.doubles; data.triples += participant.triples; data.quadruples += participant.quadruples; data.pentas += participant.pentas;
        data.q += participant.q; data.w += participant.w; data.e += participant.e; data.r += participant.r; data.d += participant.d; data.f += participant.f;
        accumulateSpellCasts(participant);
        if (arena) {
            data.arenaFirst += participant.subTeamPlacement == 1 ? 1 : 0;
            data.arenaSecond += participant.subTeamPlacement == 2 ? 1 : 0;
            data.arenaThird += participant.subTeamPlacement == 3 ? 1 : 0;
            data.arenaPlacementSum += participant.subTeamPlacement;
        }
        data.playtime += Math.max(0, timeEnd - timeStart);
        data.lastPlayedAt = Math.max(data.lastPlayedAt, timeStart);
        if (teamKills > 0) data.killParticipationSum += (double) (values[0] + values[2]) / teamKills * 100;
        if (enemyTeamKills > 0) data.deathShareSum += (double) values[1] / enemyTeamKills * 100;
    }

    public void accumulateSpellCasts(Participant participant) {
        if (participant == null) return;
        if (participant.d > 0) data.spellDCasts.merge(participant.summonerSpell1, (long) participant.d, Long::sum);
        if (participant.f > 0) data.spellFCasts.merge(participant.summonerSpell2, (long) participant.f, Long::sum);
    }

    public void merge(LeafStats other) {
        LeafStatsAccumulator.of(data).merge(other);
        if (other instanceof ProfileLeafStats o) {
            data.blueGames += o.blueGames;
            data.blueWins += o.blueWins;
            data.redGames += o.redGames;
            data.redWins += o.redWins;
            data.damage += o.damage;
            data.damageBuilding += o.damageBuilding;
            data.damageTaken = sum(data.damageTaken, o.damageTaken);
            data.healing += o.healing;
            data.vision += o.vision;
            data.ward += o.ward;
            data.wardKilled += o.wardKilled;
            data.cs += o.cs;
            data.gold += o.gold;
            data.lpGain += o.lpGain;
            data.championLevelTotal = sum(data.championLevelTotal, o.championLevelTotal);
            data.doubles += o.doubles;
            data.triples += o.triples;
            data.quadruples += o.quadruples;
            data.pentas += o.pentas;
            data.q += o.q;
            data.w += o.w;
            data.e += o.e;
            data.r += o.r;
            data.d += o.d;
            data.f += o.f;
            mergeSpellCasts(data.spellDCasts, o.spellDCasts);
            mergeSpellCasts(data.spellFCasts, o.spellFCasts);
            data.arenaFirst += o.arenaFirst;
            data.arenaSecond += o.arenaSecond;
            data.arenaThird += o.arenaThird;
            data.arenaPlacementSum += o.arenaPlacementSum;
            data.playtime += o.playtime;
            data.lastPlayedAt = Math.max(data.lastPlayedAt, o.lastPlayedAt);
            data.killParticipationSum += o.killParticipationSum;
            data.deathShareSum += o.deathShareSum;
            data.goldDiffAt15Sum += o.goldDiffAt15Sum;
            data.goldDiffAt15Games += o.goldDiffAt15Games;
            data.csDiffAt15Sum += o.csDiffAt15Sum;
            data.csDiffAt15Games += o.csDiffAt15Games;
            data.xpDiffAt15Sum += o.xpDiffAt15Sum;
            data.xpDiffAt15Games += o.xpDiffAt15Games;
            data.killDiffAt15Sum += o.killDiffAt15Sum;
            data.killDiffAt15Games += o.killDiffAt15Games;
            data.levelDiffAt15Sum += o.levelDiffAt15Sum;
            data.levelDiffAt15Games += o.levelDiffAt15Games;
            data.plateDiffAt15Sum += o.plateDiffAt15Sum;
            data.plateDiffAt15Games += o.plateDiffAt15Games;
        }
    }

    // ============================================================================

    private static Long sum(Long a, Long b) {
        if (b == null) return a;
        if (a == null) return b;
        return a + b;
    }

    private static void mergeSpellCasts(Map<Integer, Long> target, Map<Integer, Long> source) {
        if (target == null || source == null) return;
        for (Map.Entry<Integer, Long> entry : source.entrySet())
            if (entry.getKey() != null && entry.getValue() != null) target.merge(entry.getKey(), entry.getValue(), Long::sum);
    }
}
