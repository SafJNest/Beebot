package com.safjnest.lol.service;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.statistics.ArenaChampionStatistics;
import com.safjnest.lol.model.statistics.ArenaChampionStatistics.Distribution;
import com.safjnest.utils.JsonCodec;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ArenaChampionAnalyzerTest {

    @Test
    public void bootsUseTheirOwnWinrateAndChampionPrevalenceDenominator() {
        var accumulator = accumulator();
        var won = participant(1, 3);
        won.boots = 3006;
        accumulator.accept(match("EUW1_1", won, List.of(event("ITEM_PURCHASED", 1000, 3006))));
        var lost = participant(1, 2);
        lost.boots = 3006;
        var missingTime = match("EUW1_2", lost, List.of());
        missingTime.eventData = null;
        accumulator.accept(missingTime);
        accumulator.accept(match("EUW1_3", participant(1, 2), List.of()));
        var source = accumulator.finish();
        var boots = source.boots().get(0).overall();
        assertEquals(3, source.overall().games());
        assertEquals(2, boots.stats().games());
        assertEquals(1, boots.stats().wins());
        assertEquals(0.5, boots.stats().winrate(), 0.00001);
        assertEquals(3, boots.denominator());
        assertEquals(2.0 / 3, boots.pickrate(), 0.00001);
        assertEquals(1, boots.timing().timeCount());
        assertEquals(Double.valueOf(1000), boots.timing().averageTimeMillis());
    }

    @Test
    public void positionsOneThroughSixUseTimelineOrderAndIndependentDenominators() {
        var accumulator = accumulator();
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 6; i > 0; i--) events.add(event("ITEM_PURCHASED", i * 1000, 447000 + i));
        accumulator.accept(match("EUW1_1", participant(1, 3), events));
        var finalOnly = participant(1, 2);
        finalOnly.item0 = 447001;
        var missing = match("EUW1_2", finalOnly, List.of());
        missing.eventData = null;
        accumulator.accept(missing);
        var result = accumulator.finish();
        assertEquals(6, result.prismatics().size());
        for (int i = 0; i < 6; i++) {
            var distribution = result.prismatics().get(i);
            var position = distribution.positions().get(0);
            assertEquals(Integer.valueOf(i + 1), position.position());
            assertEquals(447001 + i, position.id());
            assertEquals(1, position.stats().games());
            assertEquals(1, position.stats().wins());
            assertEquals(2, position.denominator());
        }
        var first = result.prismatics().get(0).overall();
        assertEquals(2, first.stats().games());
        assertEquals(0.5, first.stats().winrate(), 0.00001);
        assertEquals(1, first.unpositionedGames());
    }

    @Test
    public void equalOrderedCoresMergeDespiteTimesAndReversedItemsRemainSeparate() {
        var accumulator = accumulator();
        accumulator.accept(match("EUW1_1", participant(1, 3), List.of(
            event("ITEM_PURCHASED", 1000, 4001), event("ITEM_PURCHASED", 2000, 4002))));
        accumulator.accept(match("EUW1_2", participant(1, 2), List.of(
            event("ITEM_PURCHASED", 5000, 4001), event("ITEM_PURCHASED", 8000, 4002))));
        accumulator.accept(match("EUW1_3", participant(1, 3), List.of(
            event("ITEM_PURCHASED", 1000, 4002), event("ITEM_PURCHASED", 2000, 4001))));
        var result = accumulator.finish();
        assertEquals(2, result.builds().size());
        Build common = result.builds().get(0);
        assertEquals(2, common.orderedCore().stats().games());
        assertEquals(1, common.orderedCore().stats().wins());
        assertEquals(Double.valueOf(3000), common.orderedCore().entries().get(0).timing().averageTimeMillis());
        assertEquals(List.of(4001, 4002), ids(common));
        assertEquals(List.of(4002, 4001), ids(result.builds().get(1)));
        assertEquals(1, common.paths().size());
        assertEquals(2, common.orderedItems().size());
        assertEquals(2, common.orderedItems().get(0).population().games());
    }

    @Test
    public void anvilPurchaseIsCoreItemAndAwardIsOnlyPrismaticDistribution() {
        var accumulator = accumulator();
        accumulator.accept(match("EUW1_1", participant(1, 3), List.of(
            event("ITEM_PURCHASED", 1000, 4001), event("ITEM_PURCHASED", 2000, 220007),
            event("ITEM_DESTROYED", 2001, 220007), event("ITEM_PURCHASED", 3000, 447001))));
        var result = accumulator.finish();
        assertEquals(List.of(4001, 220007), ids(result.builds().get(0)));
        assertEquals(220007, result.items().get(1).overall().id());
        assertEquals(1, result.prismatics().size());
        assertEquals(447001, result.prismatics().get(0).overall().id());
    }

    @Test
    public void absentAndPartialTimelinePreserveOutcomeBootsItemsPrismaticsAndAugments() {
        var accumulator = accumulator();
        var participant = participant(1, 0);
        participant.win = true;
        participant.boots = 3006;
        participant.item0 = 4001;
        participant.item1 = 447001;
        participant.augments = List.of(11, 0, 33, 44, 55, 66);
        var absent = match("EUW1_1", participant, List.of());
        absent.eventData = null;
        accumulator.accept(absent);
        accumulator.accept(match("EUW1_2", participant, List.of(event("ITEM_PURCHASED", 1000, 4001))), false);
        var result = accumulator.finish();
        assertEquals(2, result.overall().games());
        assertEquals(2, result.overall().wins());
        assertEquals(0, result.overall().placementGames());
        assertNull(result.overall().averagePlacement());
        assertEquals(2, result.boots().get(0).overall().stats().games());
        assertEquals(2, result.items().get(0).overall().stats().games());
        assertEquals(1, result.items().get(0).overall().timing().timeCount());
        assertEquals(2, result.prismatics().get(0).overall().unpositionedGames());
        assertTrue(result.builds().isEmpty());
        assertEquals(Integer.valueOf(6), result.augments().get(4).positions().get(0).position());
    }

    @Test
    public void repeatedChoicesAndAugmentPositionsCountOverallOncePerGame() {
        var accumulator = accumulator();
        var participant = participant(1, 3);
        participant.augments = List.of(11, 11, 22);
        var first = event("ITEM_PURCHASED", 1000, 447001);
        accumulator.accept(match("EUW1_1", participant, List.of(first, first,
            event("ITEM_PURCHASED", 2000, 447001), event("ITEM_PURCHASED", 3000, 4001))));
        var result = accumulator.finish();
        var prismatic = result.prismatics().get(0);
        assertEquals(1, prismatic.overall().stats().games());
        assertEquals(1, prismatic.overall().timing().timeCount());
        assertEquals(2, prismatic.positions().size());
        var augment = result.augments().get(0);
        assertEquals(1, augment.overall().stats().games());
        assertEquals(2, augment.positions().size());
        assertEquals(3, result.builds().get(0).orderedAugments().size());
        assertEquals(1, result.builds().get(0).orderedAugments().get(0).options().get(0).denominator());
    }

    @Test
    public void matchAndParticipantDuplicatesAreRejectedBeforeMutation() {
        var accumulator = accumulator();
        var participant = participant(1, 3);
        var match = match("EUW1_1", participant, List.of());
        accumulator.accept(match);
        var before = accumulator.finish();
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(match));
        assertEquals(before, accumulator.finish());
        var duplicate = match("EUW1_2", participant, List.of());
        duplicate.participants = List.of(participant, participant);
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(duplicate));
        assertEquals(before, accumulator.finish());
        duplicate.participants = List.of(participant);
        assertTrue(accumulator.accept(duplicate));
        assertEquals(2, accumulator.finish().overall().games());
    }

    @Test
    public void sameChampionOnTwoPlayersCountsTwoGamesButOneMatch() {
        var accumulator = accumulator();
        var first = participant(1, 3);
        var second = participant(2, 2);
        first.boots = second.boots = 3006;
        var match = match("EUW1_1", first, List.of());
        match.participants = List.of(first, second);
        match.eventData.put("participants", Map.of("1", first.puuid, "2", second.puuid));
        accumulator.accept(match);
        var result = accumulator.finish();
        assertEquals(1, result.coverage().matches());
        assertEquals(2, result.coverage().participantGames());
        assertEquals(2, result.boots().get(0).overall().stats().games());
        assertEquals(Map.of(2, 1L, 3, 1L), result.overall().placements());
    }

    @Test
    public void versionedScopeRejectsWrongQueuePartialPatchAndInvalidIds() {
        var accumulator = accumulator();
        var source = match("EUW1_1", participant(1, 3), List.of());
        source.queue = GameQueueType.ARAM;
        assertFalse(accumulator.accept(source));
        source.queue = GameQueueType.CHERRY;
        source.patch = "16.19";
        assertFalse(accumulator.accept(source));
        source.patch = "16.19.1";
        source.gameId = "1";
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(source));
        assertEquals(0, accumulator.finish().overall().games());
    }

    @Test
    public void snapshotsRoundTripJsonAndStructuredBsonAndStayDetached() {
        var accumulator = accumulator();
        var participant = participant(1, 3);
        participant.augments = List.of(11, 22);
        accumulator.accept(match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 2000, 220007), event("ITEM_PURCHASED", 3000, 447001))));
        var source = accumulator.finish();
        assertEquals(source, JsonCodec.fromJson(JsonCodec.toJson(source), ArenaChampionStatistics.class));
        var bson = JsonCodec.toDocument(source);
        assertTrue(bson.get("builds") instanceof List<?>);
        assertEquals(source, JsonCodec.fromDocument(bson, ArenaChampionStatistics.class));
        assertEquals(source.builds().get(0), Build.fromJson(source.builds().get(0).toJson()));
        assertThrows(UnsupportedOperationException.class, () -> source.builds().clear());
        assertThrows(UnsupportedOperationException.class, () -> source.builds().get(0).orderedCore().entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> source.overall().placements().clear());
        accumulator.accept(match("EUW1_2", participant, List.of()));
        assertEquals(1, source.overall().games());
        assertEquals(2, accumulator.finish().overall().games());
    }

    @Test
    public void prismaticBeyondSixStillHasIndependentMembership() {
        var accumulator = accumulator();
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 1; i <= 7; i++) events.add(event("ITEM_PURCHASED", i * 1000, 447000 + i));
        accumulator.accept(match("EUW1_1", participant(1, 3), events));
        var last = accumulator.finish().prismatics().get(6);
        assertEquals(1, last.overall().stats().games());
        assertEquals(1, last.overall().unpositionedGames());
        assertTrue(last.positions().isEmpty());
    }

    private static ArenaChampionAnalyzer.Accumulator accumulator() {
        return ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);
    }

    private static List<Integer> ids(Build build) {
        List<Integer> result = new ArrayList<>();
        for (var entry : build.orderedCore().entries()) result.add(entry.id());
        return result;
    }
}
