package com.safjnest.nosql;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.safjnest.lol.model.Filter;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class MongoMatchFilterTest {

    @Test
    public void everyPlayerFilterCombinationKeepsChampionAndLaneOnTheRequestedParticipant() throws Exception {
        for (int mask = 0; mask < 8; mask++) {
            for (boolean patch : List.of(false, true)) {
                boolean champion = (mask & 1) != 0;
                boolean lane = (mask & 2) != 0;
                boolean queue = (mask & 4) != 0;
                Filter filter = Filter.summoner(100, 1_000).setRegion(LeagueShard.EUW1)
                    .setChampion(champion ? 887 : 0).setLane(lane ? LaneType.TOP : null)
                    .setQueue(queue ? GameQueueType.TEAM_BUILDER_RANKED_SOLO : null)
                    .setPatch(patch ? "26.19" : null);
                BsonDocument query = flatten(playerFilter(filter, LeagueShard.NA1, 200, 900));
                List<String> fields = new ArrayList<>(query.keySet());
                assertEquals(champion || lane ? "participants" : "participants.puuid", fields.get(0));
                assertEquals("region", fields.get(1));
                assertEquals("EUW1", query.getString("region").getValue());
                assertEquals(queue, query.containsKey("queue"));
                assertEquals(patch, query.containsKey("patchMajor"));
                assertEquals(200, query.getDocument("timeStart").getNumber("$gte").longValue());
                assertEquals(900, query.getDocument("timeEnd").getNumber("$lte").longValue());
                assertEquals(List.of("timeStart", "timeEnd"), fields.subList(fields.size() - 2, fields.size()));
                if (champion || lane) {
                    BsonDocument participant = query.getDocument("participants").getDocument("$elemMatch");
                    assertEquals("player", participant.getString("puuid").getValue());
                    assertEquals(champion, participant.containsKey("champion"));
                    assertEquals(lane, participant.containsKey("lane"));
                    if (champion) assertEquals(887, participant.getInt32("champion").getValue());
                    if (lane) assertEquals("TOP", participant.getString("lane").getValue());
                    assertFalse(query.containsKey("participants.champion"));
                    assertFalse(query.containsKey("participants.lane"));
                } else {
                    assertEquals("player", query.getString("participants.puuid").getValue());
                    assertFalse(query.toJson().contains("$elemMatch"));
                }
            }
        }
    }

    @Test
    public void knownShardScopesFiltersAndRegionDiscoveryStaysCrossRegion() throws Exception {
        Bson aliases = Filters.in("queue", "TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5");
        Method method = MongoDB.class.getDeclaredMethod("scopedMatchFilter", String.class,
            LeagueShard.class, Bson.class, long.class, long.class);
        method.setAccessible(true);
        BsonDocument scoped = flatten(render((Bson) method.invoke(null, "player", LeagueShard.EUW1, aliases, 100L, 200L)));
        assertEquals(List.of("participants.puuid", "region", "queue", "timeStart", "timeEnd"),
            new ArrayList<>(scoped.keySet()));
        BsonDocument discovery = flatten(render((Bson) method.invoke(null, "player", null, aliases, 0L, 0L)));
        assertFalse(discovery.containsKey("region"));
        assertFalse(discovery.toJson().contains("$elemMatch"));
        assertEquals(2, discovery.getDocument("queue").getArray("$in").size());
        BsonDocument fallback = flatten(playerFilter(Filter.summoner(0, 0), LeagueShard.EUW1, 0, 0));
        assertEquals("EUW1", fallback.getString("region").getValue());
    }

    @Test
    public void independentOpponentAndDuoConditionsDoNotBindToThePlayer() throws Exception {
        Filter filter = Filter.summoner(0, 0).setRegion(LeagueShard.EUW1).setOpponent(1).setDuo(2);
        BsonDocument query = playerFilter(filter, null, 0, 0);
        String json = query.toJson();
        assertTrue(json.contains("participants.puuid"));
        assertTrue(json.contains("participants.champion"));
        assertFalse(json.contains("$elemMatch"));
        assertTrue(query.containsKey("$and"));
    }

    @Test
    public void globalChampionQueryUsesDottedSingleFieldAndBindsMultipleParticipantFields() throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("championMatchFilter", Filter.class, String.class);
        method.setAccessible(true);
        Filter filter = Filter.championBuild(887, "26.19", null);
        BsonDocument champion = flatten(render((Bson) method.invoke(null, filter, null)));
        assertEquals(887, champion.getInt32("participants.champion").getValue());
        assertFalse(champion.toJson().contains("$elemMatch"));
        filter.setLane(LaneType.TOP);
        BsonDocument scoped = flatten(render((Bson) method.invoke(null, filter, "player")));
        BsonDocument participant = scoped.getDocument("participants").getDocument("$elemMatch");
        assertEquals(List.of("puuid", "champion", "lane"), new ArrayList<>(participant.keySet()));
        assertEquals("player", participant.getString("puuid").getValue());
        assertEquals(887, participant.getInt32("champion").getValue());
        assertEquals("TOP", participant.getString("lane").getValue());
    }

    private static BsonDocument playerFilter(Filter filter, LeagueShard shard, long start, long end) throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("buildMatchFilter", String.class,
            LeagueShard.class, Filter.class, long.class, long.class);
        method.setAccessible(true);
        return render((Bson) method.invoke(null, "player", shard, filter, start, end));
    }

    private static BsonDocument render(Bson filter) {
        return filter.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    private static BsonDocument flatten(BsonDocument filter) {
        BsonDocument result = new BsonDocument();
        for (String field : filter.keySet()) {
            if (field.equals("$and")) {
                for (var child : filter.getArray(field)) result.putAll(flatten(child.asDocument()));
            } else result.put(field, filter.get(field));
        }
        return result;
    }
}
