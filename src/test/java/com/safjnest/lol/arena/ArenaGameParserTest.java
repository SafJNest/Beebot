package com.safjnest.lol.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

public class ArenaGameParserTest {

    @Test
    public void exactEvidenceBuildsSemanticCoreAndNormalizesPostCoreItems() {
        Participant participant = participant(3);
        participant.augments = List.of(11, 33, 66);
        Match match = match(exactEvents(220001, 2000, 5000, List.of(3006, 1001, 220007), List.of(
            item("ITEM_PURCHASED", 1000, 3006, 0, 0),
            item("ITEM_PURCHASED", 2000, 1001, 0, 0),
            item("ITEM_PURCHASED", 6000, 4001, 0, 0),
            item("ITEM_PURCHASED", 7000, 4002, 0, 0),
            item("ITEM_SOLD", 8000, 4002, 4002, 0),
            item("ITEM_PURCHASED", 9000, 4003, 0, 0),
            item("ITEM_UNDO", 10000, 0, 0, 0)
        )));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertEquals(Integer.valueOf(3), parsed.subTeamPlacement());
        assertTrue(parsed.win());
        assertEquals(220001, parsed.core().firstPrismaticId());
        assertEquals(3006, parsed.core().bootsId());
        assertEquals(List.of(1001, 3006), parsed.core().snapshotItemIds());
        assertEquals(2000, parsed.core().firstPrismaticTimestampMillis());
        assertEquals(5000, parsed.core().snapshotTimestampMillis());
        assertEquals(parsed.core().identity(), coreWithDifferentTimes(parsed.core()).identity());
        assertEquals(List.of(new ParsedArenaGame.Item(4001, 1, 6000)), parsed.items());
        assertEquals(List.of(
            new ParsedArenaGame.Augment(11, 1, null),
            new ParsedArenaGame.Augment(33, 2, null),
            new ParsedArenaGame.Augment(66, 3, null)
        ), parsed.augments());
        assertEquals(List.of(new ParsedArenaGame.Prismatic(220001, 2000L)), parsed.prismatics());
        assertEquals(0, parsed.coverage().missing());
    }

