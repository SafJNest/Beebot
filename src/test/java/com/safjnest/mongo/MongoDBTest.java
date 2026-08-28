package com.safjnest.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;

import com.mongodb.client.model.WriteModel;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecordParser;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class MongoDBTest {

    @Test
    public void matchDocumentsUseNamedBansAndFlatParticipants() {
        Match match = new Match();
        match.gameId = "EUW1_123";
        match.leagueShard = LeagueShard.EUW1;
        match.patch = "14.2.1";
        match.bans = Map.of(TeamType.BLUE, List.of(157), TeamType.RED, List.of(238));
        Participant participant = new Participant();
        participant.puuid = "p1";
        participant.item0 = 1055;
        participant.rankProgress = new RankProgress(TierDivisionType.GOLD_II, 73, 21, null, null);
        match.participants = List.of(participant);

        Document document = MongoDB.toDocument(match);
        Document bans = (Document) document.get("bans");
        Document savedParticipant = (Document) ((List<?>) document.get("participants")).get(0);

        assertEquals(List.of(157), bans.get("BLUE"));
        assertEquals(List.of(238), bans.get("RED"));
        assertEquals("EUW1", document.getString("region"));
        assertEquals("EUW1_123", document.getString("_id"));
        assertEquals("14.2", document.getString("patchMajor"));
        assertFalse(document.containsKey("fullGameId"));
        assertFalse(document.containsKey("gameId"));
        assertFalse(document.containsKey("game_id"));
        assertFalse(document.containsKey("leagueShard"));
        assertFalse(document.containsKey("legacyMatchId"));
        assertEquals(1055, savedParticipant.getInteger("item0").intValue());
        Document progress = savedParticipant.get("rankProgress", Document.class);
        assertEquals("GOLD_II", progress.getString("rank"));
        assertEquals(73, progress.getInteger("lp").intValue());
        assertEquals(21, progress.getInteger("gain").intValue());
        assertFalse(savedParticipant.containsKey("rank"));
        assertFalse(savedParticipant.containsKey("lp"));
        assertFalse(savedParticipant.containsKey("gain"));
        assertFalse(savedParticipant.containsKey("build"));
        assertFalse(savedParticipant.containsKey("id"));
        assertFalse(savedParticipant.containsKey("summonerId"));
        assertFalse(savedParticipant.containsKey("matchId"));
        assertFalse(savedParticipant.containsKey("legacyParticipantId"));
    }

    @Test
    public void ordinalBansBecomeBlueAndRed() {
        Match match = new Match();
        match.gameId = "EUW1_789";
        match.leagueShard = LeagueShard.EUW1;
        match.bans = new LinkedHashMap<>();
        match.bans.put(TeamType.SUBTEAM, List.of());
        match.bans.put(TeamType.BLUE, List.of(555, 420));

        Document document = MongoDB.toDocument(match);
        Document bans = (Document) document.get("bans");

        assertEquals(List.of(), bans.get("BLUE"));
        assertEquals(List.of(555, 420), bans.get("RED"));
    }

    @Test
    public void matchRoundTripPreservesEnumAndBans() {
        Match match = new Match();
        match.gameId = "EUW1_456";
        match.leagueShard = LeagueShard.EUW1;
        match.bans = Map.of(TeamType.BLUE, List.of(), TeamType.RED, List.of());

        Match decoded = MongoDB.read(QueryRecordParser.fromDocument(MongoDB.toDocument(match)), Match.class);

        assertEquals(LeagueShard.EUW1, decoded.leagueShard);
        assertEquals("EUW1_456", decoded.gameId);
        assertEquals(List.of(), decoded.bans.get(TeamType.BLUE));
        assertEquals(List.of(), decoded.bans.get(TeamType.RED));
    }

    @Test
    public void summonerUsesPuuidAsIdentityAndOmitsLegacyFields() {
        Summoner summoner = new Summoner("puuid-42", "Name#TAG", no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1, 500, 1234);

        Document document = MongoDB.toDocument(summoner);

        assertEquals("puuid-42", document.getString("_id"));
        assertFalse(document.containsKey("legacySummonerId"));
        assertFalse(document.containsKey("tracking"));
        assertFalse(document.containsKey("userId"));

        Summoner decoded = MongoDB.read(QueryRecordParser.fromDocument(document), Summoner.class);
        assertEquals("puuid-42", decoded.puuid());
        assertEquals(LeagueShard.EUW1, decoded.region());
    }

    @Test
    public void embeddedRankAndMasteryDoNotPersistMariaDbIds() {
        Document rank = MongoDB.toDocument(new Rank(null, 0, 0, 0));
        Document mastery = MongoDB.toDocument(new Mastery(157, 30, 250000));

        assertFalse(rank.containsKey("id"));
        assertFalse(rank.containsKey("legacyRankId"));
        assertFalse(mastery.containsKey("id"));
        assertFalse(mastery.containsKey("legacyMasteryId"));
    }

    @Test
    public void summonerRanksSerializeAsCanonicalQueueObjectAndReadLegacyArrays() {
        Summoner source = Summoner.hydrated("puuid-ranks", "Name#TAG", LeagueShard.EUW1, 1, 1,
            null, false, Map.of(
                GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.MASTER_I, 500, 10, 5),
                GameQueueType.RANKED_FLEX_SR, new Rank(TierDivisionType.DIAMOND_I, 50, 8, 4)
            ), List.of());

        Document document = MongoDB.toDocument(source);
        Document ranks = document.get("ranks", Document.class);
        assertEquals(TierDivisionType.MASTER_I.name(), ranks.get("RANKED_SOLO_5X5", Document.class).getString("rank"));
        assertFalse(ranks.get("RANKED_SOLO_5X5", Document.class).containsKey("queue"));
        assertEquals(TierDivisionType.DIAMOND_I.name(), ranks.get("RANKED_FLEX_SR", Document.class).getString("rank"));

        Document legacy = new Document("_id", "puuid-legacy").append("region", LeagueShard.EUW1.name())
            .append("ranks", List.of(new Document("queue", "RANKED_SOLO_5X5").append("rank", "MASTER_I").append("lp", 500)));
        Summoner decoded = MongoDB.read(QueryRecordParser.fromDocument(legacy), Summoner.class);
        assertEquals(TierDivisionType.MASTER_I, decoded.ranks().get(GameQueueType.RANKED_SOLO_5X5).tier());
    }

    @Test
    public void participantRoundTripPreservesPingsAndSpellCasts() {
        Participant participant = new Participant();
        participant.puuid = "puuid-1";
        participant.pings.put("push", 4);
        participant.pings.put("vision_cleared", 2);
        participant.q = 11;
        participant.w = 12;
        participant.e = 13;
        participant.r = 14;
        participant.d = 3;
        participant.f = 4;

        Participant decoded = MongoDB.read(QueryRecordParser.fromDocument(MongoDB.toDocument(participant)), Participant.class);

        assertEquals(participant.pings, decoded.pings);
        assertEquals(11, decoded.q);
        assertEquals(12, decoded.w);
        assertEquals(13, decoded.e);
        assertEquals(14, decoded.r);
        assertEquals(3, decoded.d);
        assertEquals(4, decoded.f);
    }

    @Test
    public void matchRoundTripPreservesParticipantRankLpAndGain() {
        Match match = new Match();
        match.gameId = "EUW1_1000";
        match.leagueShard = LeagueShard.EUW1;

        Participant participant = new Participant();
        participant.puuid = "puuid-1";
        participant.rankProgress = new RankProgress(TierDivisionType.PLATINUM_I, 63, 23, TierDivisionType.PLATINUM_II, 40);
        match.participants = List.of(participant);

        Match decoded = MongoDB.read(QueryRecordParser.fromDocument(MongoDB.toDocument(match)), Match.class);
        Participant decodedParticipant = decoded.participants.get(0);

        assertEquals(TierDivisionType.PLATINUM_I, decodedParticipant.rankProgress.rank);
        assertEquals(63, decodedParticipant.rankProgress.lp.intValue());
        assertEquals(23, decodedParticipant.rankProgress.gain.intValue());
        assertEquals(TierDivisionType.PLATINUM_II, decodedParticipant.rankProgress.previousRank);
        assertEquals(40, decodedParticipant.rankProgress.previousLp.intValue());
    }

    @Test
    public void matchDoesNotEmbedEvents() {
        Match match = new Match();
        match.gameId = "EUW1_999";
        match.leagueShard = LeagueShard.EUW1;
        match.eventData = Map.of("champion_kills", List.of(Map.of("timestamp", 1000)));

        assertFalse(MongoDB.toDocument(match).containsKey("events"));
    }

    @Test
    public void rankProgressSchemaPipelineSupportsBsonNullValues() throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("rankProgressSchemaUpdate");
        method.setAccessible(true);

        List<?> pipeline = (List<?>) method.invoke(null);

        assertEquals(1, pipeline.size());
        assertNotNull(pipeline.getFirst());
    }

    @Test
    public void rankProgressHistoryMigrationRepairsLegacyUnrankedPlacementGain() throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("rankProgressHistoryUpdate",
                Document.class, String.class, RankProgress.class, RankProgress.class);
        method.setAccessible(true);

        Document rawMatch = new Document("_id", "EUW1_1").append("tracked", false);
        RankProgress placement = new RankProgress(TierDivisionType.SILVER_III, 89, 90, null, null);
        RankProgress unranked = new RankProgress(TierDivisionType.UNRANKED, 0, 0, null, null);
        WriteModel<?> repaired = (WriteModel<?>) method.invoke(null, rawMatch, "puuid", placement, unranked);

        assertNotNull(repaired);

        RankProgress mismatchedRankedGain = new RankProgress(TierDivisionType.SILVER_III, 89, 90, null, null);
        RankProgress previousRanked = new RankProgress(TierDivisionType.SILVER_III, 70, 0, null, null);
        WriteModel<?> skipped = (WriteModel<?>) method.invoke(null, rawMatch, "puuid", mismatchedRankedGain, previousRanked);

        assertNull(skipped);
    }

    @Test
    public void structuredStatisticsDoNotContainLegacyPayload() {
        Document document = MongoDB.toDocument(new ProfileStatistics());

        assertFalse(document.containsKey("legacyPayload"));
        assertFalse(document.containsKey("statistics"));
        assertFalse(document.toJson().contains("legacyPayload"));
    }

    @Test
    public void flatProfileStatisticsCanBeReadWithMongoIdentityFields() throws Exception {
        Document document = MongoDB.toDocument(new ProfileStatistics(100));
        document.put("_id", new ObjectId());
        document.put("puuid", "puuid");
        document.put("filterKey", "filter");

        Method reader = MongoDB.class.getDeclaredMethod("readProfileStatistics", Document.class);
        reader.setAccessible(true);

        ProfileStatistics decoded = (ProfileStatistics) reader.invoke(null, document);
        assertNotNull(decoded);
        assertEquals(100, decoded.timeStart);
    }
}
