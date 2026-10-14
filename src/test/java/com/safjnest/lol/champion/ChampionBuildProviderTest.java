package com.safjnest.lol.champion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryRecordParser;
import org.junit.Test;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ChampionBuildProviderTest {

    @Test
    public void providerBatchContractIsOneHundredRecords() {
        assertEquals(100, ChampionBuildProvider.BATCH_SIZE);
    }

    @Test
    public void skipsBuildRecordWithoutTimeline() {
        QueryRecord record = QueryRecordParser.fromMap(Map.of("build", "{\"build\":{},\"skill_order\":[]}"));

        assertNull(ChampionBuildProvider.parse(record, new Filter()));
    }

    @Test
    public void arenaEntryPointRejectsStandardScopeBeforeLoadingSources() {
        Filter filter = Filter.championBuild(1, "26.19.123", GameQueueType.ARAM);
        assertThrows(IllegalArgumentException.class,
            () -> ChampionBuildProvider.forEachArenaBatch(filter, batch -> {}));
    }

}
