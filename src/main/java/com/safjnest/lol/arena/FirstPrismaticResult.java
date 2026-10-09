package com.safjnest.lol.arena;

import java.util.List;

public record FirstPrismaticResult(
        Integer itemId,
        FirstPrismaticResolveType type,
        String reason,
        List<FinalItem> finalItems,
        List<Integer> finalPrismatics,
        List<Long> anvilTimestamps,
        List<PrismaticEvent> prismaticEvents,
        int missingCount,
        int ambiguousCount) {

    public FirstPrismaticResult {
        reason = reason == null ? "" : reason;
        finalItems = finalItems == null ? List.of() : List.copyOf(finalItems);
        finalPrismatics = finalPrismatics == null ? List.of() : List.copyOf(finalPrismatics);
        anvilTimestamps = anvilTimestamps == null ? List.of() : List.copyOf(anvilTimestamps);
        prismaticEvents = prismaticEvents == null ? List.of() : List.copyOf(prismaticEvents);
    }

    public record FinalItem(int slot, int itemId, PrismaticItemClassifier.Classification classification) {}

    public record PrismaticEvent(long timestampMillis, int itemId, String eventType,
            Integer beforeId, Integer afterId, String undoTargetType, boolean undone) {}
}
