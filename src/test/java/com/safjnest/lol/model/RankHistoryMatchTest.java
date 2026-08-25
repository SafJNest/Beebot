package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankHistoryMatch;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

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

        assertEquals(22, result.champion());
        assertEquals(Integer.valueOf(67), result.enemy());
        assertEquals(Integer.valueOf(40), result.duo());
        assertEquals(Integer.valueOf(12), result.duoEnemy());
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

        assertEquals(Integer.valueOf(36), result.enemy());
        assertNull(result.duo());
        assertNull(result.duoEnemy());
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
