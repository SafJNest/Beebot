package com.safjnest.lol.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.arena.PrismaticItemClassifier.Classification;

public class FirstPrismaticResolverTest {

    private static final PrismaticItemClassifier CLASSIFIER = new PrismaticItemClassifier(Map.of(
        "15.10", Map.of(444636, Classification.PRISMATIC,
                600001, Classification.PRISMATIC, 600002, Classification.PRISMATIC)
    ));

    @Test
    public void usesOnlyKnownFinalPrismaticWithNoAnvil() {
        Participant participant = participant(444636, 447111, 3006, 0, 0, 0);

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10.1", participant, events(), CLASSIFIER);

        assertEquals(Integer.valueOf(444636), result.itemId());
        assertEquals(FirstPrismaticResolveType.SINGLE_CANDIDATE, result.type());
        assertEquals(List.of(444636), result.finalPrismatics());
        assertEquals(0, result.missingCount());
    }

    @Test
    public void excludes447111FromPrismaticClassification() {
        Participant participant = participant(447111, 900001, 0, 0, 0, 0);

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10.1", participant, events(), CLASSIFIER);

        assertEquals(FirstPrismaticResolveType.NOT_FOUND, result.type());
        assertNull(result.itemId());
        assertEquals(1, result.missingCount());
        assertFalse(new PrismaticItemClassifier(Map.of()).isPrismatic("15.10", 447111));
    }

    @Test
    public void deduplicatesDestroyAndPurchaseForAnvilUseAndUsesTooltipFallback() {
        Participant participant = participant(600001, 3006, 600002, 0, 0, 0);
        Map<String, Object> raw = events(
            item("ITEM_DESTROYED", 384137, 220007, 220007, 0),
            item("ITEM_PURCHASED", 384137, 220007, 0, 220007)
        );

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10", participant, raw, CLASSIFIER);

        assertEquals(1, result.anvilTimestamps().size());
        assertEquals(Long.valueOf(384137), result.anvilTimestamps().get(0));
        assertEquals(Integer.valueOf(600001), result.itemId());
        assertEquals(FirstPrismaticResolveType.TOOLTIP_FALLBACK, result.type());
    }

    @Test
    public void preAnvilSaleIdentifiesTheFreePrismatic() {
        Participant participant = participant(600002, 3006, 0, 0, 0, 0);
        Map<String, Object> raw = events(
            item("ITEM_SOLD", 300000, 600001, 600001, 0),
            item("ITEM_DESTROYED", 384137, 220007, 220007, 0),
            item("ITEM_PURCHASED", 384137, 220007, 0, 220007)
        );

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10", participant, raw, CLASSIFIER);

        assertEquals(Integer.valueOf(600001), result.itemId());
        assertEquals(FirstPrismaticResolveType.PRE_ANVIL_EVENT, result.type());
    }

    @Test
    public void accountingResolvesTheOnlyCandidateNotPurchasedAfterAnvil() {
        Participant participant = participant(600001, 3006, 600002, 0, 0, 0);
        Map<String, Object> raw = events(
            item("ITEM_DESTROYED", 384137, 220007, 220007, 0),
            item("ITEM_PURCHASED", 384137, 220007, 0, 220007),
            item("ITEM_PURCHASED", 500000, 600002, 0, 0)
        );

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10", participant, raw, CLASSIFIER);

        assertEquals(Integer.valueOf(600001), result.itemId());
        assertEquals(FirstPrismaticResolveType.ACCOUNTING, result.type());
    }

    @Test
    public void undoMarksThePrismaticSaleAsReversed() {
        Participant participant = participant(600002, 3006, 0, 0, 0, 0);
        Map<String, Object> raw = events(
            item("ITEM_SOLD", 300000, 600001, 600001, 0),
            item("ITEM_UNDO", 300001, 600001, 0, 600001)
        );

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10", participant, raw, CLASSIFIER);

        assertEquals(FirstPrismaticResolveType.PRE_ANVIL_EVENT, result.type());
        assertEquals(2, result.prismaticEvents().size());
        assertEquals(true, result.prismaticEvents().get(0).undone());
        assertEquals("ITEM_UNDO", result.prismaticEvents().get(1).eventType());
        assertEquals("ITEM_SOLD", result.prismaticEvents().get(1).undoTargetType());
    }

    @Test
    public void undoOfPrismaticPurchaseIsNotPossessionEvidence() {
        Participant participant = participant(3006, 0, 0, 0, 0, 0);
        Map<String, Object> raw = events(
            item("ITEM_PURCHASED", 300000, 600001, 0, 0),
            item("ITEM_UNDO", 300001, 600001, 0, 600001)
        );

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10", participant, raw, CLASSIFIER);

        assertEquals(FirstPrismaticResolveType.NOT_FOUND, result.type());
        assertNull(result.itemId());
    }

    @Test
    public void undoWithoutItemIdentityIsCountedAsAmbiguous() {
        Participant participant = participant(3006, 0, 0, 0, 0, 0);
        Map<String, Object> raw = events(
            item("ITEM_PURCHASED", 300000, 600001, 0, 0),
            item("ITEM_UNDO", 300001, 0, 0, 0)
        );

        FirstPrismaticResult result = FirstPrismaticResolver.resolve("15.10", participant, raw, CLASSIFIER);

        assertEquals(FirstPrismaticResolveType.SINGLE_CANDIDATE, result.type());
        assertEquals(1, result.ambiguousCount());
        assertEquals(Integer.valueOf(600001), result.itemId());
    }

    private static Participant participant(int item0, int item1, int item2, int item3, int item4, int item5) {
        Participant participant = new Participant();
        participant.id = 4;
        participant.puuid = "puuid-4";
        participant.item0 = item0;
        participant.item1 = item1;
        participant.item2 = item2;
        participant.item3 = item3;
        participant.item4 = item4;
        participant.item5 = item5;
        return participant;
    }

    private static Map<String, Object> events(Map<String, Object>... items) {
        return Map.of("participants", Map.of("4", "puuid-4"), "item_events", List.of(items));
    }

    private static Map<String, Object> item(String type, long timestamp, int item, int before, int after) {
        return Map.of("event", type, "timestamp", timestamp, "participant", 4,
                "item", item, "before", before, "after", after);
    }
}
