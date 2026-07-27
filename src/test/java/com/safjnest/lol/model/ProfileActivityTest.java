package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileActivity;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ProfileActivityTest {

    @Test
    public void calculatesAllActivitySectionsInOnePass() {
        long first = Instant.parse("2026-07-27T10:00:00Z").toEpochMilli();
        long second = Instant.parse("2026-07-27T11:00:00Z").toEpochMilli();
        long third = Instant.parse("2026-07-27T15:00:00Z").toEpochMilli();
        ProfileActivity activity = ProfileActivity.from(List.of(
            match("one", first, first + 30 * 60 * 1000L, true, GameQueueType.TEAM_BUILDER_RANKED_SOLO, 1),
            match("two", second, second + 30 * 60 * 1000L, false, GameQueueType.TEAM_BUILDER_RANKED_SOLO, 2),
            match("three", third, third + 30 * 60 * 1000L, true, GameQueueType.ARAM, 3)
        ), "puuid", null);

        assertEquals(3, activity.coverage().games());
        assertEquals(2, activity.summary().wins());
        assertEquals(1, activity.summary().losses());
        assertEquals(2, activity.summary().sessionCount());
        assertEquals(168, activity.heatmap().cells().size());
        assertEquals(0, activity.heatmap().cells().get(0).day());
        assertEquals(0, activity.heatmap().cells().get(0).hour());
        assertEquals(23, activity.heatmap().cells().get(23).hour());
        assertEquals(1, activity.heatmap().cells().get(24).day());
        assertEquals(0, activity.heatmap().cells().get(24).hour());
        assertEquals(1, activity.recentSessions().get(0).games());
        assertEquals(2, activity.recentSessions().get(1).games());
        assertNull(activity.heatmap().cells().get(0).winrate());
    }

    private static Match match(
        String gameId,
        long start,
        long end,
        boolean win,
        GameQueueType queue,
        int champion
    ) {
        Match match = new Match();
        match.gameId = gameId;
        match.queue = queue;
        match.timeStart = start;
        match.timeEnd = end;
        Participant player = new Participant();
        player.puuid = "puuid";
        player.champion = champion;
        player.win = win;
        match.participants = List.of(player);
        return match;
    }
}
