package com.safjnest.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.nosql.MongoMigration;
import com.safjnest.sql.QueryRecord;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class MongoMigrationTest {

    @Test
    public void migrationOptionsAreBatchableAndResumable() {
        MongoMigration.Options options = new MongoMigration.Options(true, 50, "run-1", true, 100);

        assertEquals(50, options.batchSize());
        assertEquals("run-1", options.runId());
        assertEquals(100, options.highWaterMark());
        assertEquals(500_000, MongoMigration.Options.defaults().batchSize());
    }

    @Test
    public void invalidBatchIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MongoMigration.Options(false, 0, "run", false, 0));
        assertThrows(IllegalArgumentException.class, () -> new MongoMigration.Options(false, 500_001, "run", false, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rankMigrationKeepsTheLatestCanonicalQueueRank() throws Exception {
        Method method = MongoMigration.class.getDeclaredMethod("mergeRank", Map.class, QueryRecord.class);
        method.setAccessible(true);
        Map<String, Map<GameQueueType, Rank>> ranksByPuuid = new LinkedHashMap<>();

        method.invoke(null, ranksByPuuid, rank("TEAM_BUILDER_RANKED_SOLO", "GOLD_II", 20));
        method.invoke(null, ranksByPuuid, rank("RANKED_SOLO_5X5", "PLATINUM_I", 80));

        Rank rank = ranksByPuuid.get("puuid").get(GameQueueType.RANKED_SOLO_5X5);
        assertEquals(TierDivisionType.PLATINUM_I, rank.tier());
        assertEquals(80, rank.lp());
    }

    private QueryRecord rank(String queue, String tier, int lp) {
        QueryRecord row = new QueryRecord();
        row.put("puuid", "puuid");
        row.put("queue", queue);
        row.put("rank", tier);
        row.put("lp", lp);
        row.put("wins", 10);
        row.put("losses", 5);
        return row;
    }
}
