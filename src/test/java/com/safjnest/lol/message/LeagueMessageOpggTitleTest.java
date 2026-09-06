package com.safjnest.lol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.sql.QueryRecord;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class LeagueMessageOpggTitleTest {

    @Test
    public void rawRankedMatchShowsUnknownLp() throws Exception {
        String title = title(false, null, List.of(row("EUW1_1", false, null, null, null, null, false)));

        assertTrue(title.endsWith("? LP"));
    }

    @Test
    public void legacyRankedMatchUsesParticipantRankWhenTrackedIsMissing() throws Exception {
        String title = title(true, new RankProgress(TierDivisionType.GOLD_II, 50, 18, null, null), List.of(
            row("EUW1_1", false, TierDivisionType.GOLD_II, 50, 18, null, true)
        ));

        assertTrue(title.endsWith("+18 LP"));
    }

    @Test
    public void trackedLossUsesOneNegativeSign() throws Exception {
        String title = title(false, new RankProgress(TierDivisionType.GOLD_II, 40, -10, TierDivisionType.GOLD_II, 50), List.of(
            row("EUW1_1", true, TierDivisionType.GOLD_II, 40, -10, TierDivisionType.GOLD_II, false)
        ));

        assertTrue(title.endsWith("-10 LP"));
        assertTrue(!title.contains("--10 LP"));
    }

    @Test
    public void trackedPlacementDoesNotLookLikePromotion() throws Exception {
        String title = title(true, new RankProgress(TierDivisionType.IRON_IV, 50, 50, TierDivisionType.UNRANKED, 0), List.of(
            row("EUW1_1", true, TierDivisionType.IRON_IV, 50, 50, TierDivisionType.UNRANKED, true)
        ));

        assertEquals("Placement: WIN", title);
    }

    @Test
    public void completeZeroGainIsNotUnknown() throws Exception {
        String title = title(true, new RankProgress(TierDivisionType.GOLD_II, 54, 0, TierDivisionType.GOLD_II, 54), List.of(
            row("EUW1_1", true, TierDivisionType.GOLD_II, 54, 0, TierDivisionType.GOLD_II, true)
        ));

        assertTrue(title.endsWith("0 LP"));
        assertTrue(!title.contains("? LP"));
    }

    @Test
    public void mapsCanonicalProfileQueuesForDisplay() throws Exception {
        Method method = LeagueMessage.class.getDeclaredMethod("displayQueue", CanonicalQueue.class);
        method.setAccessible(true);

        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, method.invoke(null, CanonicalQueue.RANKED_SOLO));
        assertEquals(GameQueueType.CHERRY, method.invoke(null, CanonicalQueue.ARENA));
    }

    private static String title(boolean win, RankProgress progress, List<QueryRecord> rows) throws Exception {
        Match match = new Match();
        match.gameId = "EUW1_1";
        match.queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        Participant participant = new Participant();
        participant.win = win;
        participant.rankProgress = progress;
        Method method = LeagueMessage.class.getDeclaredMethod("getOpggMatchTitle", Match.class, Participant.class, List.class);
        method.setAccessible(true);
        return (String) method.invoke(null, match, participant, rows);
    }

    private static QueryRecord row(
            String gameId,
            boolean tracked,
            TierDivisionType rank,
            Integer lp,
            Integer gain,
            TierDivisionType previousRank,
            boolean win) {
        QueryRecord row = new QueryRecord();
        row.put("game_id", gameId);
        row.put("tracked", tracked);
        if (rank != null) {
            QueryRecord progress = new QueryRecord();
            progress.put("rank", rank);
            progress.put("lp", lp);
            progress.put("gain", gain);
            if (previousRank != null) {
                progress.put("previousRank", previousRank);
                progress.put("previousLp", 0);
            }
            row.put("rankProgress", progress);
        }
        row.put("win", win);
        return row;
    }
}
