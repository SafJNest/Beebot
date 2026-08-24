package com.safjnest.lol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.sql.QueryRecord;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class LeagueMessageOpggTitleTest {

    @Test
    public void rawRankedMatchShowsUnknownLp() throws Exception {
        String title = title(false, TierDivisionType.GOLD_II, -10, List.of(row("EUW1_1", false, null, 0, 0, false)));

        assertTrue(title.endsWith("? LP"));
    }

    @Test
    public void legacyRankedMatchUsesParticipantRankWhenTrackedIsMissing() throws Exception {
        String title = title(true, TierDivisionType.GOLD_II, 18, List.of(
            row("EUW1_1", false, TierDivisionType.GOLD_II, 50, 18, true)
        ));

        assertTrue(title.endsWith("+18 LP"));
    }

    @Test
    public void trackedLossUsesOneNegativeSign() throws Exception {
        String title = title(false, TierDivisionType.GOLD_II, -10, List.of(
            row("EUW1_0", true, TierDivisionType.GOLD_II, 50, -8, false),
            row("EUW1_1", true, TierDivisionType.GOLD_II, 40, -10, false)
        ));

        assertTrue(title.endsWith("-10 LP"));
        assertTrue(!title.contains("--10 LP"));
    }

    @Test
    public void trackedPlacementDoesNotLookLikePromotion() throws Exception {
        String title = title(true, TierDivisionType.IRON_IV, 0, List.of(
            row("EUW1_0", true, TierDivisionType.UNRANKED, 0, 0, true),
            row("EUW1_1", true, TierDivisionType.IRON_IV, 50, 0, true)
        ));

        assertEquals("Placement: WIN", title);
    }

    private static String title(boolean win, TierDivisionType rank, int gain, List<QueryRecord> rows) throws Exception {
        Match match = new Match();
        match.gameId = "EUW1_1";
        match.queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        Participant participant = new Participant();
        participant.win = win;
        participant.rank = rank;
        participant.gain = gain;
        Method method = LeagueMessage.class.getDeclaredMethod("getOpggMatchTitle", Match.class, Participant.class, List.class);
        method.setAccessible(true);
        return (String) method.invoke(null, match, participant, rows);
    }

    private static QueryRecord row(String gameId, boolean tracked, TierDivisionType rank, int lp, int gain, boolean win) {
        QueryRecord row = new QueryRecord();
        row.put("game_id", gameId);
        row.put("tracked", tracked);
        row.put("rank", rank);
        row.put("lp", lp);
        row.put("gain", gain);
        row.put("win", win);
        return row;
    }
}
