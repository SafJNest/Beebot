package com.safjnest.sound;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import com.safjnest.database.sql.database.BotDB.SoundPlay;
import com.safjnest.sound.SoundHistory.Group;

import org.junit.Test;

public class SoundHistoryTest {

    private static final Instant FROM = Instant.parse("2026-03-25T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    public void personalHistoryKeepsOnlyOnePageAndTheLookaheadEntry() {
        SoundHistory history = history("2", true, null, null);
        Instant time = FROM.plusSeconds(3600);

        for (int id = 1; id <= 10_000; id++)
            history.recordPlay(new SoundPlay(String.valueOf(id), "10", "2", "1", time, 1));

        List<SoundPlay> personal = history.getPersonalPlays("10");
        assertEquals(101, personal.size());
        assertEquals("10000", personal.get(0).id());
        assertEquals("9900", personal.get(100).id());
        assertEquals(10_000, history.getSoundTotals("10").getPlayCount());
        assertEquals(1, history.getSoundTotals("10").getTimeBuckets().size());
    }

    @Test
    public void indexesGuildsAndDeduplicatesTheSameSoundAcrossGuilds() {
        SoundHistory history = history("2", true, null, null);
        history.recordPlay(new SoundPlay("1", "10", "2", "1", FROM.plusSeconds(1), 1));
        history.recordPlay(new SoundPlay("2", "10", "2", "3", FROM.plusSeconds(2), 1));
        history.recordPlay(new SoundPlay("3", "11", "2", "3", FROM.plusSeconds(3), 2));
        history.recordPlay(new SoundPlay("4", "10", "9", "9", FROM.plusSeconds(4), 3));

        Group total = history.getTotals();
        assertEquals(4, total.getPlayCount());
        assertEquals(2, total.getUserTotals().size());
        assertEquals(3, total.getUserTotals().get("2").plays());
        assertEquals(2, total.getUserTotals().get("2").soundCount());
        assertEquals(1, history.getSoundTotals("10").getUserTotals().get("2").soundCount());
        assertEquals(1, history.getGuildTotals("10", "1").getPlayCount());
        assertEquals(1, history.getOtherGuildTotals("10", Set.of("1", "3")).getPlayCount());
    }

    @Test
    public void separatesThePreviousIntervalBeforeBuildingCurrentGroups() {
        SoundHistory history = history("2", true, null, null);
        history.recordPlay(new SoundPlay("1", "10", "2", "1", FROM.minusSeconds(1), 1));
        history.recordPlay(new SoundPlay("2", "10", "2", "1", FROM, 1));

        assertEquals(1, history.getPreviousPlays());
        assertEquals(1, history.getPreviousPlays("10"));
        assertEquals(1, history.getSoundTotals("10").getPlayCount());
        assertEquals("2", history.getPersonalPlays("10").get(0).id());
    }

    @Test
    public void cursorLimitsOnlyPersonalEntriesAndLeavesAggregatesComplete() {
        Instant cursorTime = FROM.plusSeconds(10);
        SoundHistory history = history("2", true, cursorTime, "20");

        history.recordPlay(new SoundPlay("19", "10", "2", "1", cursorTime, 1));
        history.recordPlay(new SoundPlay("20", "10", "2", "1", cursorTime, 1));
        history.recordPlay(new SoundPlay("21", "10", "2", "1", cursorTime.plusSeconds(1), 1));
        history.recordPlay(new SoundPlay("22", "10", "3", "1", cursorTime.minusSeconds(1), 1));

        assertEquals(4, history.getTotals().getPlayCount());
        assertEquals(1, history.getPersonalPlays("10").size());
        assertEquals("19", history.getPersonalPlays("10").get(0).id());
    }

    @Test
    public void summaryModeCountsWithoutBuildingDetailedHistory() {
        SoundHistory history = history("2", false, null, null);
        history.recordPlay(new SoundPlay("1", "10", "2", "1", FROM.plusSeconds(1), 1));

        assertEquals(1, history.getTotals().getPlayCount());
        assertTrue(history.getSoundTotals("10").getTimeBuckets().isEmpty());
        assertTrue(history.getSoundTotals("10").getSources().isEmpty());
        assertTrue(history.getPersonalPlays("10").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> history.getTotals().getUserTotals().clear());
    }

    /* -------------------------------------------------------------------------- */

    private SoundHistory history(String viewer, boolean detailed, Instant before, String beforeId) {
        SoundPeriod period = new SoundPeriod("7d", ZoneId.of("UTC"), FROM, TO, before, beforeId);

        return new SoundHistory(period, viewer, detailed);
    }
}
