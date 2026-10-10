package com.safjnest.lol.service;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.*;

import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

import com.safjnest.lol.model.ArenaBuildData;
import com.safjnest.utils.JsonCodec;

public class ArenaChampionAnalyzerTest {

    @Test
    public void aggregatesCoreItemsPrismaticsAndAugmentsWithTheirOwnGamesAndWins() {
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);

        var won = participant(1, 3);
        won.boots = 3006;
        won.augments = List.of(11, 22);
        accumulator.accept(match("EUW1_1", won, List.of(
            event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 4001),
            event("ITEM_PURCHASED", 3000, 447002),
            event("ITEM_PURCHASED", 4000, 4002))));

        var lost = participant(1, 1);
        lost.boots = 3006;
        lost.augments = List.of(11, 33);
        accumulator.accept(match("EUW1_2", lost, List.of(
            event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 4001),
            event("ITEM_PURCHASED", 3000, 447002))));

        ArenaBuildData result = accumulator.finish();
        var core = result.cores().get(0);

        assertEquals(1, result.cores().size());
        assertEquals(2, result.stats().games());
        assertEquals(1, result.stats().wins());
        assertEquals(0.5, result.stats().winRate(), 0.00001);
        assertEquals(2, core.stats().games());
        assertEquals(List.of(4001, 4002), core.items().stream().map(ArenaBuildData.ItemOption::itemId).toList());
        assertEquals(2, core.items().get(0).games());
        assertEquals(1, core.items().get(0).wins());
        assertEquals(List.of(447002), core.prismatics().stream().map(ArenaBuildData.PrismaticOption::prismaticId).toList());
        assertEquals(2, core.prismatics().get(0).games());
        assertEquals(1, core.prismatics().get(0).wins());
        assertEquals(2, core.augments().get(0).games());
        assertEquals(1, core.augments().get(0).wins());
        assertEquals(2, result.prismatics().size());
        assertEquals(2, result.prismatics().stream()
            .filter(option -> option.prismaticId() == 447001).findFirst().orElseThrow().games());
        assertEquals(2, result.augments().get(0).games());
        assertEquals(2, result.coverage().coreGames());
        assertEquals(2, result.coverage().buildTimelineGames());
    }

    @Test
    public void missingCoreStillContributesGlobalChoicesAndKeepsAugmentPositions() {
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);
        var participant = participant(1, 3);
        participant.item0 = 447001;
        participant.augments = java.util.Arrays.asList(11, null, 33);
        acceptWithoutCore(accumulator, participant);

        ArenaBuildData result = accumulator.finish();

        assertEquals(1, result.stats().games());
        assertTrue(result.cores().isEmpty());
        assertEquals(1, result.coverage().missingCoreGames());
        assertEquals(1, result.prismatics().get(0).games());
        assertEquals(List.of(1, 3), result.augments().stream().map(ArenaBuildData.AugmentOption::position).toList());
    }

    @Test
    public void payloadRoundTripsAsVersionFourAndContainsOnlyTheNewShape() {
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);
        var participant = participant(1, 3);
        participant.boots = 3006;
        participant.augments = List.of(11);
        accumulator.accept(match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 447001))));
        ArenaBuildData source = accumulator.finish();

        var decoded = JsonCodec.fromJson(JsonCodec.toJson(source), ArenaBuildData.class);
        assertEquals(source, decoded);
        var json = new JSONObject(JsonCodec.toJson(source));
        assertEquals(4, json.getInt("schemaVersion"));
        assertEquals(4, json.getInt("aggregationVersion"));
        assertTrue(json.getJSONObject("stats").has("winRate"));
        assertFalse(json.has("positions"));
        assertFalse(json.has("builds"));
        assertFalse(json.getJSONArray("prismatics").getJSONObject(0).has("position"));
        assertEquals(source, JsonCodec.fromDocument(JsonCodec.toDocument(source), ArenaBuildData.class));
    }

    private static void acceptWithoutCore(ArenaChampionAnalyzer.Accumulator accumulator,
            com.safjnest.lol.model.match.Participant participant) {
        var match = match("EUW1_1", participant, List.of());
        match.eventData = null;
        accumulator.accept(match);
    }
}
