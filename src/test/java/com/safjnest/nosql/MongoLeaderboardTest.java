package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.lol.model.summoner.Rank;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class MongoLeaderboardTest {

    @Test
    public void competitiveFilterUsesQueueRegionRoleAndMmrRange() {
        String value = MongoDB.competitiveFilter(
                TierType.DIAMOND,
                GameQueueType.RANKED_SOLO_5X5,
                LeagueShard.EUW1.name(),
                LaneType.UTILITY)
            .toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();

        assertTrue(value.contains("RANKED_SOLO_5X5"));
        assertTrue(value.contains("EUW1"));
        assertTrue(value.contains("UTILITY"));
        assertTrue(value.contains("2400"));
        assertTrue(value.contains("10000"));
        assertFalse(value.contains("ranks."));
    }

    @Test
    public void competitiveFilterUsesOtpChampionId() {
        String value = MongoDB.competitiveFilter(
                TierType.CHALLENGER,
                GameQueueType.RANKED_SOLO_5X5,
                LeagueShard.EUW1.name(),
                LaneType.UTILITY,
                40)
            .toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();

        assertTrue(value.contains("otpChampionId"));
        assertTrue(value.contains("40"));
    }

    @Test
    public void competitiveFilterKeepsGlobalScopeAndChallengerOpenEnded() {
        String value = MongoDB.competitiveFilter(
                TierType.CHALLENGER,
                GameQueueType.RANKED_FLEX_SR,
                "GLOBAL",
                null)
            .toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();

        assertTrue(value.contains("RANKED_FLEX_SR"));
        assertTrue(value.contains("30000"));
        assertFalse(value.contains("region"));
        assertFalse(value.contains("primary"));
    }

    @Test
    public void rankDocumentsDoNotPersistMmr() {
        Document document = MongoDB.toDocument(new Rank(
                TierDivisionType.DIAMOND_I,
                90,
                100,
                80));

        assertEquals("DIAMOND_I", document.getString("rank"));
        assertFalse(document.containsKey("mmr"));
    }

    @Test
    public void aggregateKeysKeepDistributionAndTopRegionsScopesSeparate() {
        assertEquals(
                "rank-distribution:RANKED_SOLO_5X5:EUW1",
                MongoDB.rankDistributionAggregateKey(GameQueueType.RANKED_SOLO_5X5, LeagueShard.EUW1.name()));
        assertEquals(
                "top-regions:RANKED_FLEX_SR:MASTER",
                MongoDB.topRegionsAggregateKey(GameQueueType.RANKED_FLEX_SR, TierType.MASTER));
    }
}
