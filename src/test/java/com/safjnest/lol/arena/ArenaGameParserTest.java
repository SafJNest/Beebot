package com.safjnest.lol.arena;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ArenaGameParserTest {

    public static final ArenaItemCatalog CATALOG = new ArenaItemCatalog(
        Map.of(3006, Kind.BOOTS, 4001, Kind.ITEM, 4002, Kind.ITEM, 4003, Kind.ITEM, 447111, Kind.ITEM),
        new PrismaticItemClassifier(Map.of()));

    @Test
    public void preservesOrderedPurchasesAndAnvilSeparatelyFromAward() {
        ParsedArenaGame parsed = parse(List.of(
            event("ITEM_PURCHASED", 3000, 447001), event("ITEM_PURCHASED", 2000, 220007),
            event("ITEM_PURCHASED", 1000, 4001), event("ITEM_DESTROYED", 2001, 220007)));
        assertEquals(List.of(4001, 220007), parsed.core().itemIds());
        assertEquals(1, choices(parsed, Kind.PRISMATIC).size());
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.PRISMATIC).get(0).position());
        assertEquals(447001, choices(parsed, Kind.PRISMATIC).get(0).id());
    }

    @Test
    public void doesNotUseFinalSlotOrderForPrismatics() {
        Participant participant = participant(1, 2);
        participant.item0 = 447006;
        participant.item1 = 447001;
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 6; i >= 1; i--) events.add(event("ITEM_PURCHASED", i * 1000, 447000 + i));
        ParsedArenaGame parsed = ArenaGameParser.parse(match("EUW1_1", participant, events), participant, CATALOG, true);
        var choices = choices(parsed, Kind.PRISMATIC);
        assertEquals(6, choices.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(447001 + i, choices.get(i).id());
            assertEquals(Integer.valueOf(i + 1), choices.get(i).position());
        }
    }

    @Test
    public void salesAndDestructionPreserveCommittedHistory() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_SOLD", 2000, 4001), event("ITEM_PURCHASED", 3000, 4002),
            event("ITEM_DESTROYED", 4000, 4002)));
        assertEquals(List.of(4001, 4002), parsed.core().itemIds());
    }

    @Test
    public void identifiedUndoCancelsPurchaseButNotHistoricalSale() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 2000, 4002), event("ITEM_UNDO", 2001, 4002),
            event("ITEM_SOLD", 3000, 4001), event("ITEM_UNDO", 3001, 4001),
            event("ITEM_DESTROYED", 4000, 4001), event("ITEM_UNDO", 4001, 4001)));
        assertEquals(List.of(4001), parsed.core().itemIds());
        assertEquals(1, choices(parsed, Kind.ITEM).size());
    }

    @Test
    public void ambiguousUndoOmitsPotentialPurchaseAndDisablesChronology() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 2000, 4002), event("ITEM_UNDO", 2001, 0)));
        assertNull(parsed.core());
        assertEquals(1, choices(parsed, Kind.ITEM).size());
        assertNull(choices(parsed, Kind.ITEM).get(0).position());
        assertEquals(Long.valueOf(1), parsed.coverage().ambiguous().get("UNDO_TARGET_AMBIGUOUS"));
    }

    @Test
    public void transformationUsesOnlyExplicitPurchaseAfterId() {
        Map<String, Object> transformed = new HashMap<>(event("ITEM_PURCHASED", 2000, 4002));
        transformed.put("before", 4001);
        transformed.put("after", 4002);
        assertEquals(List.of(4001, 4002), parse(List.of(event("ITEM_PURCHASED", 1000, 4001), transformed)).core().itemIds());
        transformed.put("item", 4003);
        ParsedArenaGame conflict = parse(List.of(transformed));
        assertNull(conflict.core());
        assertTrue(conflict.choices().isEmpty());
        assertTrue(conflict.coverage().ambiguous().containsKey("PURCHASE_ID_CONFLICT_OR_ABSENT"));
    }

    @Test
    public void repeatedItemAndExactDuplicateEventsCountOnce() {
        var first = event("ITEM_PURCHASED", 1000, 4001);
        ParsedArenaGame parsed = parse(List.of(first, first, event("ITEM_PURCHASED", 2000, 4001),
            event("ITEM_PURCHASED", 3000, 4002)));
        assertEquals(List.of(4001, 4002), parsed.core().itemIds());
        assertEquals(Long.valueOf(1), parsed.coverage().rejected().get("DUPLICATE_ITEM_EVENT"));
        assertEquals(Long.valueOf(1000), choices(parsed, Kind.ITEM).get(0).timestampMillis());
    }

    @Test
    public void participantZeroAndMissingActorsNeverSupplyChoices() {
        Participant participant = participant(1, 2);
        var zero = new HashMap<>(event("ITEM_PURCHASED", 1000, 447001));
        zero.put("participant", 0);
        var absent = new HashMap<>(event("ITEM_PURCHASED", 2000, 4001));
        absent.remove("participant");
        Match match = match("EUW1_1", participant, List.of(zero, absent));
        match.eventData.put("participants", Map.of("0", participant.puuid, "1", participant.puuid));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertTrue(parsed.choices().isEmpty());
        assertNull(parsed.core());
        assertEquals(Long.valueOf(1), parsed.coverage().rejected().get("PARTICIPANT_ZERO"));
    }

    @Test
    public void conflictingAndDuplicateReferencesAreUnattributable() {
        Participant participant = participant(1, 2);
        Match match = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 4001)));
        match.eventData.put("participants", Map.of("1", "other"));
        assertTrue(ArenaGameParser.parse(match, participant, CATALOG, true).choices().isEmpty());
        match.eventData.put("participants", Map.of("1", participant.puuid, "2", participant.puuid));
        assertTrue(ArenaGameParser.parse(match, participant, CATALOG, true).choices().isEmpty());
    }

    @Test
    public void absentAndPartialTimelinesKeepIndependentMembershipAndAugmentGaps() {
        Participant participant = participant(1, 0);
        participant.item0 = 4001;
        participant.item1 = 447002;
        participant.boots = 3006;
        participant.augments = List.of(11, 0, 33);
        Match match = match("EUW1_1", participant, List.of());
        match.eventData = null;
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertNull(parsed.core());
        assertEquals(5, parsed.choices().size());
        assertNull(choices(parsed, Kind.PRISMATIC).get(0).position());
        assertNull(choices(parsed, Kind.BOOTS).get(0).timestampMillis());
        assertEquals(Integer.valueOf(3), choices(parsed, Kind.AUGMENT).get(1).position());
        match = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 4001)));
        parsed = ArenaGameParser.parse(match, participant, CATALOG, false);
        assertNull(parsed.core());
        assertNull(choices(parsed, Kind.ITEM).get(0).position());
        assertEquals(Long.valueOf(1000), choices(parsed, Kind.ITEM).get(0).timestampMillis());
    }

    @Test
    public void missingTimeKeepsChoiceWithoutCoreOrPosition() {
        var event = new HashMap<>(event("ITEM_PURCHASED", 1000, 447001));
        event.remove("timestamp");
        ParsedArenaGame parsed = parse(List.of(event));
        assertEquals(1, parsed.choices().size());
        assertNull(parsed.choices().get(0).position());
        assertNull(parsed.choices().get(0).timestampMillis());
    }

    @Test
    public void directFirstEvidenceCanContributeWithoutTimeAndDoesNotInventCore() {
        Participant participant = participant(1, 3);
        Match match = match("EUW1_1", participant, List.of());
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "EXACT", "item_id", 447001)))));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, false);
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.PRISMATIC).get(0).position());
        assertNull(choices(parsed, Kind.PRISMATIC).get(0).timestampMillis());
        assertNull(parsed.core());
    }

    @Test
    public void exactFirstPrismaticShiftsOnlyLaterObservedPrismatics() {
        Participant participant = participant(1, 3);
        Match match = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 2000, 447002)));
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "EXACT", "item_id", 447001, "timestamp", 1000)))));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertEquals(Integer.valueOf(2), choices(parsed, Kind.PRISMATIC).get(0).position());
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.PRISMATIC).get(1).position());
    }

    @Test
    public void unknownCatalogDoesNotShiftLaterPositionsAnd447111IsNotPrismatic() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 99999),
            event("ITEM_PURCHASED", 2000, 447001), event("ITEM_PURCHASED", 3000, 447111)));
        assertNull(parsed.core());
        assertNull(choices(parsed, Kind.PRISMATIC).get(0).position());
        assertEquals(447111, choices(parsed, Kind.ITEM).get(0).id());
    }

    @Test
    public void exactSidecarCannotBypassDuplicateParticipantReferences() {
        Participant participant = participant(1, 3);
        Match match = match("EUW1_1", participant, List.of());
        match.eventData.put("participants", Map.of("1", participant.puuid, "2", participant.puuid));
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "EXACT", "item_id", 447001, "timestamp", 1000)))));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertTrue(parsed.choices().isEmpty());
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_UNATTRIBUTABLE"));
    }

    @Test
    public void conflictingExactSidecarDoesNotOverwriteObservedChronology() {
        Participant participant = participant(1, 3);
        Match match = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 447001)));
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "EXACT", "item_id", 447002, "timestamp", 3000)))));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertEquals(1, choices(parsed, Kind.PRISMATIC).size());
        assertEquals(447001, choices(parsed, Kind.PRISMATIC).get(0).id());
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.PRISMATIC).get(0).position());
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_EVIDENCE_CONFLICT"));
    }

    @Test
    public void untimedUndoCannotEraseUnrelatedIndependentPurchase() {
        var undo = new HashMap<>(event("ITEM_UNDO", 1500, 4001));
        undo.remove("timestamp");
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001), undo,
            event("ITEM_PURCHASED", 2000, 4002)));
        assertNull(parsed.core());
        assertEquals(1, choices(parsed, Kind.ITEM).size());
        assertEquals(4002, choices(parsed, Kind.ITEM).get(0).id());
        assertNull(choices(parsed, Kind.ITEM).get(0).position());
    }

    @Test
    public void unknownAndInvalidDirectEvidenceCannotCreatePrismaticChoices() {
        Participant participant = participant(1, 3);
        Match match = match("EUW1_1", participant, List.of());
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "UNKNOWN")))));
        assertTrue(choices(ArenaGameParser.parse(match, participant, CATALOG, true), Kind.PRISMATIC).isEmpty());
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "EXACT", "item_id", 220007, "timestamp", 1000)))));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertTrue(choices(parsed, Kind.PRISMATIC).isEmpty());
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_ID_INVALID"));
    }

    @Test
    public void untimedExactSidecarKeepsAlreadyObservedFirstAcquisitionTime() {
        Participant participant = participant(1, 3);
        Match match = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 447001)));
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of(
            "first_prismatic", Map.of("status", "EXACT", "item_id", 447001)))));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertEquals(1, choices(parsed, Kind.PRISMATIC).size());
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.PRISMATIC).get(0).position());
        assertEquals(Long.valueOf(1000), choices(parsed, Kind.PRISMATIC).get(0).timestampMillis());
    }

    @Test
    public void untimedIdentityFreeUndoSuppressesTheWholeAcquisitionHistory() {
        var undo = new HashMap<>(event("ITEM_UNDO", 1000, 0));
        undo.remove("timestamp");
        var purchase = new HashMap<>(event("ITEM_PURCHASED", 2000, 4001));
        purchase.remove("timestamp");
        ParsedArenaGame parsed = parse(List.of(undo, purchase));
        assertTrue(parsed.choices().isEmpty());
        assertNull(parsed.core());
    }

    @Test
    public void cancelledBootCannotBeResurrectedByLegacyDerivedBootField() {
        Participant participant = participant(1, 3);
        participant.boots = 3006;
        Match match = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 3006),
            event("ITEM_UNDO", 1001, 3006)));
        assertTrue(choices(ArenaGameParser.parse(match, participant, CATALOG, true), Kind.BOOTS).isEmpty());
        participant.item0 = 3006;
        assertEquals(1, choices(ArenaGameParser.parse(match, participant, CATALOG, true), Kind.BOOTS).size());
        participant.item0 = 0;
        match.eventData = null;
        var legacy = choices(ArenaGameParser.parse(match, participant, CATALOG, true), Kind.BOOTS);
        assertEquals(1, legacy.size());
        assertNull(legacy.get(0).timestampMillis());
    }

    @Test
    public void soldBootRemainsHistoricalAndRepurchaseAfterUndoCountsOnce() {
        Participant participant = participant(1, 3);
        participant.boots = 3006;
        Match sold = match("EUW1_1", participant, List.of(event("ITEM_PURCHASED", 1000, 3006),
            event("ITEM_SOLD", 2000, 3006)));
        assertEquals(1, choices(ArenaGameParser.parse(sold, participant, CATALOG, true), Kind.BOOTS).size());
        Match repurchased = match("EUW1_2", participant, List.of(event("ITEM_PURCHASED", 1000, 3006),
            event("ITEM_UNDO", 1001, 3006), event("ITEM_PURCHASED", 2000, 3006)));
        var boots = choices(ArenaGameParser.parse(repurchased, participant, CATALOG, true), Kind.BOOTS);
        assertEquals(1, boots.size());
        assertEquals(Long.valueOf(2000), boots.get(0).timestampMillis());
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
        return Map.of("event", type, "participant", 1, "timestamp", time, "item", item, "before", 0, "after", 0);
    }

    private static ParsedArenaGame parse(List<Map<String, Object>> events) {
        Participant participant = participant(1, 3);
        return ArenaGameParser.parse(match("EUW1_1", participant, events), participant, CATALOG, true);
    }

    private static List<ParsedArenaGame.Observation> choices(ParsedArenaGame game, Kind kind) {
        List<ParsedArenaGame.Observation> result = new ArrayList<>();
        for (var choice : game.choices()) if (choice.kind() == kind) result.add(choice);
        return result;
    }
}
