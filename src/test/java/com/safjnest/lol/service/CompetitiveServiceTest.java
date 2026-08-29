package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.summoner.Rank;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
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
        assertNull(entry.primary());
        assertNull(entry.otpChampionId());
    }
}
