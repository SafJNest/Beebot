package com.safjnest.r4b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.Test;

import com.R4B.api.dto.Filter;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LaneType;
import com.R4B.lol.LeagueShard;
import com.R4B.lol.RegionShard;
import com.R4B.lol.TierDivisionType;
import com.R4B.lol.TierType;

public class R4BSemanticHelpersTest {
    @Test
    public void preservesRankScoresAndHistoricalDivisionFive() {
        assertEquals(-1, TierDivisionType.getMmr(null, 99));
        assertEquals(-1, TierDivisionType.getMmr(TierDivisionType.UNRANKED, 99));
        assertEquals(0, TierDivisionType.getMmr(TierDivisionType.IRON_IV, -5));
        assertEquals(1250, TierDivisionType.getMmr(TierDivisionType.GOLD_IV, 50));
        assertEquals(2000, TierDivisionType.getMmr(TierDivisionType.EMERALD_IV, 0));
        assertEquals(2700, TierDivisionType.getMmr(TierDivisionType.DIAMOND_I, 0));
        assertEquals(10_500, TierDivisionType.getMmr(TierDivisionType.MASTER_I, 500));
        assertEquals(20_050, TierDivisionType.getMmr(TierDivisionType.GRANDMASTER_I, 50));
        assertEquals(30_999, TierDivisionType.getMmr(TierDivisionType.CHALLENGER_I, 999));
        for (TierDivisionType division : TierDivisionType.values()) {
            if ("V".equals(division.getDivision())) assertEquals(-1, TierDivisionType.getMmr(division, 100));
        }
    }

    @Test
    public void preservesIntegerAverageAndUnrankedExclusion() {
        assertEquals(TierType.UNRANKED, TierDivisionType.getAverageRank(null));
        assertEquals(TierType.UNRANKED, TierDivisionType.getAverageRank(List.of()));
        assertEquals(TierType.UNRANKED, TierDivisionType.getAverageRank(List.of(TierDivisionType.UNRANKED)));
        assertEquals(TierType.MASTER, TierDivisionType.getAverageRank(
            List.of(TierDivisionType.MASTER_I, TierDivisionType.DIAMOND_I)));
        assertEquals(TierType.DIAMOND, TierDivisionType.getAverageRank(
            List.of(TierDivisionType.DIAMOND_V, TierDivisionType.UNRANKED)));
    }

    @Test
    public void preservesPromotionBoundariesAndTierOrdering() {
        assertEquals(TierDivisionType.UNRANKED, TierDivisionType.getDivisionFromMmr(-1));
        assertEquals(TierDivisionType.IRON_IV, TierDivisionType.getDivisionFromMmr(0));
        assertEquals(TierDivisionType.GOLD_IV, TierDivisionType.getDivisionFromMmr(1200));
        assertEquals(TierDivisionType.DIAMOND_I, TierDivisionType.getDivisionFromMmr(9999));
        assertEquals(TierDivisionType.MASTER_I, TierDivisionType.getDivisionFromMmr(10_000));
        assertEquals(TierDivisionType.GRANDMASTER_I, TierDivisionType.getDivisionFromMmr(20_000));
        assertEquals(TierDivisionType.CHALLENGER_I, TierDivisionType.getDivisionFromMmr(30_000));
        assertEquals(List.of(TierType.CHALLENGER, TierType.GRANDMASTER, TierType.MASTER,
            TierType.DIAMOND, TierType.EMERALD), TierType.getHigherTiers(TierType.EMERALD));
    }

