package com.R4B.api.dto.match;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.shared.ProfileLeafStats;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.lol.GameModeType;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.GameType;
import com.R4B.lol.LeagueShard;
import com.R4B.lol.MapType;
import com.R4B.lol.TeamType;

public record LiveGame(
    boolean notInGame,
    Long gameId,
    Long startedAt,
    Long gameLength,
    LeagueShard platform,
    GameQueueType queue,
    GameModeType mode,
    GameType type,
    MapType map,
    Map<TeamType, List<Integer>> bans,
    List<Participant> participants
) {

    public record Participant(
        String puuid,
        String riotId,
        int championId,
        Long icon,
        TeamType team,
        Integer summonerSpell1,
        Integer summonerSpell2,
        RuneSignature runes,
        ProfileOverview profileOverview
    ) {}

    public record ProfileOverview(
        Summoner summoner,
        Map<GameQueueType, Rank> ranks,
        List<Mastery> masteries,
        Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> championStats
    ) {
        public ProfileOverview(
            Summoner summoner,
            Map<GameQueueType, Rank> ranks,
            List<Mastery> masteries,
            List<com.R4B.api.dto.statistics.shared.ProfileLeafStats> championStats
        ) {
            this(summoner, ranks, masteries, indexedChampionStats(championStats));
        }

        public ProfileOverview {
            ranks = ranks != null ? Map.copyOf(ranks) : Map.of();
            masteries = masteries != null ? List.copyOf(masteries) : List.of();
            championStats = championStats != null ? Map.copyOf(championStats) : Map.of();
        }

        private static void copyProfile(ProfileLeafStats source, ProfileLeafStats target) {
            target.blueGames = source.blueGames;
            target.blueWins = source.blueWins;
            target.redGames = source.redGames;
            target.redWins = source.redWins;
            target.damage = source.damage;
            target.damageBuilding = source.damageBuilding;
            target.damageTaken = source.damageTaken;
            target.healing = source.healing;
            target.vision = source.vision;
            target.ward = source.ward;
            target.wardKilled = source.wardKilled;
            target.cs = source.cs;
            target.gold = source.gold;
            target.lpGain = source.lpGain;
            target.championLevelTotal = source.championLevelTotal;
            target.doubles = source.doubles;
            target.triples = source.triples;
            target.quadruples = source.quadruples;
            target.pentas = source.pentas;
            target.q = source.q;
            target.w = source.w;
            target.e = source.e;
            target.r = source.r;
            target.d = source.d;
            target.f = source.f;
            target.spellDCasts = source.spellDCasts;
            target.spellFCasts = source.spellFCasts;
            target.arenaFirst = source.arenaFirst;
            target.arenaSecond = source.arenaSecond;
            target.arenaThird = source.arenaThird;
            target.arenaPlacementSum = source.arenaPlacementSum;
            target.playtime = source.playtime;
            target.lastPlayedAt = source.lastPlayedAt;
            target.killParticipationSum = source.killParticipationSum;
            target.deathShareSum = source.deathShareSum;
            target.goldDiffAt15Sum = source.goldDiffAt15Sum;
            target.goldDiffAt15Games = source.goldDiffAt15Games;
            target.csDiffAt15Sum = source.csDiffAt15Sum;
            target.csDiffAt15Games = source.csDiffAt15Games;
            target.xpDiffAt15Sum = source.xpDiffAt15Sum;
            target.xpDiffAt15Games = source.xpDiffAt15Games;
            target.killDiffAt15Sum = source.killDiffAt15Sum;
            target.killDiffAt15Games = source.killDiffAt15Games;
            target.levelDiffAt15Sum = source.levelDiffAt15Sum;
            target.levelDiffAt15Games = source.levelDiffAt15Games;
            target.plateDiffAt15Sum = source.plateDiffAt15Sum;
            target.plateDiffAt15Games = source.plateDiffAt15Games;
            target.isOtp = source.isOtp;
            target.adjustedWinrate = source.adjustedWinrate;
            target.weightedDelta = source.weightedDelta;
            target.matchupScore = source.matchupScore;
            target.tier = source.tier;
            target.reliable = source.reliable;
            target.kda = source.kda;
            target.goldPerMinute = source.goldPerMinute;
            target.killParticipation = source.killParticipation;
            target.deathShare = source.deathShare;
            target.goldDiffAt15 = source.goldDiffAt15;
            target.csDiffAt15 = source.csDiffAt15;
            target.xpDiffAt15 = source.xpDiffAt15;
            target.killDiffAt15 = source.killDiffAt15;
            target.levelDiffAt15 = source.levelDiffAt15;
            target.turretPlateDiffAt15 = source.turretPlateDiffAt15;
            target.games = source.games;
            target.wins = source.wins;
            target.kills = source.kills;
            target.deaths = source.deaths;
            target.assists = source.assists;
        }
        private static Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> indexedChampionStats(List<com.R4B.api.dto.statistics.shared.ProfileLeafStats> values) {
            Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> result = new LinkedHashMap<>();
            if (values != null) for (com.R4B.api.dto.statistics.shared.ProfileLeafStats value : values)
                if (value != null && value.reference != null) {
                    com.R4B.api.dto.statistics.shared.ProfileLeafStats copy = new com.R4B.api.dto.statistics.shared.ProfileLeafStats();
                    copyProfile(value, copy);
                    result.put((Integer) value.reference, copy);
                }
            return result;
        }
    }

    public LiveGame {
        bans = copyBans(bans);
        participants = participants != null ? List.copyOf(participants) : List.of();
    }

    public static LiveGame empty() {
        return new LiveGame(true, null, null, null, null, null, null, null, null, Map.of(), List.of());
    }

    private static Map<TeamType, List<Integer>> copyBans(Map<TeamType, List<Integer>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<TeamType, List<Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<TeamType, List<Integer>> entry : source.entrySet()) {
            if (entry.getKey() != null) result.put(entry.getKey(),
                entry.getValue() != null ? List.copyOf(entry.getValue()) : List.of());
        }
        return Map.copyOf(result);
    }
}
