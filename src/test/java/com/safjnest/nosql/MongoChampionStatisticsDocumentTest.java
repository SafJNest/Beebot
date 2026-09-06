package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.statistics.ChampionStatsDocument;
import com.safjnest.lol.model.statistics.shared.ChampionLeafStats;
import com.safjnest.lol.model.statistics.shared.ChampionNode;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class MongoChampionStatisticsDocumentTest {

    @Test
    public void bsonRoundTripStoresOneScopeDocumentWithRawLeavesOnly() {
        ChampionStatsScope scope = new ChampionStatsScope(GameQueueType.TEAM_BUILDER_RANKED_SOLO,
            null, Filter.RankBehavior.GREATER_OR_EQUAL, "15.14", null, 0, 0);
        ChampionStatsDocument source = new ChampionStatsDocument(scope, 10, 8, "15.13");
        ChampionNode champion = new ChampionNode(3);
        ChampionLeafStats leaf = new ChampionLeafStats();
        leaf.games = 4;
        leaf.wins = 3;
        leaf.kills = 12;
        leaf.csm = 28;
        leaf.csmGames = 4;
        champion.lanes.put(LaneType.TOP.name(), leaf);
        source.champions.put(1, champion);

        Document bson = JsonCodec.toDocument(source);
        ChampionStatsDocument decoded = JsonCodec.fromDocument(bson, ChampionStatsDocument.class);

        assertEquals(scope.toKey(), bson.getString("_id"));
        assertTrue(bson.containsKey("scope"));
        assertTrue(bson.containsKey("champions"));
        assertFalse(bson.containsKey("statistics"));
        assertFalse(bson.containsKey("overview"));
        assertFalse(bson.containsKey("filter"));
        assertFalse(bson.containsKey("laneStats"));
        assertEquals(4, decoded.champion(1).lane(LaneType.TOP.name()).games);
        assertEquals(3, decoded.champion(1).bans);
    }
}
