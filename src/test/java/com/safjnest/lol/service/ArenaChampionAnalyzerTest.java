package com.safjnest.lol.service;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.json.JSONObject;

import com.safjnest.lol.model.ArenaBuildData;
import com.safjnest.lol.model.ArenaBuildData.AnchorKind;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.utils.JsonCodec;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ArenaChampionAnalyzerTest {

    @Test
    public void bootsUseTheirOwnWinrateAndChampionDenominator() {
        var accumulator = accumulator();
        var won = participant(1, 3);
        won.boots = 3006;
        accumulator.accept(match("EUW1_1", won, List.of(event("ITEM_PURCHASED", 1000, 3006))), true);
        var lost = participant(1, 2);
        lost.boots = 3006;
        var noTimeline = match("EUW1_2", lost, List.of());
        noTimeline.eventData = null;
        accumulator.accept(noTimeline);
        accumulator.accept(match("EUW1_3", participant(1, 2), List.of()));
        var source = accumulator.finish().arena();
        var boots = source.positions().boots().get(0);
        assertEquals(3, source.stats().games());
        assertEquals(2, boots.stats().games());
        assertEquals(1, boots.stats().wins());
        assertEquals(0.5, boots.stats().winrate(), 0.00001);
        assertEquals(3, boots.denominator());
        assertEquals(2.0 / 3, boots.pickrate(), 0.00001);
        assertEquals(1, boots.timing().timeCount());
        assertEquals(Double.valueOf(1000), boots.timing().averageTimeMillis());
    }

    @Test
    public void globalPrismaticPositionsOneThroughSixFollowTimeline() {
        var accumulator = accumulator();
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 6; i > 0; i--) events.add(event("ITEM_PURCHASED", i * 1000, 447000 + i));
        accumulator.accept(match("EUW1_1", participant(1, 3), events), true);
        var finalOnly = participant(1, 2);
        finalOnly.item0 = 447001;
        var missing = match("EUW1_2", finalOnly, List.of());
        missing.eventData = null;
        accumulator.accept(missing);
        var result = accumulator.finish().arena();
        assertEquals(6, result.positions().prismatics().size());
        for (int i = 0; i < 6; i++) {
            var slot = result.positions().prismatics().get(i);
            var choice = slot.options().get(0);
            assertEquals(i + 1, slot.position());
            assertEquals(447001 + i, choice.id());
            assertEquals(i == 0 ? 2 : 1, choice.stats().games());
            assertEquals(1, choice.stats().wins());
            assertEquals(2, choice.denominator());
        }
        assertEquals(2, membership(result, Kind.PRISMATIC, 447001).stats().games());
        assertEquals(Map.of("EXACT", 1L, "FALLBACK", 1L), result.coverage().firstPrismaticQuality());
    }

    @Test
    public void positionalAugmentsOneThroughSixAreIndependentOfCores() {
        var accumulator = accumulator();
        var p = participant(1, 3);
        p.augments = List.of(11, 22, 33, 44, 55, 66);
        accumulator.accept(match("EUW1_1", p, List.of()));
        var result = accumulator.finish().arena();
        assertTrue(result.cores().isEmpty());
        assertEquals(6, result.positions().augments().size());
        for (int i = 0; i < 6; i++) {
            var slot = result.positions().augments().get(i);
            assertEquals(i + 1, slot.position());
            assertEquals((i + 1) * 11, slot.options().get(0).id());
            assertEquals(1, slot.options().get(0).denominator());
            assertEquals(0, slot.options().get(0).timing().timeCount());
        }
    }

    @Test
    public void legendaryPositionsRemainIndependentAndDoNotUsePrismaticSlots() {
        var accumulator = accumulator();
        var p = tooltipPath();
        accumulator.accept(match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 4001), event("ITEM_PURCHASED", 3000, 447002),
            event("ITEM_PURCHASED", 4000, 4003))), true);
        var result = accumulator.finish().arena();
        assertTrue(result.cores().isEmpty());
        assertEquals(2, result.positions().legendaryItems().size());
        assertEquals(4001, result.positions().legendaryItems().get(0).options().get(0).id());
        assertEquals(4003, result.positions().legendaryItems().get(1).options().get(0).id());
        assertEquals(2, result.positions().legendaryItems().get(1).position());
    }

    @Test
    public void genericCoresUseBootsAndTheirOwnAnchorAndFallbackPoolsAugments() {
        var accumulator = accumulator();
        var a = tooltipPath(); a.boots = 3006; a.augments = List.of(11, 22);
        var b = tooltipPath(); b.boots = 3006; b.augments = List.of(33, 44);
        accumulator.accept(match("EUW1_1", a, List.of()));
        accumulator.accept(match("EUW1_2", b, List.of()));
        var result = accumulator.finish().arena();
        assertEquals(3, result.cores().size());
        assertEquals(1, core(result, AnchorKind.AUGMENT, 11).stats().games());
        assertEquals(1, core(result, AnchorKind.AUGMENT, 33).stats().games());
        var fallback = core(result, AnchorKind.PRISMATIC, 447001);
        assertEquals(2, fallback.stats().games());
        assertEquals(2, fallback.denominator());
        var root = root(fallback);
        assertTrue(root.context().augments().isEmpty());
        assertEquals(2, root.choices().legendary().get(0).stats().games());
        assertEquals(2, root.choices().augments().get(0).options().size());
        assertEquals(2, result.coverage().decisionCoreGames());
        assertEquals(2, result.coverage().fallbackCoreGames());
        assertEquals(2, result.stats().games());
    }

    @Test
    public void independentAnchorsPermitEachCoreWithoutTheOther() {
        var accumulator = accumulator();
        var a = participant(1, 3); a.boots = 3006; a.augments = List.of(11);
        var b = participant(1, 2); b.boots = 3006; b.item0 = 447001;
        accumulator.accept(match("EUW1_1", a, List.of()));
        accumulator.accept(match("EUW1_2", b, List.of()));
        var result = accumulator.finish().arena();
        assertEquals(2, result.cores().size());
        assertEquals(1, core(result, AnchorKind.AUGMENT, 11).stats().games());
        assertEquals(1, core(result, AnchorKind.PRISMATIC, 447001).stats().games());
        assertEquals(1, result.coverage().firstPrismaticQuality().get("UNRESOLVED").longValue());
    }

    @Test
    public void augmentGapDoesNotPromoteA3ToDecisionAnchor() {
        var accumulator = accumulator();
        var p = participant(1, 3); p.boots = 3006; p.item0 = 447001; p.augments = List.of(0, 0, 33);
        accumulator.accept(match("EUW1_1", p, List.of()));
        var result = accumulator.finish().arena();
        assertEquals(1, result.cores().size());
        assertEquals(AnchorKind.PRISMATIC, result.cores().get(0).key().anchorKind());
        assertEquals(3, result.positions().augments().get(0).position());
    }

    @Test
    public void completeStepsHavePrefixesAndRetainOneGameExactStates() {
        var accumulator = accumulator();
        var p = tooltipPath(); p.boots = 3006; p.augments = List.of(11, 22, 33);
        accumulator.accept(match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 4001), event("ITEM_PURCHASED", 3000, 447002),
            event("ITEM_PURCHASED", 4000, 4003))), true);
        var result = accumulator.finish().arena();
        var decision = core(result, AnchorKind.AUGMENT, 11);
        assertEquals(15, decision.steps().size());
        for (int i = 0; i <= 4; i++) {
            final int count = i;
            var step = decision.steps().stream().filter(s -> s.context().equipment().size() == count
                && s.context().augments().size() == 1).findFirst().orElseThrow();
            assertEquals(1, step.stats().games());
            if (count == 2) {
                assertEquals(4003, step.choices().legendary().get(0).id());
                assertEquals(447002, step.choices().prismatics().get(0).id());
            }
        }
        assertEquals(4, decision.steps().get(decision.steps().size() - 1).context().equipment().size());
        assertEquals(3, decision.steps().get(decision.steps().size() - 1).context().augments().size());
    }

    @Test
    public void directA3ChoicesDoNotRequireA2OrAnAugmentTimestamp() {
        var accumulator = accumulator();
        var p = tooltipPath(); p.boots = 3006; p.augments = List.of(11, 0, 33);
        accumulator.accept(match("EUW1_1", p, List.of()));
        var root = root(core(accumulator.finish().arena(), AnchorKind.AUGMENT, 11));
        assertEquals(1, root.choices().augments().size());
        var third = root.choices().augments().get(0);
        assertEquals(3, third.position());
        assertEquals(33, third.options().get(0).id());
        assertNull(third.options().get(0).timing().averageTimeMillis());
    }

    @Test
    public void observedBuildsPoolAugmentsAndLaterPrismaticsButKeepLegendaryOrder() {
        var accumulator = accumulator();
        var a = tooltipPath(); a.boots = 3006; a.augments = List.of(11);
        var b = tooltipPath(); b.boots = 3006; b.augments = List.of(22); b.item2 = 447003;
        var c = tooltipPath(); c.boots = 3006; c.item1 = 4003; c.item3 = 4001;
        accumulator.accept(match("EUW1_1", a, List.of()));
        accumulator.accept(match("EUW1_2", b, List.of()));
        accumulator.accept(match("EUW1_3", c, List.of()));
        var result = accumulator.finish().arena();
        assertEquals(2, result.builds().size());
        var common = result.builds().get(0);
        assertEquals(3006, common.bootsId());
        assertEquals(447001, common.firstPrismaticId());
        assertEquals(List.of(4001, 4003), common.legendaryIds());
        assertEquals(2, common.stats().games());
        assertEquals(3, common.denominator());
        assertEquals(2.0 / 3, common.pickrate(), 0.00001);
        assertEquals(List.of(4003, 4001), result.builds().get(1).legendaryIds());
        assertEquals(3, core(result, AnchorKind.PRISMATIC, 447001).stats().games());
    }

    @Test
    public void purchaseTimesDoNotSplitBuildsCoresOrSteps() {
        var accumulator = accumulator();
        var p = participant(1, 3); p.boots = 3006; p.augments = List.of(11);
        for (int i = 1; i <= 2; i++) accumulator.accept(match("EUW1_" + i, p, List.of(
            event("ITEM_PURCHASED", i * 1000, 447001), event("ITEM_PURCHASED", i * 2000, 4001),
            event("ITEM_PURCHASED", i * 3000, 4002))), true);
        var result = accumulator.finish().arena();
        assertEquals(1, result.builds().size());
        assertEquals(2, result.builds().get(0).stats().games());
        assertEquals(2, core(result, AnchorKind.AUGMENT, 11).stats().games());
        for (var step : core(result, AnchorKind.AUGMENT, 11).steps()) assertEquals(2, step.stats().games());
        var firstLegendary = result.positions().legendaryItems().get(0).options().get(0);
        assertEquals(Double.valueOf(3000), firstLegendary.timing().averageTimeMillis());
    }

    @Test
    public void anvilIsAbsentFromEverySelectablePayload() {
        var accumulator = accumulator();
        var p = tooltipPath(); p.boots = 3006; p.augments = List.of(11);
        accumulator.accept(match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 2000, 220007), event("ITEM_DESTROYED", 2000, 220007),
            event("ITEM_PURCHASED", 3000, 447002))), true);
        String json = JsonCodec.toJson(accumulator.finish().arena());
        assertFalse(json.contains("220007"));
        assertEquals(1, accumulator.finish().arena().builds().size());
    }

    @Test
    public void missingAndPartialTimelinesKeepFallbackBuildsCoresAndIndependentStats() {
        var accumulator = accumulator();
        var p = tooltipPath(); p.boots = 3006; p.augments = List.of(11, 0, 33, 44, 55, 66);
        p.subTeamPlacement = 0; p.win = true;
        var absent = match("EUW1_1", p, List.of()); absent.eventData = null;
        accumulator.accept(absent);
        accumulator.accept(match("EUW1_2", p, List.of(event("ITEM_PURCHASED", 1000, 4001))), false);
        var result = accumulator.finish().arena();
        assertEquals(2, result.stats().games());
        assertEquals(2, result.stats().wins());
        assertEquals(0, result.stats().placementGames());
        assertNull(result.stats().averagePlacement());
        assertEquals(2, result.builds().get(0).stats().games());
        assertEquals(2, result.positions().boots().get(0).stats().games());
        assertEquals(1, membership(result, Kind.ITEM, 4001).timing().timeCount());
        assertEquals(6, result.positions().augments().get(4).position());
        assertEquals(2, core(result, AnchorKind.AUGMENT, 11).stats().games());
        assertEquals(2, core(result, AnchorKind.PRISMATIC, 447001).stats().games());
        assertEquals(Map.of("FALLBACK", 1L, "MIXED", 1L), result.builds().get(0).orderQuality());
    }

    @Test
    public void unknownOrderKeepsMembershipWithoutInventingPositionOrObservedBuild() {
        var accumulator = accumulator();
        var p = participant(1, 3); p.boots = 3006; p.augments = List.of(11);
        var untimed = new HashMap<>(event("ITEM_PURCHASED", 1000, 4001));
        untimed.remove("timestamp");
        accumulator.accept(match("EUW1_1", p, List.of(untimed)), true);
        var result = accumulator.finish().arena();
        assertEquals(1, membership(result, Kind.ITEM, 4001).stats().games());
        assertEquals(1, membership(result, Kind.ITEM, 4001).unpositionedGames());
        assertEquals(1, result.positions().unpositioned().get(0).stats().games());
        assertTrue(result.positions().legendaryItems().isEmpty());
        assertTrue(result.builds().isEmpty());
        assertEquals(1, result.cores().size());
    }

    @Test
    public void duplicateEventsPurchasesAndAugmentsCountOncePerRelevantKey() {
        var accumulator = accumulator();
        var p = tooltipPath(); p.boots = 3006; p.augments = List.of(11, 11, 22);
        var first = event("ITEM_PURCHASED", 1000, 447001);
        accumulator.accept(match("EUW1_1", p, List.of(first, first, event("ITEM_PURCHASED", 2000, 447001))), true);
        var result = accumulator.finish().arena();
        assertEquals(1, membership(result, Kind.PRISMATIC, 447001).stats().games());
        assertEquals(1, membership(result, Kind.PRISMATIC, 447001).timing().timeCount());
        assertEquals(1, result.positions().prismatics().get(0).options().get(0).stats().games());
        assertEquals(1, membership(result, Kind.AUGMENT, 11).stats().games());
        assertEquals(1, result.positions().augments().get(0).options().get(0).stats().games());
        assertEquals(1, result.positions().augments().get(1).options().get(0).stats().games());
        assertEquals(1, result.builds().get(0).stats().games());
        for (var core : result.cores()) for (var step : core.steps()) assertEquals(1, step.stats().games());
    }

    @Test
    public void conditionalChoiceDenominatorsUseTheActualParentPopulation() {
        var accumulator = accumulator();
        for (int i = 1; i <= 3; i++) {
            var p = participant(1, i == 1 ? 3 : 2); p.boots = 3006; p.augments = List.of(11);
            p.item0 = i <= 2 ? 447001 : 447002;
            if (i == 1) p.item1 = 4001;
            accumulator.accept(match("EUW1_" + i, p, List.of()));
        }
        var result = accumulator.finish().arena();
        var decision = core(result, AnchorKind.AUGMENT, 11);
        var root = root(decision);
        var first = root.choices().prismatics().get(0);
        assertEquals(3, first.denominator());
        assertEquals(2, first.stats().games());
        assertEquals(2.0 / 3, first.pickrate(), 0.00001);
        var state = decision.steps().stream().filter(s -> s.context().equipment().size() == 1
            && s.context().equipment().get(0).id() == 447001).findFirst().orElseThrow();
        assertEquals(2, state.stats().games());
        assertEquals(2, state.choices().legendary().get(0).denominator());
        assertEquals(1, state.choices().legendary().get(0).stats().games());
        assertEquals(0.5, state.choices().legendary().get(0).pickrate(), 0.00001);
        assertEquals(2, root(core(result, AnchorKind.PRISMATIC, 447001)).choices().legendary().get(0).denominator());
    }

    @Test
    public void duplicateMatchAndParticipantInputsAreRejectedBeforeMutation() {
        var accumulator = accumulator();
        var p = participant(1, 3);
        var source = match("EUW1_1", p, List.of());
        accumulator.accept(source);
        var before = accumulator.finish().arena();
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(source));
        assertEquals(before, accumulator.finish().arena());
        var duplicate = match("EUW1_2", p, List.of()); duplicate.participants = List.of(p, p);
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(duplicate));
        assertEquals(before, accumulator.finish().arena());
        duplicate.participants = List.of(p);
        assertTrue(accumulator.accept(duplicate));
        assertEquals(2, accumulator.finish().arena().stats().games());
    }

    @Test
    public void twoMatchingParticipantsCountTwoGamesButOneMatch() {
        var accumulator = accumulator();
        var first = participant(1, 3); var second = participant(2, 2);
        first.boots = second.boots = 3006;
        var source = match("EUW1_1", first, List.of());
        source.participants = List.of(first, second);
        source.eventData.put("participants", Map.of("1", first.puuid, "2", second.puuid));
        accumulator.accept(source);
        var result = accumulator.finish().arena();
        assertEquals(1, result.coverage().matches());
        assertEquals(2, result.coverage().participantGames());
        assertEquals(2, result.positions().boots().get(0).stats().games());
        assertEquals(Map.of(2, 1L, 3, 1L), result.stats().placements());
    }

    @Test
    public void scopeRejectsWrongQueuePatchAndInvalidMatchIds() {
        var accumulator = accumulator();
        var source = match("EUW1_1", participant(1, 3), List.of());
        source.queue = GameQueueType.ARAM; assertFalse(accumulator.accept(source));
        source.queue = GameQueueType.CHERRY; source.patch = "16.19"; assertFalse(accumulator.accept(source));
        source.patch = "16.19.1"; source.gameId = "1";
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(source));
        assertEquals(0, accumulator.finish().arena().stats().games());
        var filter = accumulator.finish().filter();
        assertEquals(27, filter.champion());
        assertEquals("16.19.1", filter.patch());
        assertEquals(GameQueueType.CHERRY, filter.queue());
        assertNull(filter.rank()); assertNull(filter.lane()); assertNull(filter.region());
        assertEquals(0, filter.opponent()); assertEquals(0, filter.duo());
    }

    @Test
    public void buildPayloadRoundTripsJsonBsonAndRemainsDetached() {
        var accumulator = accumulator();
        var p = tooltipPath(); p.boots = 3006; p.augments = List.of(11, 22, 33);
        accumulator.accept(match("EUW1_1", p, List.of()));
        Build source = accumulator.finish();
        var decoded = Build.fromJson(source.toJson());
        assertNotNull(decoded);
        assertEquals(source.arena(), decoded.arena());
        assertEquals(source.filter().toKey(), decoded.filter().toKey());
        var bson = JsonCodec.toDocument(source);
        assertTrue(bson.get("arena") instanceof Map<?, ?>);
        var bsonBuild = JsonCodec.fromDocument(bson, Build.class);
        assertNotNull(bsonBuild);
        assertEquals(source.arena(), bsonBuild.arena());
        var json = new JSONObject(source.toJson()).getJSONObject("arena");
        assertEquals(1.0, json.getJSONObject("stats").getDouble("winrate"), 0.00001);
        assertEquals(1.0, json.getJSONArray("builds").getJSONObject(0).getDouble("pickrate"), 0.00001);
        assertThrows(UnsupportedOperationException.class, () -> source.arena().cores().clear());
        assertThrows(UnsupportedOperationException.class, () -> source.arena().cores().get(0).steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> source.arena().stats().placements().clear());
        accumulator.accept(match("EUW1_2", p, List.of()));
        assertEquals(1, source.arena().stats().games());
        assertEquals(2, accumulator.finish().arena().stats().games());
        source.filter().setChampion(99);
        assertEquals(27, accumulator.finish().filter().champion());
    }

    @Test
    public void prismaticsBeyondSixKeepMembershipAndUnpositionedCoverage() {
        var accumulator = accumulator();
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 1; i <= 7; i++) events.add(event("ITEM_PURCHASED", i * 1000, 447000 + i));
        accumulator.accept(match("EUW1_1", participant(1, 3), events), true);
        var result = accumulator.finish().arena();
        assertEquals(6, result.positions().prismatics().size());
        assertEquals(1, membership(result, Kind.PRISMATIC, 447007).stats().games());
        assertEquals(1, membership(result, Kind.PRISMATIC, 447007).unpositionedGames());
    }

    @Test
    public void unspecifiedSourceCompletenessDoesNotBecomeExactEvidence() {
        var accumulator = accumulator();
        accumulator.accept(match("EUW1_1", participant(1, 3), List.of(event("ITEM_PURCHASED", 1000, 447001))));
        var result = accumulator.finish().arena();
        assertEquals(1, membership(result, Kind.PRISMATIC, 447001).stats().games());
        assertTrue(result.positions().prismatics().isEmpty());
        assertEquals(Map.of("UNRESOLVED", 1L), result.coverage().firstPrismaticQuality());
        assertTrue(result.coverage().missing().containsKey("ITEM_HISTORY_PARTIAL"));
    }

    private static ArenaChampionAnalyzer.Accumulator accumulator() {
        return ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);
    }

    private static ArenaBuildData.Core core(ArenaBuildData data, AnchorKind kind, int id) {
        return data.cores().stream().filter(c -> c.key().anchorKind() == kind && c.key().anchorId() == id).findFirst().orElseThrow();
    }

    private static ArenaBuildData.Step root(ArenaBuildData.Core core) { return core.steps().get(0); }

    private static Build.Choice membership(ArenaBuildData data, Kind kind, int id) {
        return data.positions().membership().stream().filter(c -> c.kind() == kind && c.id() == id).findFirst().orElseThrow();
    }
}
