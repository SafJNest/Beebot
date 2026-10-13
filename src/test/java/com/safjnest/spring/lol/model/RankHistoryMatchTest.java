package com.safjnest.spring.lol.model;

import com.R4B.api.dto.*;
import com.R4B.api.dto.match.Match;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.match.RankHistoryMatch;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

public class RankHistoryMatchTest {

    @Test
    public void shouldMapBotLanePartners() {
        Match match = new Match();
        match.gameId = "EUW1_1";
        match.participants = List.of(
            participant("player", LaneType.BOT, TeamType.BLUE, 22),
            participant("duo", LaneType.UTILITY, TeamType.BLUE, 40),
            participant("enemy", LaneType.BOT, TeamType.RED, 67),
            participant("enemy-duo", LaneType.UTILITY, TeamType.RED, 12)
        );

        RankHistoryMatch result = RankHistoryMatch.from(match, "player");

        assertEquals("player", result.puuid());
        assertEquals(22, result.champion());
        assertEquals(Integer.valueOf(67), result.enemyChampion());
        assertEquals("enemy", result.enemyPuuid());
        assertEquals(Integer.valueOf(40), result.duoChampion());
        assertEquals("duo", result.duoPuuid());
        assertEquals(Integer.valueOf(12), result.duoEnemyChampion());
        assertEquals("enemy-duo", result.duoEnemyPuuid());
    }

    @Test
    public void shouldNotMapDuoOutsideBottomLane() {
        Match match = new Match();
        match.gameId = "EUW1_1";
        match.participants = List.of(
            participant("player", LaneType.TOP, TeamType.BLUE, 266),
            participant("enemy", LaneType.TOP, TeamType.RED, 36)
        );

        RankHistoryMatch result = RankHistoryMatch.from(match, "player");

        assertEquals(Integer.valueOf(36), result.enemyChampion());
        assertNull(result.duoChampion());
        assertNull(result.duoEnemyChampion());
    }

    private static Participant participant(String puuid, LaneType lane, TeamType team, int champion) {
        Participant participant = new Participant();
        participant.puuid = puuid;
        participant.lane = lane;
        participant.team = team;
        participant.champion = champion;
        return participant;
    }
}
