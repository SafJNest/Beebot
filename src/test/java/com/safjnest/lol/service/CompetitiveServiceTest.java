package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.TierDivisionUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

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
        assertEquals(8, entry.score());
        assertNull(entry.primary());
        assertNull(entry.otpChampionId());
    }

    @Test
    public void scoreRewardsWinVolumeAndPenalizesSmallSamples() {
        CompetitiveEntry smallSample = CompetitiveService.entry(
            "few-games", LeagueShard.EUW1,
            Map.of(GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.IRON_I, 99, 6, 4)),
            null, GameQueueType.RANKED_SOLO_5X5, 1L);
        CompetitiveEntry largeSample = CompetitiveService.entry(
            "many-games", LeagueShard.EUW1,
            Map.of(GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.IRON_I, 99, 2200, 1800)),
            null, GameQueueType.RANKED_SOLO_5X5, 1L);

        assertEquals(2, smallSample.score());
        assertEquals(400, largeSample.score());
        org.junit.Assert.assertTrue(largeSample.score() > smallSample.score());
    }

    @Test
    public void exactDivisionRangeIsDerivedFromMmr() {
        TierDivisionUtils.MmrRange range = TierDivisionUtils.getMmrRange(TierDivisionType.GOLD_IV);

        assertEquals(1200, range.minimum());
        assertEquals(Integer.valueOf(1300), range.maximum());
        assertEquals(TierDivisionType.GOLD_IV, TierDivisionUtils.getDivisionFromMmr(1200));
        assertEquals(TierDivisionType.GOLD_III, TierDivisionUtils.getDivisionFromMmr(1300));
    }

    @Test
    public void mergeRetainsKnownOptionalFieldsWhenTheNewRefreshDoesNotKnowThem() {
        CompetitiveEntry previous = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_000, 12,
            LaneType.MID, 103, 1L
        );
        CompetitiveEntry refreshed = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_100, 14,
            null, null, 2L
        );

        CompetitiveEntry merged = CompetitiveService.merge(previous, refreshed);

        assertEquals(LaneType.MID, merged.primary());
        assertEquals(Integer.valueOf(103), merged.otpChampionId());
        assertEquals(14, merged.score());
    }

    @Test
    public void mergeReplacesKnownOptionalFieldsWhenTheNewRefreshKnowsThem() {
        CompetitiveEntry previous = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_000, 12,
            LaneType.MID, 103, 1L
        );
        CompetitiveEntry refreshed = new CompetitiveEntry(
            "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 18_100, 14,
            LaneType.JUNGLE, 64, 2L
        );

        CompetitiveEntry merged = CompetitiveService.merge(previous, refreshed);

        assertEquals(LaneType.JUNGLE, merged.primary());
        assertEquals(Integer.valueOf(64), merged.otpChampionId());
    }
}
