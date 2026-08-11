package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.util.List;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.lol.champion.ChampionBuildData;
import com.safjnest.lol.champion.ChampionBuildProvider;
import com.safjnest.lol.model.Filter;
import com.safjnest.sql.QueryRecord;

public class MongoChampionBuildRecordTest {

    @Test
    public void buildRecordPreservesParticipantRunes() throws Exception {
        Document match = new Document("_id", "EUW1_1");
        Document participant = new Document("win", true)
            .append("starterItems", List.of(1055))
            .append("boots", 3006)
            .append("supportItem", 0)
            .append("item0", 3078).append("item1", 3031).append("item2", 3036)
            .append("item3", 0).append("item4", 0).append("item5", 0)
            .append("skillOrder", List.of(1, 2, 3))
            .append("augments", List.of())
            .append("summonerSpell1", 4).append("summonerSpell2", 7)
            .append("primaryRunes", List.of(8000, 8005, 9104, 8014, 8299))
            .append("secondaryRunes", List.of(8400, 8444, 8451))
            .append("statsRunes", List.of(5008, 5008, 5011));
        Method method = MongoDB.class.getDeclaredMethod("championBuildRecord", Document.class, Document.class);
        method.setAccessible(true);

        QueryRecord record = (QueryRecord) method.invoke(null, match, participant);
        ChampionBuildData.Game game = ChampionBuildProvider.parse(record, new Filter());

        assertNotNull(game);
        assertNotNull(game.runes());
        assertEquals(8000, game.runes().primaryTree());
        assertEquals(8005, game.runes().keystone());
        assertEquals(List.of(9104, 8014, 8299), game.runes().primaryRunes());
        assertEquals(8400, game.runes().secondaryTree());
        assertEquals(List.of(8444, 8451), game.runes().secondaryRunes());
        assertEquals(List.of(5008, 5008, 5011), game.runes().statShards());
    }
}
