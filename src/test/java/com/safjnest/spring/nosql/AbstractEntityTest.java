package com.safjnest.spring.nosql;
import com.safjnest.spring.nosql.writer.SummonerWriter;
import com.safjnest.spring.nosql.writer.MatchWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.R4B.api.dto.match.Match;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.lol.*;

class AbstractEntityTest {

    @AfterEach
    void resetExecutor() {
        NoSqlEntityExecutor.resetWriter();
    }

    @Test
    void updateFlushesOneDeltaAndClearsDirtyState() {
        List<Map<String, Object>> operations = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            writes.incrementAndGet();
            operations.addAll(changes);
            return true;
        });

        TestEntity entity = new TestEntity();
        assertTrue(entity.setNumber(20).isDirty());
        assertTrue(entity.setName("updated").update());

        assertEquals(1, writes.get());
        assertFalse(entity.isDirty());
        assertEquals("set", operations.get(0).get("type"));
        assertEquals("number", operations.get(0).get("path"));
        assertEquals("name", operations.get(1).get("path"));
    }

    @Test
    void failedFlushKeepsPendingChangesForRetry() {
        AtomicInteger writes = new AtomicInteger();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> writes.incrementAndGet() > 1);

        TestEntity entity = new TestEntity().setNumber(20);
        assertFalse(entity.update());
        assertTrue(entity.isDirty());
        assertTrue(entity.update());
        assertFalse(entity.isDirty());
        assertEquals(2, writes.get());
    }

    @Test
    void cleanEntityDoesNotExecuteAQuery() {
        AtomicInteger writes = new AtomicInteger();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            writes.incrementAndGet();
            return true;
        });

        assertTrue(new TestEntity().update());
        assertEquals(0, writes.get());
    }

    @Test
    void upsertCreatesFromSnapshotThenFlushesOnlyLaterChanges() {
        List<Map<String, Object>> operations = new ArrayList<>();
        AtomicInteger upserts = new AtomicInteger();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            if (upsert) upserts.incrementAndGet();
            operations.addAll(changes);
            return true;
        });

        TestEntity entity = new TestEntity();
        assertTrue(entity.upsert());
        assertFalse(entity.isDirty());
        assertEquals(1, operations.size());
        assertEquals("number", operations.get(0).get("path"));

        operations.clear();
        entity.setName("later");
        assertTrue(entity.update());
        assertEquals(1, operations.size());
        assertEquals("name", operations.get(0).get("path"));
        assertEquals(1, upserts.get());
    }

    @Test
    void instantModeFlushesEachSetterAndDeferredModeStopsIt() {
        AtomicInteger writes = new AtomicInteger();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            writes.incrementAndGet();
            return true;
        });

        TestEntity entity = new TestEntity().instant();
        entity.setNumber(20);
        assertEquals(1, writes.get());
        assertFalse(entity.isDirty());

        entity.deferred().setNumber(21);
        assertEquals(1, writes.get());
        assertTrue(entity.isDirty());
    }

    @Test
    void nestedArrayOperationsRemainTypedAtEntityBoundary() {
        List<Map<String, Object>> operations = new ArrayList<>();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            operations.addAll(changes);
            return true;
        });

        Summoner summoner = new Summoner("puuid", "name#tag", com.R4B.lol.LeagueShard.EUW1, 10, 1);
        SummonerWriter summonerWriter = new SummonerWriter(summoner);
        summonerWriter.setRank(GameQueueType.RANKED_FLEX_SR,
                new Rank(TierDivisionType.DIAMOND_IV, 50, 10, 5));

        assertTrue(summonerWriter.update());
        assertEquals("set", operations.get(0).get("type"));
        assertEquals("ranks.RANKED_FLEX_SR", operations.get(0).get("path"));
    }

    @Test
    void matchParticipantUpdatesUsePuuidAsArrayKey() {
        List<Map<String, Object>> operations = new ArrayList<>();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            operations.addAll(changes);
            return true;
        });

        Participant participant = new Participant();
        participant.puuid = "participant-puuid";
        Match match = Match.hydrated();
        match.gameId = "EUW1_123";
        match.leagueShard = LeagueShard.EUW1;
        MatchWriter matchWriter = MatchWriter.existing(match);
        matchWriter.setParticipant(participant).setParticipantField(participant.puuid, "champion", 42);

        assertTrue(matchWriter.update());
        assertEquals("replaceOrAppendArrayElement", operations.get(0).get("type"));
        assertEquals("participants", operations.get(0).get("path"));
        assertEquals("puuid", operations.get(0).get("keyField"));
        assertEquals("setArrayElementField", operations.get(1).get("type"));
    }

    @Test
    void matchUpsertSnapshotUsesCanonicalMongoFields() {
        List<Map<String, Object>> operations = new ArrayList<>();
        NoSqlEntityExecutor.installWriterForTests((collection, id, changes, filters, upsert) -> {
            operations.addAll(changes);
            return true;
        });

        Match match = new Match();
        match.gameId = "EUW1_123";
        match.leagueShard = LeagueShard.EUW1;
        match.patch = "14.2.1";

        assertTrue(new MatchWriter(match).upsert());
        List<String> paths = new ArrayList<>();
        Object patchMajor = null;
        for (Map<String, Object> operation : operations) {
            paths.add((String) operation.get("path"));
            if ("patchMajor".equals(operation.get("path"))) patchMajor = operation.get("value");
        }
        assertFalse(paths.contains("fullGameId"));
        assertFalse(paths.contains("gameId"));
        assertFalse(paths.contains("game_id"));
        assertFalse(paths.contains("leagueShard"));
        assertEquals("14.2", patchMajor);
    }

    private static final class TestEntity extends AbstractEntity<TestEntity> {
        private int number;
        private String name;

        private TestEntity() {
        }

        private TestEntity setNumber(int number) {
            this.number = number;
            setValue("number", number);
            return this;
        }

        private TestEntity setName(String name) {
            this.name = name;
            setValue("name", name);
            return this;
        }

        @Override
        protected String collectionName() {
            return "test";
        }

        @Override
        protected String entityId() {
            return "entity";
        }

        @Override
        protected Map<String, Object> snapshotValues() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("number", number);
            if (name != null) values.put("name", name);
            return values;
        }
    }
}
