package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    public void leaderboardPipelineUsesEmbeddedRanksAndFacet() {
        List<Document> pipeline = MongoDB.leaderboardPagePipeline(
                TierType.DIAMOND,
                GameQueueType.RANKED_SOLO_5X5,
                LeagueShard.EUW1.name(),
                50,
                50);

        assertNotNull(pipeline.get(0).get("$match"));
        assertEquals("$ranks", pipeline.get(1).getString("$unwind"));
        assertNotNull(pipeline.get(2).get("$match"));

        Document facet = pipeline.get(3).get("$facet", Document.class);
        assertNotNull(facet);
        assertNotNull(facet.get("total"));

        List<?> pageStages = facet.getList("page", Object.class);
        Document sort = ((Document) pageStages.get(0)).get("$sort", Document.class);
        assertEquals(new Document("ranks.mmr", -1).append("_id", 1), sort);
        assertEquals(50, ((Document) pageStages.get(1)).getInteger("$skip").intValue());
        assertEquals(50, ((Document) pageStages.get(2)).getInteger("$limit").intValue());

        Document projection = ((Document) pageStages.get(3)).get("$project", Document.class);
        assertEquals(List.of("$ranks"), projection.get("ranks"));
        assertEquals(1, projection.getInteger("masteries").intValue());
    }

    @Test
    public void leaderboardAggregatePipelinesGroupEmbeddedRanks() {
        List<Document> distribution = MongoDB.leaderboardDistributionPipeline(
                GameQueueType.RANKED_SOLO_5X5,
                LeagueShard.EUW1.name());
        assertNotNull(distribution.get(0).get("$match"));
        assertEquals("$ranks", distribution.get(1).getString("$unwind"));
        assertEquals("$ranks.rank", ((Document) distribution.get(3).get("$group")).getString("_id"));

        List<Document> regions = MongoDB.leaderboardTopRegionsPipeline(
                GameQueueType.RANKED_SOLO_5X5,
                TierType.DIAMOND);
        assertNotNull(regions.get(0).get("$match"));
        assertEquals("$ranks", regions.get(1).getString("$unwind"));
        assertEquals("$region", ((Document) regions.get(3).get("$group")).getString("_id"));
        assertEquals(new Document("players", -1).append("_id", 1), regions.get(4).get("$sort"));
    }

    @Test
    public void rankDocumentsKeepMmrForLeaderboardOrdering() {
        Document document = MongoDB.toDocument(new Rank(
                GameQueueType.RANKED_SOLO_5X5,
                TierDivisionType.DIAMOND_I,
                90,
                100,
                80));

        assertEquals(2790, document.getInteger("mmr").intValue());
    }
}
