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
        won.item0 = 3006; won.item1 = 447001; won.item2 = 4001; won.item3 = 447002; won.item4 = 4002;
        accumulator.accept(match("EUW1_1", won, List.of(
            event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 4001),
            event("ITEM_PURCHASED", 3000, 447002),
            event("ITEM_PURCHASED", 4000, 4002))));

        var lost = participant(1, 1);
        lost.boots = 3006;
        lost.augments = List.of(11, 33);
        lost.item0 = 3006; lost.item1 = 447001; lost.item2 = 4001; lost.item3 = 447002;
        accumulator.accept(match("EUW1_2", lost, List.of(
            event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 4001),
            event("ITEM_PURCHASED", 3000, 447002))));

        ArenaBuildData result = accumulator.finish();
        var core = result.coreBuilds().get(0);

        assertEquals(1, result.coreBuilds().size());
        assertEquals(2, result.games());
        assertEquals(1, result.wins());
        assertEquals(0.5, result.winrate(), 0.00001);
        assertEquals(2, core.matches());
        assertEquals(List.of(3006, 447001), core.items());
        assertEquals(2, core.builds().size());
        assertEquals(List.of(4001, 4002), core.builds().get(0).items());
        assertEquals(1, core.builds().get(0).matches());
        assertEquals(1, core.builds().get(0).wins());
        assertEquals(List.of(447001, 447002), core.prismatics().stream().map(ArenaBuildData.Option::id).toList());
        assertEquals(2, core.prismatics().get(0).matches());
        assertEquals(1, core.prismatics().get(0).wins());
        assertEquals(2, core.augments().get(0).get(0).matches());
        assertEquals(1, core.augments().get(0).get(0).wins());
        assertEquals(2, result.prismatics().size());
        assertEquals(2, result.prismatics().stream()
            .filter(option -> option.id() == 447001).findFirst().orElseThrow().matches());
        assertEquals(2, result.augments().get(0).get(0).matches());
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

        assertEquals(1, result.games());
        assertTrue(result.coreBuilds().isEmpty());
        assertEquals(1, result.coverage().missingCoreGames());
        assertEquals(1, result.prismatics().get(0).matches());
        assertEquals(6, result.augments().size());
        assertEquals(11, result.augments().get(0).get(0).id());
        assertTrue(result.augments().get(1).isEmpty());
        assertEquals(33, result.augments().get(2).get(0).id());
    }

    @Test
    public void payloadRoundTripsAndContainsOnlyTheNewShape() {
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);
        var participant = participant(1, 3);
        participant.boots = 3006;
        participant.augments = List.of(11);
        accumulator.accept(match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 447001))));
        ArenaBuildData source = accumulator.finish();

        var decoded = JsonCodec.fromJson(JsonCodec.toJson(source), ArenaBuildData.class);
        assertEquals(source, decoded);
        var json = new JSONObject(JsonCodec.toJson(source));
        assertFalse(json.has("schemaVersion"));
        assertFalse(json.has("aggregationVersion"));
        assertTrue(json.has("winrate"));
        assertFalse(json.has("stats"));
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
