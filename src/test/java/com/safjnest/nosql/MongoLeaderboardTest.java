package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.lol.model.summoner.Rank;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class MongoLeaderboardTest {

    @Test
    public void leaderboardPageProjectionContainsOnlyTheSelectedEmbeddedRank() {
        Document projection = Document.parse(MongoDB.leaderboardPageProjection(
                GameQueueType.RANKED_SOLO_5X5).toBsonDocument(Document.class,
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson());

        assertEquals(1, projection.getInteger("_id").intValue());
        assertEquals(1, projection.getInteger("ranks.RANKED_SOLO_5X5").intValue());
        assertEquals(1, projection.getInteger("masteries").intValue());
        assertEquals(null, projection.get("ranks.RANKED_FLEX_SR"));
    }

    @Test
    public void leaderboardFiltersUseTheSelectedQueueMmrPathForGlobalAndRegionalScopes() {
        String soloRegional = MongoDB.leaderboardFilter(
                TierType.DIAMOND, GameQueueType.RANKED_SOLO_5X5, LeagueShard.EUW1.name())
                .toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();
        String flexGlobal = MongoDB.leaderboardFilter(
                null, GameQueueType.RANKED_FLEX_SR, "GLOBAL")
                .toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();

        assertTrue(soloRegional.contains("ranks.RANKED_SOLO_5X5.mmr"));
        assertTrue(soloRegional.contains("EUW1"));
        assertTrue(soloRegional.contains("ranks.RANKED_SOLO_5X5.rank"));
        assertTrue(flexGlobal.contains("ranks.RANKED_FLEX_SR.mmr"));
        assertTrue(!flexGlobal.contains("ranks.queue"));
    }

    @Test
    public void leaderboardAggregatePipelinesGroupEmbeddedRanks() {
        List<Document> distribution = MongoDB.leaderboardDistributionPipeline(
                GameQueueType.RANKED_SOLO_5X5,
                LeagueShard.EUW1.name());
        assertNotNull(distribution.get(0).get("$match"));
        assertEquals("$ranks.RANKED_SOLO_5X5.rank", ((Document) distribution.get(1).get("$group")).getString("_id"));

        List<Document> regions = MongoDB.leaderboardTopRegionsPipeline(
                GameQueueType.RANKED_SOLO_5X5,
                TierType.DIAMOND);
        assertNotNull(regions.get(0).get("$match"));
        assertEquals("$region", ((Document) regions.get(1).get("$group")).getString("_id"));
        assertEquals(new Document("players", -1).append("_id", 1), regions.get(2).get("$sort"));
    }

    @Test
    public void rankDocumentsKeepMmrForLeaderboardOrdering() {
        Document document = MongoDB.toDocument(new Rank(
                TierDivisionType.DIAMOND_I,
                90,
                100,
                80));

        assertEquals(2790, document.getInteger("mmr").intValue());
    }

    @Test
    public void aggregateKeysKeepDistributionAndTopRegionsScopesSeparate() {
        assertEquals(
                "rank-distribution:RANKED_SOLO_5X5:EUW1",
                MongoDB.rankDistributionAggregateKey(GameQueueType.RANKED_SOLO_5X5, LeagueShard.EUW1.name()));
        assertEquals(
                "top-regions:RANKED_SOLO_5X5:DIAMOND",
                MongoDB.topRegionsAggregateKey(GameQueueType.RANKED_SOLO_5X5, TierType.DIAMOND));
        assertEquals(
                "page-count:RANKED_FLEX_SR:EUW1:MASTER",
                MongoDB.leaderboardCountAggregateKey(GameQueueType.RANKED_FLEX_SR, LeagueShard.EUW1.name(), TierType.MASTER));
    }
}
