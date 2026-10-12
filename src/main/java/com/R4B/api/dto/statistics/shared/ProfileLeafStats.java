package com.R4B.api.dto.statistics.shared;

import java.util.LinkedHashMap;
import java.util.Map;

import com.R4B.api.dto.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("D")
    public Map<Integer, Long> spellDCasts = new LinkedHashMap<>();
    @JsonProperty("F")
    public Map<Integer, Long> spellFCasts = new LinkedHashMap<>();

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

    public double goldDiffAt15Sum;
    public long goldDiffAt15Games;
    public double csDiffAt15Sum;
    public long csDiffAt15Games;
    public double xpDiffAt15Sum;
    public long xpDiffAt15Games;
    public double killDiffAt15Sum;
    public long killDiffAt15Games;
    public double levelDiffAt15Sum;
    public long levelDiffAt15Games;
    public double plateDiffAt15Sum;
    public long plateDiffAt15Games;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean isOtp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double adjustedWinrate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double weightedDelta;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double matchupScore;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String tier;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean reliable;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double kda;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double goldPerMinute;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double killParticipation;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double deathShare;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double goldDiffAt15;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double csDiffAt15;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double xpDiffAt15;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double killDiffAt15;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double levelDiffAt15;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Double turretPlateDiffAt15;

    public ProfileLeafStats() {}

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
}
