package com.safjnest.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.bson.Document;
import org.junit.Test;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class MongoRecordTest {

    @Test
    public void missingValuesUseCompatibleDefaults() {
        MongoRecord record = new MongoRecord("lol_summoners", "p1", new Document("enabled", true));

        assertEquals(null, record.getAsString("missing"));
        assertEquals(0, record.getAsInt("missing"));
        assertEquals(0L, record.getAsLong("missing"));
        assertFalse(record.getAsBoolean("missing"));
        assertEquals(List.of(), record.getAsRecords("missing"));
    }

    @Test
    public void invalidTypesIncludeMongoContext() {
        MongoRecord record = new MongoRecord("lol_matches", "EUW1_1", new Document("queue", 123));

        MongoRecord.ConversionException exception = assertThrows(
                MongoRecord.ConversionException.class,
                () -> record.getAsEnum("queue", GameQueueType.class));

        assertEquals("lol_matches", exception.getCollection());
        assertEquals("EUW1_1", exception.getId());
        assertEquals("queue", exception.getField());
    }

    @Test
    public void toDocumentIsDefensive() {
        Document source = new Document("nested", new Document("value", 1));
        MongoRecord record = new MongoRecord(source);
        Document copy = record.toDocument();
        ((Document) copy.get("nested")).put("value", 2);

        assertEquals(1, ((Document) record.toDocument().get("nested")).getInteger("value").intValue());
    }
}
