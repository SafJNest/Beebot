package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Method;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.safjnest.lol.model.summoner.Summoner;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class MongoSummonerHistoryTest {
    @Test
    public void matchIdentityIsOnlyWrittenForNewSummoners() throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("summonerUpdate", Summoner.class, String.class, boolean.class);
        method.setAccessible(true);
        Summoner summoner = new Summoner("puuid", "Old Name#TAG", LeagueShard.EUW1, 42, 1);
        Document history = bson((Bson) method.invoke(null, summoner, null, true));
        assertEquals("Old Name#TAG", history.get("$setOnInsert", Document.class).getString("riotId"));
        assertEquals("oldnametag", history.get("$setOnInsert", Document.class).getString("riotSearch"));
        assertFalse(history.get("$set", Document.class).containsKey("riotId"));
        assertFalse(history.get("$set", Document.class).containsKey("riotSearch"));

        Document current = bson((Bson) method.invoke(null, summoner, null, false));
        assertEquals("Old Name#TAG", current.get("$set", Document.class).getString("riotId"));
        assertEquals("oldnametag", current.get("$set", Document.class).getString("riotSearch"));
    }

    private static Document bson(Bson value) {
        return Document.parse(value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson());
    }
}
