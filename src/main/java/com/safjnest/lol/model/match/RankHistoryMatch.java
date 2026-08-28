package com.safjnest.lol.model.match;

import java.util.List;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public record RankHistoryMatch(
    String gameId,
    GameQueueType queue,
    String patch,
    long timeStart,
    long timeEnd,
    boolean win,
    LaneType lane,
    String puuid,
    int champion,
    Integer enemyChampion,
    String enemyPuuid,
    Integer duoChampion,
    String duoPuuid,
    Integer duoEnemyChampion,
    String duoEnemyPuuid,
    RankProgress rankProgress
) {

    public static RankHistoryMatch from(Match match, String puuid) {
        if (match == null || puuid == null || puuid.isBlank()) return null;
        Participant player = participant(match.participants, puuid);
        if (player == null) return null;

        LaneType duoLane = duoLane(player.lane);
        Participant enemy = participant(match.participants, player.lane, player.team, false);
        Participant duo = participant(match.participants, duoLane, player.team, true);
        Participant duoEnemy = participant(match.participants, duoLane, player.team, false);
        return new RankHistoryMatch(match.gameId, match.queue, match.patch, match.timeStart, match.timeEnd, player.win,
            player.lane, player.puuid, player.champion, champion(enemy), puuid(enemy), champion(duo), puuid(duo),
            champion(duoEnemy), puuid(duoEnemy), player.rankProgress);
    }

    // ============================================================================

    private static Participant participant(List<Participant> participants, String puuid) {
        if (participants == null) return null;
        for (Participant participant : participants) {
            if (participant != null && puuid.equals(participant.puuid)) return participant;
        }
        return null;
    }

    private static Participant participant(List<Participant> participants, LaneType lane, TeamType team, boolean sameTeam) {
        if (participants == null || lane == null || team == null) return null;
        for (Participant participant : participants) {
            if (participant == null || participant.lane != lane || participant.team == null) continue;
            if ((sameTeam && participant.team == team) || (!sameTeam && participant.team != team)) return participant;
        }
        return null;
    }

    private static LaneType duoLane(LaneType lane) {
        if (lane == null) return null;
        return switch (lane) {
            case BOT -> LaneType.UTILITY;
            case UTILITY -> LaneType.BOT;
            default -> null;
        };
    }

    private static Integer champion(Participant participant) {
        return participant == null ? null : participant.champion;
    }

    private static String puuid(Participant participant) {
        return participant == null ? null : participant.puuid;
    }
}
