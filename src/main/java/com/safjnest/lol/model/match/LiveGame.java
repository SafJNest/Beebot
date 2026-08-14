package com.safjnest.lol.model.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.champion.RuneSignature;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.utils.ChampionUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameModeType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameType;
import no.stelar7.api.r4j.basic.constants.types.lol.MapType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.shared.BannedChampion;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;

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
        List<Rank> ranks,
        List<Mastery> masteries,
        List<Stats<Integer>> championStats
    ) {
        public ProfileOverview {
            ranks = ranks != null ? List.copyOf(ranks) : List.of();
            masteries = masteries != null ? List.copyOf(masteries) : List.of();
            championStats = championStats != null ? List.copyOf(championStats) : List.of();
        }
    }

    public LiveGame {
        bans = copyBans(bans);
        participants = participants != null ? List.copyOf(participants) : List.of();
    }

    public static LiveGame empty() {
        return new LiveGame(true, null, null, null, null, null, null, null, null, Map.of(), List.of());
    }

    public static LiveGame fromR4J(SpectatorGameInfo source, Map<String, ProfileOverview> profiles) {
        if (source == null) return empty();

        Map<TeamType, List<Integer>> bans = new LinkedHashMap<>();
        if (source.getBannedChampions() != null) for (BannedChampion banned : source.getBannedChampions()) {
            if (banned == null) continue;
            TeamType team = teamFromId(banned.getTeamId());
            if (team != null) bans.computeIfAbsent(team, ignored -> new ArrayList<>()).add(banned.getChampionId());
        }

        List<Participant> participants = new ArrayList<>();
        if (source.getParticipants() != null) for (SpectatorParticipant participant : source.getParticipants()) {
            if (participant == null) continue;
            if (participant.getPuuid() == null) {
                participants.add(new Participant(
                    null,
                    championName(participant.getChampionId()),
                    participant.getChampionId(),
                    null,
                    participant.getTeam(),
                    null,
                    null,
                    null,
                    null
                ));
                continue;
            }
            participants.add(new Participant(
                participant.getPuuid(),
                participant.getRiotId(),
                participant.getChampionId(),
                participant.getProfileIconId(),
                participant.getTeam(),
                spellId(participant.getSpell1()),
                spellId(participant.getSpell2()),
                runes(participant),
                profiles != null ? profiles.get(participant.getPuuid()) : null
            ));
        }

        return new LiveGame(
            false,
            source.getGameId(),
            source.getGameStart(),
            source.getGameLength(),
            source.getPlatform(),
            source.getGameQueueConfig(),
            source.getGameMode(),
            source.getGameType(),
            source.getMap(),
            bans,
            participants
        );
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

    private static String championName(int championId) {
        var champion = ChampionUtils.getChampion(championId);
        return champion != null ? champion.getName() : String.valueOf(championId);
    }

    private static RuneSignature runes(SpectatorParticipant participant) {
        if (participant == null || participant.getPerks() == null) return null;
        List<Integer> perkIds = participant.getPerks().getPerkIds();
        if (perkIds == null || perkIds.isEmpty()) return null;

        int primaryEnd = Math.max(1, perkIds.size() - 5);
        int secondaryEnd = Math.max(primaryEnd, perkIds.size() - 3);
        return new RuneSignature(
            participant.getPerks().getPerkStyle(),
            perkIds.getFirst(),
            List.copyOf(perkIds.subList(1, primaryEnd)),
            participant.getPerks().getPerkSubStyle(),
            List.copyOf(perkIds.subList(primaryEnd, secondaryEnd)),
            List.copyOf(perkIds.subList(secondaryEnd, perkIds.size()))
        );
    }

    private static int spellId(no.stelar7.api.r4j.basic.constants.types.lol.SummonerSpellType spell) {
        return spell != null && spell.getValue() != null ? spell.getValue() : 0;
    }

    private static TeamType teamFromId(int id) {
        for (TeamType value : TeamType.values()) if (value.getValue() == id) return value;
        return null;
    }
}
