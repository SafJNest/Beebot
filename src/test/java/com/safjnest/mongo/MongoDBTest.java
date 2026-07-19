package com.safjnest.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class MongoDBTest {

    @Test
    public void matchDocumentsUseNamedBansAndFlatParticipants() {
        Match match = new Match();
        match.gameId = "EUW1_123";
        match.leagueShard = LeagueShard.EUW1;
        match.bans = Map.of(TeamType.BLUE, List.of(157), TeamType.RED, List.of(238));
        Participant participant = new Participant();
        participant.puuid = "p1";
        participant.item0 = 1055;
        participant.rank = TierDivisionType.GOLD_II;
        participant.lp = 73;
        participant.gain = 21;
        match.participants = List.of(participant);

        Document document = MongoDB.toDocument(match);
        Document bans = (Document) document.get("bans");
        Document savedParticipant = (Document) ((List<?>) document.get("participants")).get(0);

        assertEquals(List.of(157), bans.get("BLUE"));
        assertEquals(List.of(238), bans.get("RED"));
        assertEquals("EUW1", document.getString("region"));
        assertEquals("123", document.getString("game_id"));
        assertEquals("EUW1_123", document.getString("_id"));
        assertEquals(1055, savedParticipant.getInteger("item0").intValue());
        assertEquals("GOLD_II", savedParticipant.getString("rank"));
        assertEquals(73, savedParticipant.getInteger("lp").intValue());
        assertEquals(21, savedParticipant.getInteger("gain").intValue());
        assertFalse(savedParticipant.containsKey("build"));
    }

    @Test
    public void matchRoundTripPreservesEnumAndBans() {
        Match match = new Match();
        match.gameId = "EUW1_456";
        match.leagueShard = LeagueShard.EUW1;
        match.bans = Map.of(TeamType.BLUE, List.of(), TeamType.RED, List.of());

        Match decoded = MongoDB.read(new MongoRecord("lol_matches", "EUW1_456", MongoDB.toDocument(match)), Match.class);

        assertEquals(LeagueShard.EUW1, decoded.leagueShard);
        assertEquals("456", decoded.gameId);
        assertEquals(List.of(), decoded.bans.get(TeamType.BLUE));
        assertEquals(List.of(), decoded.bans.get(TeamType.RED));
    }
}
