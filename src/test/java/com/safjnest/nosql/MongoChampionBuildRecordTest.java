package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Method;
import java.util.List;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.sql.QueryRecord;
import com.safjnest.lol.model.Filter;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import org.json.JSONArray;
import org.json.JSONObject;

public class MongoChampionBuildRecordTest {

    @Test
    public void buildRecordPreservesParticipantRunes() throws Exception {
        JSONObject timeline = new JSONObject().put("participants", new JSONObject().put("1", "player"))
            .put("item_events", new JSONArray().put(new JSONObject().put("timestamp", 120000)
                .put("event", "ITEM_PURCHASED").put("participant", 1).put("item", 3078)));
        Document match = new Document("_id", "EUW1_1");
        Document participant = new Document("puuid", "player").append("win", true)
            .append("starterItems", List.of(1055))
            .append("boots", 3006)
            .append("supportItem", 0)
            .append("roleQuestId", 3871)
            .append("item0", 3078).append("item1", 3031).append("item2", 3036)
            .append("item3", 0).append("item4", 0).append("item5", 0).append("item6", 443101)
            .append("skillOrder", List.of(1, 2, 3))
            .append("augments", List.of(1, 2, 3, 4))
            .append("summonerSpell1", 4).append("summonerSpell2", 7)
            .append("primaryRunes", List.of(8000, 8005, 9104, 8014, 8299))
            .append("secondaryRunes", List.of(8400, 8444, 8451))
            .append("statsRunes", List.of(5008, 5008, 5011));
        Method method = MongoDB.class.getDeclaredMethod("championBuildRecord", Document.class, Document.class, JSONObject.class);
        method.setAccessible(true);

        QueryRecord record = (QueryRecord) method.invoke(null, match, participant, timeline);
        JSONObject build = new JSONObject(record.get("build"));
        JSONObject buildData = build.getJSONObject("build");
        JSONObject runes = build.getJSONObject("runes");

        assertEquals("player", record.get("puuid"));
        assertFalse(buildData.has("starter"));
        assertFalse(buildData.has("boots"));
        assertFalse(buildData.has("support_item"));
        assertEquals(3871, buildData.getInt("role_bound"));
        assertEquals(List.of(3078, 3031, 3036, 0, 0, 0, 443101), integers(buildData.getJSONArray("build")));
        assertEquals(List.of(443101), integers(build.getJSONArray("prismatics")));
        assertEquals(List.of(1, 2, 3, 4), integers(build.getJSONArray("augments")));
        assertEquals(List.of(4, 7), integers(build.getJSONArray("summoner_spells")));
        assertEquals(timeline.toString(), build.getJSONObject("timeline").toString());
        assertEquals(List.of(8000, 8005, 9104, 8014, 8299), integers(runes.getJSONArray("primary")));
        assertEquals(List.of(8400, 8444, 8451), integers(runes.getJSONArray("secondary")));
        assertEquals(List.of(5008, 5008, 5011), integers(runes.getJSONArray("stats")));
    }

    @Test
    public void championBuildRecordsPassesTimelineIntoTheBuildRecord() {
        JSONObject timeline = new JSONObject().put("participants", new JSONObject().put("1", "player"))
            .put("item_events", new JSONArray());
        Document participant = new Document("puuid", "player").append("champion", 1).append("win", true)
            .append("item0", 3078).append("item1", 3031).append("item2", 3036)
            .append("item3", 0).append("item4", 0).append("item5", 0)
            .append("skillOrder", List.of(1, 2, 3));
        Document match = new Document("_id", "EUW1_2").append("events", timeline.toString())
            .append("participants", List.of(participant));

        List<QueryRecord> records = MongoDB.championBuildRecords(match,
            new Filter().setChampion(1).setLane(null).setQueue(GameQueueType.CHERRY).setRank(null));

        assertEquals(1, records.size());
        JSONObject build = new JSONObject(records.get(0).get("build"));
        assertEquals(timeline.toString(), build.getJSONObject("timeline").toString());
    }

    @Test
    public void championBuildRecordsSkipsMatchesWithoutTimeline() {
        Document participant = new Document("puuid", "player").append("champion", 1).append("win", true);
        Document match = new Document("_id", "EUW1_3").append("participants", List.of(participant));

        assertEquals(0, MongoDB.championBuildRecords(match,
            new Filter().setLane(null).setQueue(GameQueueType.CHERRY).setRank(null)).size());
    }

    @Test
    public void bothBuildSourcesProjectEveryBuildInput() throws Exception {
        Method buildOnlyProjection = MongoDB.class.getDeclaredMethod("championBuildOnlyProjection");
        Method matrixProjection = MongoDB.class.getDeclaredMethod("championRawWithBuildProjection");
        buildOnlyProjection.setAccessible(true);
        matrixProjection.setAccessible(true);

        Document buildOnly = (Document) buildOnlyProjection.invoke(null);
        Document matrix = (Document) matrixProjection.invoke(null);
        List<String> fields = List.of(
            "participants.puuid", "participants.champion", "participants.lane", "participants.win",
            "participants.roleQuestId",
            "participants.item0", "participants.item1", "participants.item2", "participants.item3",
            "participants.item4", "participants.item5", "participants.item6", "participants.skillOrder",
            "participants.augments", "participants.summonerSpell1", "participants.summonerSpell2",
            "participants.primaryRunes", "participants.secondaryRunes", "participants.statsRunes");

        assertEquals(Integer.valueOf(1), buildOnly.getInteger("_id"));
        for (String field : fields) {
            assertEquals("build-only projection missing " + field, Integer.valueOf(1), buildOnly.get(field));
            assertEquals("stats-matrix projection missing " + field, Integer.valueOf(1), matrix.get(field));
        }
    }

    private static List<Integer> integers(JSONArray values) {
        java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
        for (int index = 0; index < values.length(); index++) result.add(values.getInt(index));
        return result;
    }
}
