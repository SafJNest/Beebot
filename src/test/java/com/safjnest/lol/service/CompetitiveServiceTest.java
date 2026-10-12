package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.lol.*;

public class CompetitiveServiceTest {

    @Test
    public void rankCreatesCompetitiveEntryWithoutStatisticsOrPrimaryRole() {
        CompetitiveEntry entry = CompetitiveService.entry(
            "puuid",
            LeagueShard.EUW1,
            Map.of(GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.CHALLENGER_I, 50, 10, 2)),
            null,
            GameQueueType.RANKED_SOLO_5X5,
            1L
        );

        assertNotNull(entry);
        assertEquals(30050, entry.mmr());
        assertNull(entry.primary());
        assertNull(entry.otpChampionId());
    }

    @Test
    public void exactDivisionRangeIsDerivedFromMmr() {
        TierDivisionType.MmrRange range = TierDivisionType.getMmrRange(TierDivisionType.GOLD_IV);

        assertEquals(1200, range.minimum());
        assertEquals(Integer.valueOf(1300), range.maximum());
        assertEquals(TierDivisionType.GOLD_IV, TierDivisionType.getDivisionFromMmr(1200));
        assertEquals(TierDivisionType.GOLD_III, TierDivisionType.getDivisionFromMmr(1300));
    }

    @Test
    public void mergeRetainsKnownOptionalFieldsWhenTheNewRefreshDoesNotKnowThem() {
        CompetitiveEntry previous = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_000,
            LaneType.MID, 103, 1L
        );
        CompetitiveEntry refreshed = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_100,
            null, null, 2L
        );

        CompetitiveEntry merged = CompetitiveService.merge(previous, refreshed);

        assertEquals(LaneType.MID, merged.primary());
        assertEquals(Integer.valueOf(103), merged.otpChampionId());
    }

    @Test
    public void mergeReplacesKnownOptionalFieldsWhenTheNewRefreshKnowsThem() {
        CompetitiveEntry previous = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_000,
            LaneType.MID, 103, 1L
        );
        CompetitiveEntry refreshed = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_100,
            LaneType.JUNGLE, 64, 2L
        );

        CompetitiveEntry merged = CompetitiveService.merge(previous, refreshed);

        assertEquals(LaneType.JUNGLE, merged.primary());
        assertEquals(Integer.valueOf(64), merged.otpChampionId());
    }
}
