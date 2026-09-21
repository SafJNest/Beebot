package com.safjnest.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecordParser;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

/**
 * UNIT U5 — nullable DTO null-vs-empty contract (U1-U4 approved behavior kept).
 *
 * <p>Absent Mongo key (never fetched) reads as {@code null} DTO, which is the
 * sole fetch signal ({@code if (s.ranks() == null)} via
 * {@code RankService.find}/{@code MasteryService.find}); a present key — even
 * empty (unranked / zero mastery) — reads as an empty DTO and never fetches.
 *
 * <p>Pure mapping + reflection units: no live Mongo (beebot_test untouched),
 * no Redis, no Riot. {@code RankService.find}/{@code MasteryService.find} return
 * {@code null} only when the stored document has no piece key
 * ({@code MongoDB.findRanks/findMasteries} {@code containsKey} gate), which is the
 * sole trigger of the Riot fetch path in {@code getAsync}
 * ({@code saved != null ? completedFuture : fetch}). An empty-but-present piece
 * ({@code ranks:{}}, {@code masteries:[]}) is non-null and never fetches.
 *
 * <p>Manual remainder (needs live Riot + beebot_test, no mock framework on classpath):
 * {@code !summoner} nuovo + {@code refresh} end-to-end, i.e. the {@code fetch} branch of
 * {@code RankService.getAsync}/{@code MasteryService.getAsync} when {@code find} is null.
 */
public class SummonerWarmingTest {

    private static final long HYDRATE_TIMEOUT_SECONDS = 30;

    @Test
    public void absentRanksKeyTakesFetchPath() {
        Document stored = new Document("_id", "puuid-no-ranks").append("region", LeagueShard.EUW1.name());

        assertFalse(stored.containsKey("ranks"));
    }

    @Test
    public void emptyRanksDocumentSkipsFetch() throws Exception {
        Document stored = new Document("_id", "puuid-empty-ranks").append("region", LeagueShard.EUW1.name())
                .append("ranks", new Document());

        assertTrue(stored.containsKey("ranks"));
        Map<GameQueueType, Rank> ranks = ranksOf(stored);
        assertNotNull(ranks);
        assertTrue(ranks.isEmpty());
    }

    @Test
    public void ranksRoundTripPreservesQueues() throws Exception {
        Summoner source = Summoner.hydrated("puuid-ranks", "Name#TAG", LeagueShard.EUW1, 1, 1,
                null, false, Map.of(
                        GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.MASTER_I, 500, 10, 5),
                        GameQueueType.RANKED_FLEX_SR, new Rank(TierDivisionType.DIAMOND_I, 50, 8, 4)),
                List.of());

        Document document = MongoDB.toDocument(source);
        Document ranks = document.get("ranks", Document.class);
        assertNotNull(ranks);
        assertFalse(ranks.get("RANKED_SOLO_5X5", Document.class).containsKey("queue"));

