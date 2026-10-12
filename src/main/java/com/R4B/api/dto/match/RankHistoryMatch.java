package com.R4B.api.dto.match;

import com.R4B.api.dto.*;
import com.R4B.lol.*;

import java.util.List;

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
        return LaneType.oppositeLane(lane) == lane ? null : LaneType.oppositeLane(lane);
    }

    private static Integer champion(Participant participant) {
        return participant == null ? null : participant.champion;
    }

    private static String puuid(Participant participant) {
        return participant == null ? null : participant.puuid;
    }
}
