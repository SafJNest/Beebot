package com.safjnest.nosql;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.bson.Document;
import org.junit.Test;

public class MongoDBTrackingTest {

    @Test
    public void explicitTrackedMarkerCompletesTheMatchForEveryReference() {
        Document document = new Document("tracked", true)
                .append("participants", List.of(new Document("puuid", "puuid-1")));

        assertTrue(MongoDB.isMatchTrackedDocument(document));
    }

    @Test
    public void participantSnapshotDoesNotCompleteTheMatch() {
        Document document = new Document("participants", List.of(
                new Document("puuid", "puuid-1").append("rank", "GOLD_II"),
                new Document("puuid", "puuid-2")
        ));

        assertFalse(MongoDB.isMatchTrackedDocument(document));
    }

    @Test
    public void rawMatchIsNotTracked() {
        Document document = new Document("tracked", false)
                .append("participants", List.of(new Document("puuid", "puuid-1")));

        assertFalse(MongoDB.isMatchTrackedDocument(document));
    }
}
