package com.safjnest.sql;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.DriverManager;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;

import com.safjnest.utils.JsonCodec;

public class QueryRecordParserTest {

    @Test
    public void parsesMariaDbRowsWithTheExistingFlatContract() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:query_record;DB_CLOSE_DELAY=-1");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select 42 as id, true as active")) {
            List<QueryRecord> result = QueryRecordParser.fromRows(rows);

            assertEquals(1, result.size());
            assertEquals("42", result.get(0).get("id"));
            assertEquals(42, result.get(0).getAsInt("id"));
            assertTrue(result.get(0).getAsBoolean("active"));
        }
    }

    @Test
    public void parsesMongoDocumentsIntoDetachedNestedQueryRecords() {
        byte[] bytes = new byte[] {1, 2, 3};
        Document nested = new Document("value", 7);
        Document source = new Document("_id", new ObjectId("507f1f77bcf86cd799439011"))
                .append("enabled", true)
                .append("createdAt", new Date(1_700_000_000_000L))
                .append("bytes", bytes)
                .append("nested", nested)
                .append("ranks", List.of(
                        new Document("queue", "RANKED_SOLO_5x5").append("lp", 100),
                        new Document("queue", "RANKED_FLEX_SR").append("lp", 20)));

        QueryRecord record = QueryRecordParser.fromDocument(source);
        nested.put("value", 99);
        bytes[0] = 9;

        assertEquals("507f1f77bcf86cd799439011", record.get("_id"));
        assertTrue(record.getAsBoolean("enabled"));
        assertNotNull(record.getAsInstant("createdAt"));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) record.getValue("bytes"));
        assertEquals(7, record.getAsRecord("nested").getAsInt("value"));
        assertEquals(2, record.getAsRecords("ranks").size());
        assertEquals(100, record.getAsRecords("ranks").get(0).getAsInt("lp"));
        assertFalse(containsDocument(record));
    }

    @Test
    public void nestedQueryRecordsSurviveJsonRoundTrip() {
        QueryRecord source = QueryRecordParser.fromMap(Map.of(
                "puuid", "p1",
                "ranks", List.of(Map.of("queue", "RANKED_SOLO_5x5", "lp", 75))));

        QueryRecord decoded = JsonCodec.fromJson(JsonCodec.toJson(source), QueryRecord.class);

        assertEquals("p1", decoded.get("puuid"));
        assertEquals(1, decoded.getAsRecords("ranks").size());
        assertEquals(75, decoded.getAsRecords("ranks").get(0).getAsInt("lp"));
    }

    private static boolean containsDocument(Object value) {
        if (value instanceof Document) return true;
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) if (containsDocument(nested)) return true;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) if (containsDocument(nested)) return true;
        }
        return false;
    }
}