    @Test
    public void unknownEvidenceAndAbsentAugmentsRemainMissing() {
        Participant participant = participant(2);
        participant.augments = List.of();
        Match match = match(Map.of(
            "participants", Map.of("1", "player-one"),
            "item_events", List.of(),
            "arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
                "first_prismatic", Map.of("status", "UNKNOWN", "reason", "NO_DIRECT_SELECTION_SIGNAL"),
                "core_snapshot", Map.of("status", "UNKNOWN", "reason", "NO_DIRECT_INVENTORY_FRAME")
            )))
        ));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertNull(parsed.core());
        assertTrue(parsed.items().isEmpty());
        assertTrue(parsed.prismatics().isEmpty());
        assertTrue(parsed.augments().isEmpty());
        assertTrue(parsed.coverage().missingReasons().containsKey("AUGMENTS_ABSENT_OR_EMPTY"));
        assertTrue(parsed.coverage().missingReasons().containsKey("NO_DIRECT_SELECTION_SIGNAL"));
        assertTrue(parsed.coverage().missingReasons().containsKey("NO_DIRECT_INVENTORY_FRAME"));
    }

    @Test
    public void excludes220007FromPrismaticAndCoreAndCountsRejection() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220007, 1000, 2000, List.of(220007, 3006), List.of()));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertNull(parsed.core());
        assertTrue(parsed.prismatics().isEmpty());
        assertEquals(1, parsed.coverage().rejectedReasons().get("FIRST_PRISMATIC_220007_EXCLUDED").intValue());
        assertTrue(parsed.coverage().missingReasons().containsKey("CORE_WITHOUT_EXACT_FIRST_PRISMATIC"));
    }

    @Test
    public void excludesLater220007WithoutChangingFirstPrismaticOrCoreIdentity() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220001, 1000, 2000, List.of(3006), List.of(
            item("ITEM_PURCHASED", 3000, 220007, 0, 0)
        )));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertEquals(220001, parsed.core().firstPrismaticId());
        assertEquals(new ParsedArenaGame.CoreIdentity(220001, 3006, List.of(3006)), parsed.core().identity());
        assertEquals(List.of(new ParsedArenaGame.Prismatic(220001, 1000L)), parsed.prismatics());
        assertTrue(parsed.items().isEmpty());
    }

    @Test
    public void preservesPositivePlacementAndUsesTheLiteralWinFallback() {
        Participant second = participant(2);
        second.subTeamPlacement = 2;
        second.augments = List.of();
        Participant third = participant(3);
        third.subTeamPlacement = 3;
        third.augments = List.of();
        Participant outsideExpectedRange = participant(9);
        outsideExpectedRange.subTeamPlacement = 9;
        outsideExpectedRange.augments = List.of();
        Participant wonAtFirstPlace = participant(1);
        wonAtFirstPlace.win = true;
        wonAtFirstPlace.augments = List.of();
        Participant missingPlacement = participant(0);
        missingPlacement.subTeamPlacement = 0;
        missingPlacement.augments = List.of();

        ParsedArenaGame secondResult = ArenaGameParser.parse(match(Map.of()), second);
        ParsedArenaGame thirdResult = ArenaGameParser.parse(match(Map.of()), third);
        ParsedArenaGame outsideResult = ArenaGameParser.parse(match(Map.of()), outsideExpectedRange);
        ParsedArenaGame wonResult = ArenaGameParser.parse(match(Map.of()), wonAtFirstPlace);
        ParsedArenaGame missingResult = ArenaGameParser.parse(match(Map.of()), missingPlacement);

        assertEquals(Integer.valueOf(2), secondResult.subTeamPlacement());
        assertEquals(false, secondResult.win());
        assertEquals(Integer.valueOf(3), thirdResult.subTeamPlacement());
        assertEquals(true, thirdResult.win());
        assertEquals(Integer.valueOf(9), outsideResult.subTeamPlacement());
        assertEquals(true, outsideResult.win());
        assertTrue(outsideResult.coverage().missingReasons().containsKey("PLACEMENT_INVALID_OR_ABSENT"));
        assertEquals(Integer.valueOf(1), wonResult.subTeamPlacement());
        assertEquals(true, wonResult.win());
        assertNull(missingResult.subTeamPlacement());
        assertTrue(missingResult.coverage().missingReasons().containsKey("PLACEMENT_INVALID_OR_ABSENT"));
    }

    @Test
    public void omitsEntirePostCoreSequenceWhenARelevantTransitionIsUnresolved() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220001, 1000, 2000, List.of(3006), List.of(
            item("ITEM_TRANSFORMED", 3000, 5001, 5000, 5001)
        )));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertTrue(parsed.items().isEmpty());
        assertTrue(parsed.coverage().ambiguousReasons().containsKey("POST_CORE_ITEM_TRANSITION_UNRESOLVED"));
    }

    @Test
    public void rejectsCoreWhenSnapshotPredatesFirstPrismatic() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220001, 3000, 2000, List.of(3006), List.of()));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertNull(parsed.core());
        assertTrue(parsed.coverage().ambiguousReasons().containsKey("CORE_SNAPSHOT_PRECEDES_FIRST_PRISMATIC"));
    }

    @Test
    public void rejectsCoreSnapshotWithMissingBootOrEmptyEffectiveItems() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match noBoot = match(exactEvents(220001, 1000, 2000, List.of(1001), List.of()));
        Match noItems = match(exactEvents(220001, 1000, 2000, List.of(220007), List.of()));

        ParsedArenaGame noBootResult = ArenaGameParser.parse(noBoot, participant);
        ParsedArenaGame noItemsResult = ArenaGameParser.parse(noItems, participant);

        assertNull(noBootResult.core());
        assertTrue(noBootResult.coverage().ambiguousReasons().containsKey("CORE_SNAPSHOT_BOOT_NOT_IN_ITEM_IDS"));
        assertNull(noItemsResult.core());
        assertTrue(noItemsResult.coverage().ambiguousReasons().containsKey("CORE_SNAPSHOT_ITEMS_EMPTY"));
    }

    @Test
    public void rejectsPurchaseTransformationWithDifferentBeforeAndAfterIds() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220001, 1000, 2000, List.of(3006), List.of(
            item("ITEM_PURCHASED", 3000, 6001, 6000, 6001)
        )));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertTrue(parsed.items().isEmpty());
        assertTrue(parsed.coverage().ambiguousReasons().containsKey("POST_CORE_ITEM_TRANSITION_UNRESOLVED"));
    }

    @Test
    public void rejectsCoreSaleAndRepurchaseAfterBoundary() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220001, 1000, 2000, List.of(3006), List.of(
            item("ITEM_SOLD", 3000, 3006, 3006, 0),
            item("ITEM_PURCHASED", 4000, 3006, 0, 0)
        )));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertTrue(parsed.items().isEmpty());
        assertTrue(parsed.coverage().ambiguousReasons().containsKey("POST_CORE_ITEM_TRANSITION_UNRESOLVED"));
    }

    @Test
    public void utilityNormalizationRemovesPostCoreItemsDestroyedLater() {
        Participant participant = participant(1);
        participant.augments = List.of();
        Match match = match(exactEvents(220001, 1000, 2000, List.of(3006), List.of(
            item("ITEM_PURCHASED", 3000, 4001, 0, 0),
            item("ITEM_DESTROYED", 4000, 4001, 4001, 0),
            item("ITEM_PURCHASED", 5000, 4002, 0, 0)
        )));

        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant);

        assertEquals(List.of(new ParsedArenaGame.Item(4002, 1, 5000)), parsed.items());
    }

    private static Participant participant(int placement) {
        Participant participant = new Participant();
        participant.id = 1;
        participant.puuid = "player-one";
        participant.subTeamPlacement = placement;
        return participant;
    }

    private static Match match(Map<String, Object> eventData) {
        Match match = new Match();
        match.eventData = eventData;
        return match;
    }

    private static Map<String, Object> exactEvents(int prismaticId, long prismaticTime, long snapshotTime,
            List<Integer> snapshotItems, List<Map<String, Object>> itemEvents) {
        return Map.of(
            "participants", Map.of("1", "player-one"),
            "item_events", itemEvents,
            "arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
                "first_prismatic", Map.of("status", "EXACT", "item_id", prismaticId, "timestamp", prismaticTime),
                "core_snapshot", Map.of("status", "EXACT", "timestamp", snapshotTime,
                    "boots_id", 3006, "item_ids", snapshotItems)
            )))
        );
    }

    private static Map<String, Object> item(String type, long timestamp, int id, int before, int after) {
        return Map.of("event", type, "participant", 1, "timestamp", timestamp,
            "item", id, "before", before, "after", after);
    }

    private static ParsedArenaGame.Core coreWithDifferentTimes(ParsedArenaGame.Core core) {
        return new ParsedArenaGame.Core(core.firstPrismaticId(), core.bootsId(), core.snapshotItemIds(),
            core.firstPrismaticTimestampMillis() + 5000, core.snapshotTimestampMillis() + 5000);
    }
}
