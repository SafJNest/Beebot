package com.safjnest.lol.arena;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ArenaGameParserTest {

    public static final ArenaItemCatalog CATALOG = new ArenaItemCatalog(Map.of(
        3006, Kind.BOOTS, 4001, Kind.ITEM, 4002, Kind.ITEM, 4003, Kind.ITEM));

    @Test
    public void timelineSelectsFirstPrismaticAndBuildsLegendaryOrderWithoutAnvil() {
        Participant participant = participant(1, 3);
        participant.boots = 3006;
        var match = match("EUW1_1", participant, List.of(
            event("ITEM_PURCHASED", 1000, 3006),
            event("ITEM_PURCHASED", 2000, 447001),
            event("ITEM_PURCHASED", 2500, 220007),
            event("ITEM_PURCHASED", 3000, 4002),
            event("ITEM_PURCHASED", 4000, 447002),
            event("ITEM_PURCHASED", 5000, 4001)));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG);

        assertEquals(Integer.valueOf(447001), parsed.firstPrismaticId());
        assertFalse(parsed.firstPrismaticFallback());
        assertEquals(List.of(447001, 447002), parsed.prismatics());
        assertEquals(List.of(4002, 4001), parsed.legendaryItems());
        assertTrue(parsed.win());
        assertTrue(parsed.buildTimeline());
    }

    @Test
    public void fallbackUsesFinalSlotOrderAndDoesNotInventLegendaryTimeline() {
        Participant participant = participant(1, 1);
        participant.boots = 3006;
        participant.item0 = 447002;
        participant.item1 = 4001;
        participant.item2 = 447001;
        Match match = match("EUW1_1", participant, List.of());
        match.eventData = null;

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG);

        assertEquals(Integer.valueOf(447002), parsed.firstPrismaticId());
        assertTrue(parsed.firstPrismaticFallback());
        assertEquals(List.of(447002, 447001), parsed.prismatics());
        assertTrue(parsed.legendaryItems().isEmpty());
        assertFalse(parsed.buildTimeline());
        assertFalse(parsed.win());
    }

    @Test
    public void undoCancelsThePurchasedItemAndEventsFromOtherParticipantsAreIgnored() {
        Participant participant = participant(1, 3);
        participant.boots = 3006;
        var match = match("EUW1_1", participant, List.of(
            event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_UNDO", 1100, 447001),
            event("ITEM_PURCHASED", 1200, 4001)));
        match.eventData.put("item_events", List.of(
            event("ITEM_PURCHASED", 900, 447002, 2),
            event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_UNDO", 1100, 447001),
            event("ITEM_PURCHASED", 1200, 4001)));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG);

        assertNull(parsed.firstPrismaticId());
        assertTrue(parsed.prismatics().isEmpty());
        assertEquals(List.of(4001), parsed.legendaryItems());
    }

    public static Participant participant(int id, int placement) {
        Participant participant = new Participant();
        participant.id = id;
        participant.puuid = "player-" + id;
        participant.champion = 27;
        participant.subTeamPlacement = placement;
        return participant;
    }

    public static Match match(String id, Participant participant, List<Map<String, Object>> events) {
        Match match = new Match();
        match.gameId = id;
        match.patch = "16.19.1";
        match.queue = GameQueueType.CHERRY;
        match.participants = List.of(participant);
        match.eventData = new HashMap<>(Map.of("participants", Map.of(String.valueOf(participant.id), participant.puuid),
            "item_events", events));
        return match;
    }

    public static Map<String, Object> event(String type, long time, int item) {
        return event(type, time, item, 1);
    }

    private static Map<String, Object> event(String type, long time, int item, int participant) {
        return Map.of("event", type, "participant", participant, "timestamp", time,
            "item", item, "before", 0, "after", 0);
    }

    public static Participant tooltipPath() {
        Participant participant = participant(1, 3);
        participant.item0 = 447001;
        participant.item1 = 4001;
        participant.item2 = 447002;
        participant.item3 = 4003;
        return participant;
    }
}