    @Test
    public void separatesReceivedQueueCanonicalQueueAndRankedSelection() {
        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO,
            GameQueueType.getFromId(420));
        assertEquals(GameQueueType.RANKED_SOLO_5X5,
            GameQueueType.canonicalQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO));
        assertEquals(GameQueueType.RANKED_SOLO_5X5,
            GameQueueType.rankedCanonicalQueue(GameQueueType.ARAM));
        assertEquals(GameQueueType.RANKED_FLEX_SR,
            GameQueueType.rankedCanonicalQueue(GameQueueType.RANKED_FLEX_SR));
        assertNull(GameQueueType.canonicalQueue(null));
        assertNull(GameQueueType.rankedCanonicalQueue(null));
        assertEquals(GameQueueType.RANKED_SOLO_5X5, GameQueueType.defaultQueue(null));
        assertFalse(GameQueueType.hasLane(GameQueueType.CHERRY));
        assertFalse(GameQueueType.hasLane(GameQueueType.STRAWBERRY));
        assertFalse(GameQueueType.hasLane(GameQueueType.ARAM));
        assertTrue(GameQueueType.hasLane(GameQueueType.TEAM_BUILDER_RANKED_SOLO));
    }

    @Test
    public void preservesPlayableOrderAndComplementaryBottomPartners() {
        assertEquals(List.of(LaneType.TOP, LaneType.JUNGLE, LaneType.MID, LaneType.BOT,
            LaneType.UTILITY), LaneType.playables());
        assertEquals(LaneType.UTILITY, LaneType.oppositeLane(LaneType.BOT));
        assertEquals(LaneType.BOT, LaneType.oppositeLane(LaneType.UTILITY));
        assertEquals(LaneType.MID, LaneType.oppositeLane(LaneType.MID));
        assertNull(LaneType.oppositeLane(null));
        assertTrue(LaneType.isDuo(LaneType.BOT, LaneType.UTILITY));
        assertTrue(LaneType.isDuo(LaneType.UTILITY, LaneType.BOT));
        assertFalse(LaneType.isDuo(LaneType.BOT, LaneType.BOT));
    }

    @Test
    public void preservesAccountRoutingSeparatelyFromMatchAndCacheRouting() {
        for (LeagueShard shard : List.of(LeagueShard.VN2, LeagueShard.OC1, LeagueShard.SG2,
                LeagueShard.PH2, LeagueShard.TH2, LeagueShard.TW2)) {
            assertEquals(RegionShard.SEA, shard.toRegionShard());
            assertEquals(RegionShard.ASIA, LeagueShard.getAccountRegion(shard));
            assertEquals("SEA", LeagueShard.cacheRegion(shard));
        }
        assertEquals(RegionShard.SEA, LeagueShard.getAccountRegion(LeagueShard.ID1));
        assertEquals(RegionShard.ASIA, LeagueShard.ID1.toAccountRegionShard());
        assertEquals(RegionShard.UNKNOWN, LeagueShard.getAccountRegion(LeagueShard.UNKNOWN));
        assertEquals("kr1", LeagueShard.getRegionCode(LeagueShard.KR));
        assertEquals("euw", LeagueShard.getRegionCode(LeagueShard.EUW1));
        assertEquals("NA1", LeagueShard.getRegionCode(LeagueShard.NA1));
    }

    @Test
    public void readsAndReemitsExistingOrdinalStateKeys() {
        String raw = "99|0|45|3|15.16|EUW1|55|22|1700000000000|1701000000000|EXACT";
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        Filter filter = Filter.fromStateKey(key);
        assertEquals(99, filter.champion());
        assertEquals(LaneType.MID, filter.lane());
        assertEquals(GameQueueType.RANKED_FLEX_SR, filter.queue());
        assertEquals(TierType.DIAMOND, filter.rank());
        assertEquals(Filter.RankBehavior.EXACT, filter.rankBehavior());
        assertEquals("15.16", filter.patch());
        assertEquals(LeagueShard.EUW1, filter.region());
        assertEquals(55, filter.opponent());
        assertEquals(22, filter.duo());
        assertEquals(1_700_000_000_000L, filter.timeStart());
        assertEquals(1_701_000_000_000L, filter.timeEnd());
        assertEquals(key, filter.toStateKey());
    }
}