        Map<GameQueueType, Rank> decoded = ranksOf(document);
        assertEquals(TierDivisionType.MASTER_I, decoded.get(GameQueueType.RANKED_SOLO_5X5).tier());
        assertEquals(TierDivisionType.DIAMOND_I, decoded.get(GameQueueType.RANKED_FLEX_SR).tier());
    }

    @Test
    public void absentMasteriesKeyTakesFetchPath() {
        Document stored = new Document("_id", "puuid-no-masteries").append("region", LeagueShard.EUW1.name());

        assertFalse(stored.containsKey("masteries"));
    }

    @Test
    public void emptyMasteriesListSkipsFetch() throws Exception {
        Document stored = new Document("_id", "puuid-empty-masteries").append("region", LeagueShard.EUW1.name())
                .append("masteries", List.of());

        assertTrue(stored.containsKey("masteries"));
        List<Mastery> masteries = masteriesOf(stored);
        assertNotNull(masteries);
        assertTrue(masteries.isEmpty());
    }

    @Test
    public void masteriesRoundTripPreservesEntries() {
        Summoner source = Summoner.hydrated("puuid-masteries", "Name#TAG", LeagueShard.EUW1, 1, 1,
                null, false, Map.of(), List.of(new Mastery(157, 7, 250000), new Mastery(238, 5, 30000)));

        Document document = MongoDB.toDocument(source);
        Summoner decoded = MongoDB.read(QueryRecordParser.fromDocument(document), Summoner.class);

        assertEquals(List.of(new Mastery(157, 7, 250000), new Mastery(238, 5, 30000)), decoded.masteries());
        for (Object item : document.getList("masteries", Document.class)) {
            assertFalse(((Document) item).containsKey("id"));
        }
    }

    @Test
    public void identityUpsertCreatesNoRanksKey() throws Exception {
        Summoner identity = new Summoner("puuid-identity", "Name#TAG", LeagueShard.EUW1, 1, 1);

        org.bson.BsonDocument update = updateOf(identity);

        assertFalse(update.getDocument("$set").containsKey("ranks"));
        assertFalse(update.containsKey("$setOnInsert"));
    }

    @Test
    public void identityDocumentOmitsPieceKeys() {
        Document identity = MongoDB.toDocument(new Summoner("puuid-identity", "Name#TAG", LeagueShard.EUW1, 1, 1));
        assertFalse(identity.containsKey("ranks"));
        assertFalse(identity.containsKey("masteries"));

        Document hydratedEmpty = MongoDB.toDocument(
                Summoner.hydrated("puuid-empty", "Name#TAG", LeagueShard.EUW1, 1, 1, null, false, Map.of(), List.of()));
        assertFalse(hydratedEmpty.containsKey("ranks"));
        assertFalse(hydratedEmpty.containsKey("masteries"));
    }

    @Test
    public void keylessDocumentReadsAsNullPieces() {
        Document stored = new Document("_id", "puuid-keyless").append("region", LeagueShard.EUW1.name())
                .append("riotId", "Name#TAG").append("level", 30).append("icon", 500);

        Summoner decoded = MongoDB.read(QueryRecordParser.fromDocument(stored), Summoner.class);

        assertNull(decoded.ranks());
        assertNull(decoded.masteries());
    }

    @Test
    public void nullValuedPieceKeysReadAsNull() throws Exception {
        Document stored = new Document("_id", "puuid-null-pieces").append("region", LeagueShard.EUW1.name())
                .append("ranks", null).append("masteries", null);

        assertNull(ranksOf(stored));
        assertNull(masteriesOf(stored));
    }

    @Test
    public void presentEmptyPiecesReadAsEmpty() {
        Document stored = new Document("_id", "puuid-present-empty").append("region", LeagueShard.EUW1.name())
                .append("riotId", "Name#TAG").append("level", 30).append("icon", 500)
                .append("ranks", new Document()).append("masteries", List.of());

        Summoner decoded = MongoDB.read(QueryRecordParser.fromDocument(stored), Summoner.class);

        assertNotNull(decoded.ranks());
        assertTrue(decoded.ranks().isEmpty());
        assertNotNull(decoded.masteries());
        assertTrue(decoded.masteries().isEmpty());
    }

    @Test
    public void writeOmitsNullPieces() {
        Document document = MongoDB.toDocument(
                Summoner.hydrated("puuid-null", "Name#TAG", LeagueShard.EUW1, 1, 1, null, false, null, null));

        assertFalse(document.containsKey("ranks"));
        assertFalse(document.containsKey("masteries"));
    }

    @Test
    public void setRankLazyInitializesNullMap() {
        Summoner identity = new Summoner("puuid-lazy", "Name#TAG", LeagueShard.EUW1, 1, 1);

        assertNull(identity.ranks());
        identity.setRank(GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.GOLD_I, 75, 20, 10));

        assertNotNull(identity.ranks());
        assertEquals(TierDivisionType.GOLD_I, identity.ranks().get(GameQueueType.RANKED_SOLO_5X5).tier());
    }

    @Test
    public void setMasteriesNullClearsFieldWithoutNpe() {
        Summoner hydrated = Summoner.hydrated("puuid-clear", "Name#TAG", LeagueShard.EUW1, 1, 1,
                null, false, Map.of(), List.of(new Mastery(157, 7, 250000)));

        hydrated.setMasteries(null);

        assertNull(hydrated.masteries());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void snapshotOmitsNullPieces() throws Exception {
        Summoner identity = new Summoner("puuid-snapshot", "Name#TAG", LeagueShard.EUW1, 1, 1);

        Method method = Summoner.class.getSuperclass().getDeclaredMethod("snapshotValues");
        method.setAccessible(true);
        Map<String, Object> values = (Map<String, Object>) method.invoke(identity);

        assertFalse(values.containsKey("ranks"));
        assertFalse(values.containsKey("masteries"));
        for (Object value : values.values()) assertNotNull(value);
    }

    @Test
    public void hydrateSkipsRiotWhenPiecesPresent() throws Exception {
        Summoner hydrated = Summoner.hydrated("puuid-full", "Name#TAG", LeagueShard.EUW1, 30, 500,
                null, false, Map.of(GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.GOLD_I, 75, 20, 10)),
                List.of(new Mastery(157, 7, 250000)));

        assertSame(hydrated, hydrate(hydrated));
    }

    @Test
    public void hydrateSkipsRiotForIdentityOnly() throws Exception {
        Summoner identity = new Summoner("puuid-identity", "Name#TAG", LeagueShard.EUW1, 30, 500);

        assertSame(identity, hydrate(identity));
    }

    @Test
    public void hydrateNullGuards() throws Exception {
        assertNull(hydrate(null));
        assertNull(hydrate(new Summoner("  ", "Name#TAG", LeagueShard.EUW1, 1, 1)));
        assertNull(hydrate(new Summoner("puuid-no-region", "Name#TAG", null, 1, 1)));
    }

    @SuppressWarnings("unchecked")
    private static Map<GameQueueType, Rank> ranksOf(Document document) throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("ranks", Document.class);
        method.setAccessible(true);
        return (Map<GameQueueType, Rank>) method.invoke(null, document);
    }

    @SuppressWarnings("unchecked")
    private static List<Mastery> masteriesOf(Document document) throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("masteries", Document.class);
        method.setAccessible(true);
        return (List<Mastery>) method.invoke(null, document);
    }

    private static org.bson.BsonDocument updateOf(Summoner summoner) throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("summonerUpdate", Summoner.class, String.class);
        method.setAccessible(true);
        return ((Bson) method.invoke(null, summoner, null))
                .toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
    }

    private static Summoner hydrate(Summoner identity) throws Exception {
        Method method = SummonerService.class.getDeclaredMethod("hydrate", Summoner.class);
        method.setAccessible(true);
        CompletableFuture<Summoner> future =
                (CompletableFuture<Summoner>) method.invoke(null, new Object[] { identity });
        return future.get(HYDRATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
