package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class MongoChampionStatisticsDocumentTest {

    @Test
    public void writerCreatesOneReadyDocumentWithAllChampions() throws Exception {
        Filter filter = new Filter()
            .setChampion(0)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setPatch("15.14")
            .setRank(null)
            .setRegion(null)
            .setLane(null);
        ChampionStatistics first = populated(filterForChampion(filter, 1));
        ChampionStatistics second = populated(filterForChampion(filter, 2));

        Method writer = MongoDB.class.getDeclaredMethod(
            "championStatisticsDocument", Filter.class, Map.class, boolean.class);
        writer.setAccessible(true);
        Document document = (Document) writer.invoke(null, filter, Map.of(1, first, 2, second), true);

        assertEquals(filter.genericKey(), document.getString("_id"));
        assertEquals(filter.genericKey(), document.getString("filterKey"));
        assertTrue(document.getBoolean("ready"));
        assertFalse(document.containsKey("championId"));
        Document statistics = document.get("statistics", Document.class);
        assertNotNull(statistics);
        assertEquals(2, statistics.size());
        assertTrue(statistics.containsKey("1"));
        assertTrue(statistics.containsKey("2"));

        Method reader = MongoDB.class.getDeclaredMethod(
            "readAggregatedChampionStatistics", Document.class, int.class);
        reader.setAccessible(true);
        ChampionStatistics decodedFirst = (ChampionStatistics) reader.invoke(null, document, 1);
        ChampionStatistics decodedSecond = (ChampionStatistics) reader.invoke(null, document, 2);
        assertNotNull(decodedFirst);
        assertNotNull(decodedSecond);
        assertEquals(first.overview(), decodedFirst.overview());
        assertEquals(second.overview(), decodedSecond.overview());
        assertEquals(filter.genericKey(), decodedFirst.filter().genericKey());
        assertEquals(filter.genericKey(), decodedSecond.filter().genericKey());
    }

    @Test
    public void writerOmitsChampionWithoutDataButKeepsFilterDocument() throws Exception {
        Filter filter = new Filter()
            .setChampion(0)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setPatch("15.14")
            .setRank(null)
            .setRegion(null)
            .setLane(null);
        Method writer = MongoDB.class.getDeclaredMethod(
            "championStatisticsDocument", Filter.class, Map.class, boolean.class);
        writer.setAccessible(true);
        Document document = (Document) writer.invoke(null, filter,
            Map.of(1, empty(filterForChampion(filter, 1))), true);

        assertEquals(filter.genericKey(), document.getString("_id"));
        assertTrue(document.getBoolean("ready"));
        assertTrue(document.get("statistics", Document.class).isEmpty());
    }

    private static Filter filterForChampion(Filter source, int champion) {
        return new Filter()
            .setChampion(champion)
            .setQueue(source.queue())
            .setPatch(source.patch())
            .setRank(source.rank())
            .setRegion(source.region())
            .setLane(source.lane());
    }

    private static ChampionStatistics empty(Filter filter) {
        return new ChampionStatistics(filter, 0, 0, 0, 0, 0, 0, 0,
            List.of(), Map.of());
    }

    private static ChampionStatistics populated(Filter filter) {
        return new ChampionStatistics(filter, 10, 2, 0, 1, 0.5, 0.2, 0,
            List.of(), Map.of());
    }
}
