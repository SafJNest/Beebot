package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.nosql.MongoDB;
import com.safjnest.spring.controller.LolApiParameters;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class MatchRankQueueTest {

    @Test
    public void initialSnapshotsUseTheMatchingRankForEveryQueue() throws Exception {
        try (Store store = new Store()) {
            Method ranking = MatchService.class.getDeclaredMethod("localRanking", Match.class);
            ranking.setAccessible(true);
            for (GameQueueType queue : GameQueueType.values()) {
                Match match = match(queue);
                ranking.invoke(null, match);
                RankProgress progress = match.participants.get(0).rankProgress;
                assertEquals(queue.name(), expectedRank(queue), progress.rank);
                assertEquals(queue.name(), Integer.valueOf(expectedLp(queue)), progress.lp);
                assertNull(progress.gain);
                assertNull(progress.previousRank);
            }
        }
    }

    @Test
    public void mixedMatchPagesFillMissingSnapshotsWithTheirOwnQueueRank() throws Exception {
        try (Store store = new Store()) {
            List<Match> matches = List.of(
                match(GameQueueType.RANKED_FLEX_SR), match(GameQueueType.RANKED_PREMADE_5X5),
                match(GameQueueType.ARAM), match(GameQueueType.TEAM_BUILDER_RANKED_SOLO));

            MatchService.upsertRankProgress(matches, LeagueShard.EUW1);

            assertEquals(3, store.summonerQueries);
            assertEquals(4, store.updates.size());
            for (Match match : matches) {
                Document progress = store.updates.get(match.gameId);
                assertEquals(expectedRank(match.queue).name(), progress.getString("rank"));
                assertEquals(Integer.valueOf(expectedLp(match.queue)), progress.getInteger("lp"));
            }
        }
    }

    @Test
    public void missingSelectedRankDoesNotFallBackToSolo() throws Exception {
        try (Store store = new Store()) {
            store.ranks.remove(GameQueueType.RANKED_FLEX_SR.name());
            store.ranks.remove(GameQueueType.RANKED_PREMADE_5X5.name());
            MatchService.upsertRankProgress(List.of(match(GameQueueType.RANKED_FLEX_SR),
                match(GameQueueType.RANKED_PREMADE_5X5)), LeagueShard.EUW1);

            assertEquals(2, store.updates.size());
            for (Document progress : store.updates.values()) {
                assertEquals("UNRANKED", progress.getString("rank"));
                assertEquals(Integer.valueOf(0), progress.getInteger("lp"));
            }
        }
    }

    @Test
    public void unknownRanksLeaveSnapshotsUnsetWithoutRequestingRefresh() throws Exception {
        for (String state : List.of("absent", "null", "missing-summoner")) {
            try (Store store = new Store()) {
                switch (state) {
                    case "absent" -> store.summoner.remove("ranks");
                    case "null" -> store.summoner.put("ranks", null);
                    case "missing-summoner" -> store.summonerExists = false;
                }
                List<Match> matches = List.of(match(GameQueueType.RANKED_FLEX_SR),
                    match(GameQueueType.RANKED_PREMADE_5X5), match(GameQueueType.ARAM));

                MatchService.upsertRankProgress(matches, LeagueShard.EUW1);

                assertEquals(state, 3, store.summonerQueries);
                assertTrue(state, store.updates.isEmpty());
                for (Match match : matches) assertNull(state, match.participants.get(0).rankProgress);
            }
        }
    }

    @Test
    public void seasonalHistoryReadsPremadeAlongsideSoloAndFlex() throws Exception {
        try (Store store = new Store()) {
            MongoDB.findRankHistoryMatches("player", LeagueShard.EUW1, 1000, 2000);

            String filter = store.historyFilter.toJson();
            assertTrue(filter.contains("RANKED_PREMADE_5X5"));
            assertTrue(filter.contains("RANKED_FLEX_SR"));
            assertTrue(filter.contains("RANKED_SOLO_5X5"));
            assertTrue(filter.contains("TEAM_BUILDER_RANKED_SOLO"));
        }
    }

    @Test
    public void premadePassesBothHttpAndServiceValidation() {
        var query = LolApiParameters.rankHistoryQuery(
            "RANKED_PREMADE_5X5", null, 2025, null, Long.MAX_VALUE, 0, null);

        var history = MatchService.getRankHistory("player", LeagueShard.EUW1, query);

        assertNotNull(history.metadata());
        assertEquals(GameQueueType.RANKED_PREMADE_5X5, history.metadata().filter().queue());
    }

    private static TierDivisionType expectedRank(GameQueueType queue) {
        return switch (queue) {
            case RANKED_FLEX_SR -> TierDivisionType.PLATINUM_III;
            case RANKED_PREMADE_5X5 -> TierDivisionType.DIAMOND_IV;
            default -> TierDivisionType.GOLD_II;
        };
    }

    private static int expectedLp(GameQueueType queue) {
        return switch (queue) {
            case RANKED_FLEX_SR -> 22;
            case RANKED_PREMADE_5X5 -> 33;
            default -> 11;
        };
    }

    private static Match match(GameQueueType queue) {
        Match match = new Match();
        match.gameId = "EUW1_" + queue.name();
        match.leagueShard = LeagueShard.EUW1;
        match.queue = queue;
        Participant participant = new Participant();
        participant.puuid = "player";
        match.participants = List.of(participant);
        return match;
    }

    private static final class Store implements AutoCloseable {
        private final Field databaseField;
        private final Field readyField;
        private final Object originalDatabase;
        private final boolean originalReady;
        private final Document ranks = new Document();
        private final Document summoner;
        private final Map<String, Document> updates = new LinkedHashMap<>();
        private boolean summonerExists = true;
        private int summonerQueries;
        private Document historyFilter;

        private Store() throws Exception {
            for (GameQueueType queue : List.of(GameQueueType.RANKED_SOLO_5X5,
                    GameQueueType.RANKED_FLEX_SR, GameQueueType.RANKED_PREMADE_5X5)) {
                ranks.put(queue.name(), new Document("rank", expectedRank(queue).name()).append("lp", expectedLp(queue)));
            }
            summoner = new Document("_id", "player").append("region", "EUW1").append("ranks", ranks);
            databaseField = MongoDB.class.getDeclaredField("database");
            readyField = MongoDB.class.getDeclaredField("collectionsReady");
            databaseField.setAccessible(true);
            readyField.setAccessible(true);
            originalDatabase = databaseField.get(null);
            originalReady = readyField.getBoolean(null);
            databaseField.set(null, proxy(MongoDatabase.class, (database, method, args) -> {
                if (!method.getName().equals("getCollection")) throw new UnsupportedOperationException(method.getName());
                String collection = (String) args[0];
                return proxy(MongoCollection.class, (object, operation, values) -> switch (operation.getName()) {
                    case "find" -> {
                        List<Document> rows = new ArrayList<>();
                        if (collection.equals("summoner")) {
                            summonerQueries++;
                            if (summonerExists) rows.add(summoner);
                        } else {
                            assertEquals("match", collection);
                            historyFilter = bson((Bson) values[0]);
                        }
                        yield iterable(rows);
                    }
                    case "updateOne" -> {
                        assertEquals("match", collection);
                        Document filter = bson((Bson) values[0]);
                        List<Document> clauses = filter.getList("$and", Document.class);
                        assertEquals(new Document("$ne", true), clauses.get(1).get("tracked"));
                        assertTrue(filter.toJson().contains("$exists"));
                        String gameId = clauses.get(0).getString("_id");
                        Document update = bson((Bson) values[1]);
                        updates.put(gameId, update.get("$set", Document.class).get("participants.$.rankProgress", Document.class));
                        yield UpdateResult.acknowledged(0, 0L, null);
                    }
                    default -> throw new UnsupportedOperationException(operation.getName());
                });
            }));
            readyField.setBoolean(null, true);
        }

        private static FindIterable<Document> iterable(List<Document> rows) {
            return proxy(FindIterable.class, (object, method, args) -> switch (method.getName()) {
                case "projection", "limit", "sort" -> object;
                case "first" -> rows.isEmpty() ? null : rows.get(0);
                case "iterator" -> {
                    var iterator = rows.iterator();
                    yield proxy(MongoCursor.class, (cursor, operation, values) -> switch (operation.getName()) {
                        case "hasNext" -> iterator.hasNext();
                        case "next" -> iterator.next();
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(operation.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static Document bson(Bson value) {
            return Document.parse(value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson());
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
        }

        @Override
        public void close() throws Exception {
            databaseField.set(null, originalDatabase);
            readyField.setBoolean(null, originalReady);
        }
    }
}
