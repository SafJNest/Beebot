package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import com.safjnest.lol.utils.ChampionUtils;
import com.R4B.api.dto.*;
import com.R4B.api.dto.match.LiveGame;
import com.R4B.api.dto.match.LiveGame.*;
import com.R4B.api.dto.riot.BannedChampion;
import com.R4B.api.dto.riot.SpectatorGameInfo;
import com.R4B.api.dto.riot.SpectatorParticipant;
import com.R4B.lol.TeamType;

public final class LiveGameProjection {

    public static LiveGame fromR4J(SpectatorGameInfo source, Map<String, ProfileOverview> profiles) {
        return fromR4J(source, profiles, LiveGameProjection::championName);
    }

    public static LiveGame fromR4J(SpectatorGameInfo source, Map<String, ProfileOverview> profiles,
            IntFunction<String> championNames) {
        if (source == null) return LiveGame.empty();

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
                    championNames.apply(participant.getChampionId()),
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

    // ============================================================================

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

    private static int spellId(com.R4B.lol.SummonerSpellType spell) {
        return spell != null && spell.getValue() != null ? spell.getValue() : 0;
    }

    private static TeamType teamFromId(int id) {
        for (TeamType value : TeamType.values()) if (value.getValue() == id) return value;
        return null;
    }
}
