package com.safjnest.lol.arena;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.arena.ParsedArenaGame.OrderQuality;
import com.safjnest.lol.arena.ParsedArenaGame.OrderSource;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ArenaGameParserTest {

    public static final ArenaItemCatalog CATALOG = new ArenaItemCatalog(
        Map.of(3006, Kind.BOOTS, 4001, Kind.ITEM, 4002, Kind.ITEM, 4003, Kind.ITEM, 447111, Kind.ITEM),
        new PrismaticItemClassifier(Map.of()));

    @Test
    public void excludesAnvilFromEquipmentAndKeepsAwardSeparate() {
        ParsedArenaGame parsed = parse(List.of(
            event("ITEM_PURCHASED", 3000, 447001), event("ITEM_PURCHASED", 2000, 220007),
            event("ITEM_PURCHASED", 1000, 4001), event("ITEM_DESTROYED", 2000, 220007)));
        assertEquals(List.of(4001, 447001), ids(parsed));
        assertNull(parsed.firstPrismaticId());
        assertEquals(List.of(2000L), parsed.firstPrismatic().anvilTimestamps());
        assertFalse(parsed.choices().stream().anyMatch(c -> c.id() == 220007));
    }

    @Test
    public void timelineOrdersAllSixPrismaticsAndOverridesTooltip() {
        Participant participant = participant(1, 2);
        participant.item0 = 447006;
        participant.item1 = 447001;
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 6; i >= 1; i--) events.add(event("ITEM_PURCHASED", i * 1000, 447000 + i));
        ParsedArenaGame parsed = ArenaGameParser.parse(match("EUW1_1", participant, events), participant, CATALOG, true);
        assertEquals(Integer.valueOf(447001), parsed.firstPrismaticId());
        assertEquals(FirstPrismaticResolveType.TOOLTIP_FALLBACK, parsed.firstPrismatic().type());
        assertEquals(6, parsed.equipment().size());
        for (int i = 0; i < 6; i++) {
            var item = parsed.equipment().get(i);
            assertEquals(447001 + i, item.id());
            assertEquals(Integer.valueOf(i + 1), item.position());
            assertEquals(Integer.valueOf(i + 1), item.typePosition());
            assertEquals(OrderSource.TIMELINE, item.orderSource());
        }
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_RESOLVER_CONFLICT"));
    }

    @Test
    public void resolverUsesPreAnvilPossessionAndPreservesUnknownPurchaseTime() {
        Participant p = participant(1, 3);
        p.item0 = 447002;
        var parsed = ArenaGameParser.parse(match("EUW1_1", p, List.of(event("ITEM_SOLD", 1000, 447001),
            event("ITEM_PURCHASED", 2000, 220007), event("ITEM_DESTROYED", 2000, 220007),
            event("ITEM_PURCHASED", 3000, 447002))), p, CATALOG, true);
        assertEquals(FirstPrismaticResolveType.PRE_ANVIL_EVENT, parsed.firstPrismatic().type());
        assertEquals(Integer.valueOf(447001), parsed.firstPrismaticId());
        assertEquals(List.of(447001, 447002), ids(parsed));
        assertNull(parsed.equipment().get(0).timestampMillis());
        assertEquals(OrderSource.FIRST_PRISMATIC_RESOLVER, parsed.equipment().get(0).orderSource());
    }

    @Test
    public void absentTimelineUsesLabelledTooltipFallbackAndKeepsMetadata() {
        Participant p = participant(1, 2);
        p.item0 = 447002;
        p.item1 = 4001;
        p.item2 = 447001;
        p.boots = 3006;
        Match match = match("EUW1_1", p, List.of());
        match.eventData = null;
        var parsed = ArenaGameParser.parse(match, p, CATALOG, false);
        assertEquals(FirstPrismaticResolveType.TOOLTIP_FALLBACK, parsed.firstPrismatic().type());
        assertEquals(OrderQuality.FALLBACK, parsed.orderQuality());
        assertTrue(parsed.equipmentComplete());
        assertEquals(Integer.valueOf(447002), parsed.firstPrismaticId());
        assertEquals(List.of(447002, 4001, 447001), ids(parsed));
        assertEquals(Integer.valueOf(0), parsed.equipment().get(0).tooltipSlot());
        assertEquals(OrderSource.TOOLTIP_FALLBACK, parsed.equipment().get(1).orderSource());
        assertNull(parsed.equipment().get(1).timestampMillis());
    }

    @Test
    public void partialOrderKeepsUntimedPrismaticBetweenTimedLegendaryItems() {
        Participant p = tooltipPath();
        var parsed = ArenaGameParser.parse(match("EUW1_1", p, List.of(
            event("ITEM_PURCHASED", 1000, 4001), event("ITEM_PURCHASED", 3000, 4003))), p, CATALOG, true);
        assertEquals(List.of(447001, 4001, 447002, 4003), ids(parsed));
        assertEquals(OrderQuality.MIXED, parsed.orderQuality());
        assertEquals(Integer.valueOf(2), parsed.equipment().get(2).typePosition());
        assertEquals(Integer.valueOf(2), parsed.equipment().get(3).typePosition());
    }

    @Test
    public void partialOrderOverridesTooltipWhereTimelineRequiresIt() {
        Participant p = tooltipPath();
        var parsed = ArenaGameParser.parse(match("EUW1_1", p, List.of(
            event("ITEM_PURCHASED", 1000, 4001), event("ITEM_PURCHASED", 2000, 4003),
            event("ITEM_PURCHASED", 3000, 447002))), p, CATALOG, true);
        assertEquals(List.of(447001, 4001, 4003, 447002), ids(parsed));
        assertEquals(Integer.valueOf(2), parsed.equipment().get(2).typePosition());
        assertEquals(Integer.valueOf(4), parsed.equipment().get(3).position());
    }

    @Test
    public void equipmentAndAugmentsHaveIndependentPathsWithGaps() {
        Participant p = tooltipPath();
        p.augments = List.of(11, 0, 33, 44, 55, 66);
        var parsed = ArenaGameParser.parse(match("EUW1_1", p, List.of()), p, CATALOG, false);
        assertEquals(List.of(1, 3, 4, 5, 6), parsed.augments().stream().map(ParsedArenaGame.Augment::position).toList());
        assertEquals(4, parsed.equipment().size());
        for (var choice : choices(parsed, Kind.AUGMENT)) assertNull(choice.timestampMillis());
    }

    @Test
    public void salesAndDestructionPreserveCommittedHistory() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_SOLD", 2000, 4001), event("ITEM_PURCHASED", 3000, 4002),
            event("ITEM_DESTROYED", 4000, 4002)));
        assertEquals(List.of(4001, 4002), ids(parsed));
    }

    @Test
    public void identifiedUndoCancelsPurchaseButNotHistoricalSale() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 2000, 4002), event("ITEM_UNDO", 2001, 4002),
            event("ITEM_SOLD", 3000, 4001), event("ITEM_UNDO", 3001, 4001),
            event("ITEM_DESTROYED", 4000, 4001), event("ITEM_UNDO", 4001, 4001)));
        assertEquals(List.of(4001), ids(parsed));
        assertEquals(1, choices(parsed, Kind.ITEM).size());
    }

    @Test
    public void cancelledPrismaticDoesNotReachResolverOrChoices() {
        var parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 447001), event("ITEM_UNDO", 1001, 447001)));
        assertNull(parsed.firstPrismaticId());
        assertEquals(FirstPrismaticResolveType.NOT_FOUND, parsed.firstPrismatic().type());
        assertTrue(parsed.equipment().isEmpty());
    }

    @Test
    public void undoOfAnvilPairRemovesBothEvidenceEvents() {
        Participant p = participant(1, 3);
        p.item0 = 447001;
        var parsed = ArenaGameParser.parse(match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 220007),
            event("ITEM_DESTROYED", 1000, 220007), event("ITEM_UNDO", 1001, 220007))), p, CATALOG, true);
        assertTrue(parsed.firstPrismatic().anvilTimestamps().isEmpty());
        assertEquals(FirstPrismaticResolveType.SINGLE_CANDIDATE, parsed.firstPrismatic().type());
    }

    @Test
    public void ambiguousUndoOmitsPotentialPurchaseWithoutGuessingOrder() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 2000, 4002), event("ITEM_UNDO", 2001, 0)));
        assertFalse(parsed.equipmentComplete());
        assertEquals(List.of(4001), ids(parsed));
        assertNull(parsed.equipment().get(0).position());
        assertEquals(Long.valueOf(1), parsed.coverage().ambiguous().get("UNDO_TARGET_AMBIGUOUS"));
    }

    @Test
    public void transformationUsesOnlyExplicitPurchaseAfterId() {
        Map<String, Object> transformed = new HashMap<>(event("ITEM_PURCHASED", 2000, 4002));
        transformed.put("before", 4001);
        transformed.put("after", 4002);
        assertEquals(List.of(4001, 4002), ids(parse(List.of(event("ITEM_PURCHASED", 1000, 4001), transformed))));
        transformed.put("item", 4003);
        ParsedArenaGame conflict = parse(List.of(transformed));
        assertTrue(conflict.choices().isEmpty());
        assertTrue(conflict.coverage().ambiguous().containsKey("PURCHASE_ID_CONFLICT_OR_ABSENT"));
    }

    @Test
    public void unsupportedTransformationCannotInventAnAcquisition() {
        var parsed = parse(List.of(event("ITEM_TRANSFORMED", 1000, 4001)));
        assertTrue(parsed.choices().isEmpty());
        assertTrue(parsed.coverage().ambiguous().containsKey("ITEM_TRANSITION_UNSUPPORTED_OR_INVALID"));
    }

    @Test
    public void repeatedPurchasesAndExactDuplicateEventsRetainEarliestEvidenceOnce() {
        var first = event("ITEM_PURCHASED", 1000, 4001);
        ParsedArenaGame parsed = parse(List.of(first, first, event("ITEM_PURCHASED", 2000, 4001),
            event("ITEM_PURCHASED", 3000, 4002)));
        assertEquals(List.of(4001, 4002), ids(parsed));
        assertEquals(Long.valueOf(1), parsed.coverage().rejected().get("DUPLICATE_ITEM_EVENT"));
        assertEquals(Long.valueOf(1000), choices(parsed, Kind.ITEM).get(0).timestampMillis());
    }

    @Test
    public void participantZeroAndMissingActorsCannotBypassResolverAttribution() {
        Participant participant = participant(1, 2);
        var zero = new HashMap<>(event("ITEM_SOLD", 1000, 447001));
        zero.put("participant", 0);
        var absent = new HashMap<>(event("ITEM_PURCHASED", 2000, 4001));
        absent.remove("participant");
        Match match = match("EUW1_1", participant, List.of(zero, absent));
        match.eventData.put("participants", Map.of("0", participant.puuid, "1", participant.puuid));
        ParsedArenaGame parsed = ArenaGameParser.parse(match, participant, CATALOG, true);
        assertTrue(parsed.choices().isEmpty());
        assertNull(parsed.firstPrismaticId());
        assertEquals(Long.valueOf(1), parsed.coverage().rejected().get("PARTICIPANT_ZERO"));
    }

    @Test
    public void conflictingParticipantIdZeroIsRejected() {
        var source = new HashMap<>(event("ITEM_PURCHASED", 1000, 447001));
        source.put("participantId", 0);
        var parsed = parse(List.of(source));
        assertTrue(parsed.choices().isEmpty());
        assertNull(parsed.firstPrismaticId());
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
    public void directPositiveParticipantIdWorksWithoutReferenceMap() {
        Participant p = participant(1, 3);
        Match match = match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 447001)));
        match.eventData.remove("participants");
        var parsed = ArenaGameParser.parse(match, p, CATALOG, true);
        assertEquals(Integer.valueOf(447001), parsed.firstPrismaticId());
    }

    @Test
    public void partialTimelineKeepsValidTimesAndFallbackPositions() {
        Participant participant = tooltipPath();
        var parsed = ArenaGameParser.parse(match("EUW1_1", participant,
            List.of(event("ITEM_PURCHASED", 1000, 4001))), participant, CATALOG, false);
        assertTrue(parsed.equipmentComplete());
        assertEquals(OrderQuality.MIXED, parsed.orderQuality());
        assertEquals(Long.valueOf(1000), choices(parsed, Kind.ITEM).get(0).timestampMillis());
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.ITEM).get(0).position());
    }

    @Test
    public void missingTimeKeepsUnpositionedPurchaseMembership() {
        var source = new HashMap<>(event("ITEM_PURCHASED", 1000, 447001));
        source.remove("timestamp");
        ParsedArenaGame parsed = parse(List.of(source));
        assertEquals(1, parsed.choices().size());
        assertNull(parsed.choices().get(0).position());
        assertNull(parsed.choices().get(0).timestampMillis());
    }

    @Test
    public void directFirstEvidenceContributesWithoutTimeOrFrontendConcepts() {
        Participant p = participant(1, 3);
        Match match = sidecar(p, 447001, null, List.of());
        var parsed = ArenaGameParser.parse(match, p, CATALOG, false);
        assertEquals(Integer.valueOf(447001), parsed.firstPrismaticId());
        assertEquals(Integer.valueOf(1), choices(parsed, Kind.PRISMATIC).get(0).position());
        assertNull(choices(parsed, Kind.PRISMATIC).get(0).timestampMillis());
    }

    @Test
    public void exactSidecarAndLaterTimelinePrismaticReconstructTwoPositions() {
        Participant p = participant(1, 3);
        var parsed = ArenaGameParser.parse(sidecar(p, 447001, 1000L,
            List.of(event("ITEM_PURCHASED", 2000, 447002))), p, CATALOG, true);
        assertEquals(List.of(447001, 447002), ids(parsed));
        assertEquals(Integer.valueOf(2), choices(parsed, Kind.PRISMATIC).get(1).position());
    }

    @Test
    public void unknownCatalogDoesNotShiftLaterPositionsAnd447111IsNormalItem() {
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 99999),
            event("ITEM_PURCHASED", 2000, 447001), event("ITEM_PURCHASED", 3000, 447111)));
        assertFalse(parsed.equipmentComplete());
        assertNull(choices(parsed, Kind.PRISMATIC).get(0).position());
        assertEquals(447111, choices(parsed, Kind.ITEM).get(0).id());
    }

    @Test
    public void exactSidecarCannotBypassDuplicateParticipantReferences() {
        Participant p = participant(1, 3);
        Match match = sidecar(p, 447001, 1000L, List.of());
        match.eventData.put("participants", Map.of("1", p.puuid, "2", p.puuid));
        var parsed = ArenaGameParser.parse(match, p, CATALOG, true);
        assertTrue(parsed.choices().isEmpty());
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_UNATTRIBUTABLE"));
    }

    @Test
    public void conflictingSidecarDoesNotOverwriteTimeline() {
        Participant p = participant(1, 3);
        var parsed = ArenaGameParser.parse(sidecar(p, 447002, 3000L,
            List.of(event("ITEM_PURCHASED", 1000, 447001))), p, CATALOG, true);
        assertEquals(List.of(447001), ids(parsed));
        assertEquals(Integer.valueOf(447001), parsed.firstPrismaticId());
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_EVIDENCE_CONFLICT"));
    }

    @Test
    public void untimedUndoCannotEraseUnrelatedIndependentPurchase() {
        var undo = new HashMap<>(event("ITEM_UNDO", 1500, 4001));
        undo.remove("timestamp");
        ParsedArenaGame parsed = parse(List.of(event("ITEM_PURCHASED", 1000, 4001), undo,
            event("ITEM_PURCHASED", 2000, 4002)));
        assertEquals(List.of(4002), ids(parsed));
        assertNull(choices(parsed, Kind.ITEM).get(0).position());
    }

    @Test
    public void invalidDirectEvidenceCannotCreatePrismaticChoices() {
        Participant p = participant(1, 3);
        var parsed = ArenaGameParser.parse(sidecar(p, 220007, 1000L, List.of()), p, CATALOG, true);
        assertTrue(choices(parsed, Kind.PRISMATIC).isEmpty());
        assertTrue(parsed.coverage().rejected().containsKey("FIRST_PRISMATIC_ID_INVALID"));
    }

    @Test
    public void untimedSidecarKeepsExistingAcquisitionTime() {
        Participant p = participant(1, 3);
        var parsed = ArenaGameParser.parse(sidecar(p, 447001, null,
            List.of(event("ITEM_PURCHASED", 1000, 447001))), p, CATALOG, true);
        assertEquals(1, choices(parsed, Kind.PRISMATIC).size());
        assertEquals(Long.valueOf(1000), choices(parsed, Kind.PRISMATIC).get(0).timestampMillis());
    }

    @Test
    public void untimedIdentityFreeUndoSuppressesHistory() {
        var undo = new HashMap<>(event("ITEM_UNDO", 1000, 0));
        undo.remove("timestamp");
        var purchase = new HashMap<>(event("ITEM_PURCHASED", 2000, 4001));
        purchase.remove("timestamp");
        assertTrue(parse(List.of(undo, purchase)).choices().isEmpty());
    }

    @Test
    public void cancelledBootCannotBeResurrectedByDerivedBootField() {
        Participant p = participant(1, 3);
        p.boots = 3006;
        Match match = match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 3006), event("ITEM_UNDO", 1001, 3006)));
        assertTrue(choices(ArenaGameParser.parse(match, p, CATALOG, true), Kind.BOOTS).isEmpty());
        p.item0 = 3006;
        assertEquals(1, choices(ArenaGameParser.parse(match, p, CATALOG, true), Kind.BOOTS).size());
        p.item0 = 0;
        match.eventData = null;
        assertNull(choices(ArenaGameParser.parse(match, p, CATALOG, true), Kind.BOOTS).get(0).timestampMillis());
    }

    @Test
    public void soldBootAndRepurchaseAfterUndoCountOnce() {
        Participant p = participant(1, 3);
        p.boots = 3006;
        Match sold = match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 3006), event("ITEM_SOLD", 2000, 3006)));
        assertEquals(1, choices(ArenaGameParser.parse(sold, p, CATALOG, true), Kind.BOOTS).size());
        Match again = match("EUW1_2", p, List.of(event("ITEM_PURCHASED", 1000, 3006),
            event("ITEM_UNDO", 1001, 3006), event("ITEM_PURCHASED", 2000, 3006)));
        assertEquals(Long.valueOf(2000), choices(ArenaGameParser.parse(again, p, CATALOG, true), Kind.BOOTS).get(0).timestampMillis());
    }

    @Test
    public void equalTimelineTimestampsUseEventOrderBeforeTooltip() {
        Participant p = participant(1, 3);
        p.item0 = 4002;
        p.item1 = 4001;
        var parsed = ArenaGameParser.parse(match("EUW1_1", p, List.of(event("ITEM_PURCHASED", 1000, 4001),
            event("ITEM_PURCHASED", 1000, 4002))), p, CATALOG, true);
        assertEquals(List.of(4001, 4002), ids(parsed));
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

    public static Participant tooltipPath() {
        Participant p = participant(1, 3);
        p.item0 = 447001; p.item1 = 4001; p.item2 = 447002; p.item3 = 4003;
        return p;
    }

    private static Match sidecar(Participant p, int id, Long time, List<Map<String, Object>> events) {
        Match match = match("EUW1_1", p, events);
        Map<String, Object> evidence = new HashMap<>(Map.of("status", "EXACT", "item_id", id));
        if (time != null) evidence.put("timestamp", time);
        match.eventData.put("arena_evidence", Map.of("version", 1, "participants", Map.of("1", Map.of("first_prismatic", evidence))));
        return match;
    }

    private static ParsedArenaGame parse(List<Map<String, Object>> events) {
        Participant participant = participant(1, 3);
        return ArenaGameParser.parse(match("EUW1_1", participant, events), participant, CATALOG, true);
    }

    private static List<Integer> ids(ParsedArenaGame game) {
        List<Integer> result = new ArrayList<>();
        for (var item : game.equipment()) result.add(item.id());
        return result;
    }

    private static List<ParsedArenaGame.Observation> choices(ParsedArenaGame game, Kind kind) {
        List<ParsedArenaGame.Observation> result = new ArrayList<>();
        for (var choice : game.choices()) if (choice.kind() == kind) result.add(choice);
        return result;
    }
}
