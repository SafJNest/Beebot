package com.safjnest.lol.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.arena.FirstPrismaticResult.FinalItem;
import com.safjnest.lol.arena.FirstPrismaticResult.PrismaticEvent;
import com.safjnest.lol.arena.PrismaticItemClassifier.Classification;
import com.safjnest.lol.model.match.Participant;

public final class FirstPrismaticResolver {

    private static final int PRISMATIC_ANVIL_ID = 220007;
    private static final int FINAL_ITEM_SLOTS = 6;

    private FirstPrismaticResolver() {}

    public static FirstPrismaticResult resolve(String patch, Participant participant,
            Object rawEvents, PrismaticItemClassifier classifier) {
        return resolve(patch, participant, rawEvents, classifier, true);
    }

    public static FirstPrismaticResult resolve(String patch, Participant participant,
            Object rawEvents, PrismaticItemClassifier classifier, boolean completeItemHistory) {
        if (participant == null) throw new IllegalArgumentException("participant is required");
        if (classifier == null) throw new IllegalArgumentException("classifier is required");

        List<FinalItem> finalItems = getFinalItems(patch, participant, classifier);
        List<Integer> finalPrismatics = getFinalPrismatics(finalItems);
        List<RawItemEvent> participantEvents = getParticipantItemEvents(rawEvents, participant);
        markUndoneEvents(participantEvents);
        int ambiguousCount = countAmbiguousUndoEvents(participantEvents);

        Set<Long> anvilUses = getPrismaticAnvilUses(participantEvents);
        long firstAnvilTimestamp = anvilUses.stream().mapToLong(Long::longValue).min().orElse(Long.MAX_VALUE);
        List<PrismaticEvent> prismaticEvents = getPrismaticEvents(patch, participantEvents, classifier);
        int missingCount = countUnknownItems(patch, finalItems, prismaticEvents, classifier);
        if (itemEventArray(jsonObject(rawEvents)) == null) missingCount++;
        if (!completeItemHistory) missingCount++;

        Set<Integer> candidates = getCandidates(patch, finalItems, prismaticEvents, classifier);
        if (candidates.isEmpty()) missingCount++;
        Set<Integer> preAnvilCandidates = resolveFromPreAnvilEvents(patch, prismaticEvents, firstAnvilTimestamp, classifier);
        if (completeItemHistory && preAnvilCandidates.size() == 1) {
            int itemId = preAnvilCandidates.iterator().next();
            return result(itemId, FirstPrismaticResolveType.PRE_ANVIL_EVENT,
                    "Un evento di possesso Prismatic precede il primo utilizzo di 220007.", finalItems,
                    finalPrismatics, anvilUses, prismaticEvents, missingCount, ambiguousCount);
        }
        if (preAnvilCandidates.size() > 1) ambiguousCount++;

        if (anvilUses.isEmpty() && candidates.size() == 1 && missingCount == 0) {
            int itemId = candidates.iterator().next();
            return result(itemId, FirstPrismaticResolveType.SINGLE_CANDIDATE,
                    "Nessun utilizzo di 220007 e un solo Prismatic ricostruibile.", finalItems,
                    finalPrismatics, anvilUses, prismaticEvents, missingCount, ambiguousCount);
        }

        AccountingResolution accounting = resolveByAccounting(finalItems, prismaticEvents, candidates,
                anvilUses, firstAnvilTimestamp, missingCount);
        if (accounting.itemId() != null) {
            return result(accounting.itemId(), FirstPrismaticResolveType.ACCOUNTING, accounting.reason(),
                    finalItems, finalPrismatics, anvilUses, prismaticEvents, missingCount, ambiguousCount);
        }
        if (accounting.ambiguous()) ambiguousCount++;

        Integer fallback = resolveByTooltipOrder(finalItems);
        if (fallback != null) {
            ambiguousCount++;
            String reason = missingCount > 0
                    ? "La ricostruzione esatta è ambigua; scelto il primo Prismatic noto nell'ordine degli slot. "
                        + missingCount + " classificazioni item restano sconosciute."
                    : "La ricostruzione esatta non individua un candidato unico; scelto il primo Prismatic nell'ordine degli slot.";
            return result(fallback, FirstPrismaticResolveType.TOOLTIP_FALLBACK, reason, finalItems,
                    finalPrismatics, anvilUses, prismaticEvents, missingCount, ambiguousCount);
        }

        String reason = missingCount > 0
                ? "Nessun candidato risolto; evidenza o classificazione mancante (missingCount=" + missingCount + ")."
                : "Nessun Prismatic trovato negli item finali o negli eventi persistiti.";
        if (candidates.size() > 1 || !preAnvilCandidates.isEmpty()) ambiguousCount++;
        return result(null, FirstPrismaticResolveType.NOT_FOUND, reason, finalItems, finalPrismatics,
                anvilUses, prismaticEvents, missingCount, ambiguousCount);
    }

    public static List<FinalItem> getFinalItems(String patch, Participant participant, PrismaticItemClassifier classifier) {
        int[] slots = { participant.item0, participant.item1, participant.item2, participant.item3, participant.item4, participant.item5 };
        List<FinalItem> result = new ArrayList<>(FINAL_ITEM_SLOTS);
        for (int slot = 0; slot < slots.length; slot++) {
            int itemId = slots[slot];
            if (itemId <= 0) continue;
            result.add(new FinalItem(slot, itemId, classifier.classify(patch, itemId)));
        }
        return List.copyOf(result);
    }

    public static List<Integer> getFinalPrismatics(List<FinalItem> finalItems) {
        List<Integer> result = new ArrayList<>();
        if (finalItems != null) for (FinalItem item : finalItems)
            if (item.classification() == Classification.PRISMATIC) result.add(item.itemId());
        return List.copyOf(result);
    }

    private static Set<Long> getPrismaticAnvilUses(List<RawItemEvent> events) {
        Set<Long> result = new LinkedHashSet<>();
        for (RawItemEvent event : events) {
            if (event.undone || event.timestamp < 0 || !event.involves(PRISMATIC_ANVIL_ID)) continue;
            if ("ITEM_PURCHASED".equals(event.type) || "ITEM_DESTROYED".equals(event.type)) result.add(event.timestamp);
        }
        return result;
    }

    private static List<PrismaticEvent> getPrismaticEvents(String patch, List<RawItemEvent> events,
            PrismaticItemClassifier classifier) {
        List<PrismaticEvent> result = new ArrayList<>();
        for (RawItemEvent event : events) {
            LinkedHashSet<Integer> ids = event.itemIds();
            boolean relevant = ids.contains(PRISMATIC_ANVIL_ID);
            int selectedId = firstRelevantId(patch, ids, classifier);
            relevant |= selectedId > 0;
            if (!relevant) continue;
            if (selectedId == 0) selectedId = PRISMATIC_ANVIL_ID;
            result.add(new PrismaticEvent(event.timestamp, selectedId, event.type,
                    positiveOrNull(event.beforeId), positiveOrNull(event.afterId), event.undoTargetType, event.undone));
        }
        result.sort(Comparator.comparingLong(PrismaticEvent::timestampMillis));
        return List.copyOf(result);
    }

    private static Set<Integer> resolveFromPreAnvilEvents(String patch, List<PrismaticEvent> events,
            long firstAnvilTimestamp, PrismaticItemClassifier classifier) {
        Set<Integer> result = new LinkedHashSet<>();
        for (PrismaticEvent event : events) {
            if (event.timestampMillis() >= firstAnvilTimestamp || event.itemId() == PRISMATIC_ANVIL_ID
                    || event.undone() && !"ITEM_UNDO".equals(event.eventType())) continue;
            if (!"ITEM_SOLD".equals(event.eventType()) && !"ITEM_DESTROYED".equals(event.eventType())
                    && !"ITEM_UNDO".equals(event.eventType())) continue;
            if ("ITEM_UNDO".equals(event.eventType())
                    && !"ITEM_SOLD".equals(event.undoTargetType())
                    && !"ITEM_DESTROYED".equals(event.undoTargetType())) continue;
            if (classifier.classify(patch, event.itemId()) == Classification.PRISMATIC) result.add(event.itemId());
        }
        return result;
    }

    private static Set<Integer> getCandidates(String patch, List<FinalItem> finalItems,
            List<PrismaticEvent> events, PrismaticItemClassifier classifier) {
        Set<Integer> result = new LinkedHashSet<>();
        for (FinalItem item : finalItems)
            if (item.classification() == Classification.PRISMATIC) result.add(item.itemId());
        for (PrismaticEvent event : events)
            if (event.itemId() != PRISMATIC_ANVIL_ID
                    && (!event.undone() || "ITEM_UNDO".equals(event.eventType()))
                    && (!"ITEM_UNDO".equals(event.eventType())
                        || "ITEM_SOLD".equals(event.undoTargetType()) || "ITEM_DESTROYED".equals(event.undoTargetType()))
                    && classifier.classify(patch, event.itemId()) == Classification.PRISMATIC) result.add(event.itemId());
        return result;
    }

    private static AccountingResolution resolveByAccounting(List<FinalItem> finalItems,
            List<PrismaticEvent> events, Set<Integer> candidates, Set<Long> anvilUses,
            long firstAnvilTimestamp, int missingCount) {
        if (anvilUses.isEmpty() || candidates.size() < 2 || missingCount > 0) return AccountingResolution.none(false);

        int expectedAcquisitions = 1 + anvilUses.size();
        int observedAcquisitions = getObservedAcquisitionCount(finalItems, events);
        if (expectedAcquisitions != observedAcquisitions) return AccountingResolution.none(false);

        Set<Integer> explicitlyPostAnvil = new HashSet<>();
        int postAnvilPurchases = 0;
        for (PrismaticEvent event : events) {
            if (!"ITEM_PURCHASED".equals(event.eventType()) || event.undone()
                    || event.timestampMillis() <= firstAnvilTimestamp || event.itemId() == PRISMATIC_ANVIL_ID) continue;
            explicitlyPostAnvil.add(event.itemId());
            postAnvilPurchases++;
        }
        if (postAnvilPurchases != anvilUses.size()) return AccountingResolution.none(false);

        Set<Integer> possibleFirst = new LinkedHashSet<>(candidates);
        possibleFirst.removeAll(explicitlyPostAnvil);
        if (possibleFirst.size() == 1) {
            int itemId = possibleFirst.iterator().next();
            return new AccountingResolution(itemId,
                    "Le acquisizioni osservate coincidono con 1 + usi anvil; gli acquisti Prismatic post-anvil lasciano un solo candidato gratuito.", false);
        }
        return AccountingResolution.none(possibleFirst.size() > 1);
    }

    private static int getObservedAcquisitionCount(List<FinalItem> finalItems, List<PrismaticEvent> events) {
        int count = 0;
        for (FinalItem item : finalItems) if (item.classification() == Classification.PRISMATIC) count++;
        for (PrismaticEvent event : events) {
            if (event.undone() || event.itemId() == PRISMATIC_ANVIL_ID) continue;
            if ("ITEM_SOLD".equals(event.eventType()) || "ITEM_DESTROYED".equals(event.eventType())) count++;
        }
        return count;
    }

    private static Integer resolveByTooltipOrder(List<FinalItem> finalItems) {
        for (FinalItem item : finalItems)
            if (item.classification() == Classification.PRISMATIC) return item.itemId();
        return null;
    }

    private static List<RawItemEvent> getParticipantItemEvents(Object rawEvents, Participant participant) {
        JSONObject source = jsonObject(rawEvents);
        JSONArray items = itemEventArray(source);
        if (items == null) return List.of();
        JSONObject refs = source.optJSONObject("participants");
        List<RawItemEvent> result = new ArrayList<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null || !belongsTo(item.opt("participant"), refs, participant)) continue;
            long timestamp = item.optLong("timestamp", -1);
            String type = item.optString("event", "");
            if (timestamp < 0 || !type.startsWith("ITEM_")) continue;
            result.add(new RawItemEvent(index, timestamp, type, item.optInt("item", 0),
                    item.optInt("before", 0), item.optInt("after", 0)));
        }
        result.sort(Comparator.comparingLong((RawItemEvent event) -> event.timestamp).thenComparingInt(event -> event.index));
        return result;
    }

    private static void markUndoneEvents(List<RawItemEvent> events) {
        for (int index = 0; index < events.size(); index++) {
            RawItemEvent undo = events.get(index);
            if (!"ITEM_UNDO".equals(undo.type)) continue;
            int targetId = undo.undoTargetId();
            if (targetId == 0) {
                undo.ambiguousUndo = true;
                continue;
            }
            RawItemEvent target = null;
            for (int previous = index - 1; previous >= 0; previous--) {
                RawItemEvent candidate = events.get(previous);
                if (candidate.undone || "ITEM_UNDO".equals(candidate.type)) continue;
                if (targetId == 0 || candidate.involves(targetId)) { target = candidate; break; }
            }
            if (target != null) {
                undo.undoTargetType = target.type;
                undo.undoTargetItemId = target.relevantItemId();
                target.undone = true;
                if (target.involves(PRISMATIC_ANVIL_ID)) for (RawItemEvent candidate : events) {
                    if (candidate.timestamp == target.timestamp && candidate.involves(PRISMATIC_ANVIL_ID)) candidate.undone = true;
                }
            } else undo.ambiguousUndo = true;
        }
    }

    private static int countAmbiguousUndoEvents(List<RawItemEvent> events) {
        int count = 0;
        for (RawItemEvent event : events) if (event.ambiguousUndo) count++;
        return count;
    }

    private static int countUnknownItems(String patch, List<FinalItem> finalItems, List<PrismaticEvent> events,
            PrismaticItemClassifier classifier) {
        Set<Integer> unknown = new HashSet<>();
        for (FinalItem item : finalItems) if (item.classification() == Classification.UNKNOWN) unknown.add(item.itemId());
        for (PrismaticEvent event : events) {
            if (event.itemId() != PRISMATIC_ANVIL_ID
                    && classifier.classify(patch, event.itemId()) == Classification.UNKNOWN) unknown.add(event.itemId());
            if (event.beforeId() != null && event.beforeId() != PRISMATIC_ANVIL_ID
                    && classifier.classify(patch, event.beforeId()) == Classification.UNKNOWN) unknown.add(event.beforeId());
            if (event.afterId() != null && event.afterId() != PRISMATIC_ANVIL_ID
                    && classifier.classify(patch, event.afterId()) == Classification.UNKNOWN) unknown.add(event.afterId());
        }
        return unknown.size();
    }

    private static int firstRelevantId(String patch, Set<Integer> ids, PrismaticItemClassifier classifier) {
        for (int id : ids) if (id != PRISMATIC_ANVIL_ID
                && classifier.classify(patch, id) != Classification.NON_PRISMATIC) return id;
        return 0;
    }

    private static boolean belongsTo(Object participantRef, JSONObject refs, Participant participant) {
        if (participantRef == null || participantRef == JSONObject.NULL) return false;
        String value = String.valueOf(participantRef);
        if (participant.id > 0 && String.valueOf(participant.id).equals(value)) return true;
        return refs != null && participant.puuid != null && participant.puuid.equals(refs.optString(value, null));
    }

    private static JSONObject jsonObject(Object raw) {
        if (raw instanceof JSONObject object) return object;
        if (raw instanceof Map<?, ?> map) {
            try { return new JSONObject(map); }
            catch (RuntimeException ignored) { return null; }
        }
        if (raw instanceof String json && !json.isBlank()) {
            try { return new JSONObject(json); }
            catch (RuntimeException ignored) { return null; }
        }
        return null;
    }

    private static JSONArray itemEventArray(JSONObject source) {
        if (source == null) return null;
        JSONArray items = source.optJSONArray("item_events");
        if (items != null) return items;
        String raw = source.optString("item_events", "");
        if (raw.isBlank()) return null;
        try { return new JSONArray(raw); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private static FirstPrismaticResult result(Integer itemId, FirstPrismaticResolveType type, String reason,
            List<FinalItem> finalItems, List<Integer> finalPrismatics, Set<Long> anvilUses,
            List<PrismaticEvent> events, int missingCount, int ambiguousCount) {
        List<Long> timestamps = new ArrayList<>(anvilUses);
        timestamps.sort(Long::compareTo);
        return new FirstPrismaticResult(itemId, type, reason, finalItems, finalPrismatics,
                timestamps, events, missingCount, ambiguousCount);
    }

    private record AccountingResolution(Integer itemId, String reason, boolean ambiguous) {
        private static AccountingResolution none(boolean ambiguous) {
            return new AccountingResolution(null, "", ambiguous);
        }
    }

    private static final class RawItemEvent {
        private final int index;
        private final long timestamp;
        private final String type;
        private final int itemId;
        private final int beforeId;
        private final int afterId;
        private boolean undone;
        private boolean ambiguousUndo;
        private String undoTargetType;
        private int undoTargetItemId;

        private RawItemEvent(int index, long timestamp, String type, int itemId, int beforeId, int afterId) {
            this.index = index;
            this.timestamp = timestamp;
            this.type = type;
            this.itemId = itemId;
            this.beforeId = beforeId;
            this.afterId = afterId;
        }

        private LinkedHashSet<Integer> itemIds() {
            LinkedHashSet<Integer> result = new LinkedHashSet<>();
            if (itemId > 0) result.add(itemId);
            if (beforeId > 0) result.add(beforeId);
            if (afterId > 0) result.add(afterId);
            if (undoTargetItemId > 0) result.add(undoTargetItemId);
            return result;
        }

        private boolean involves(int id) {
            return id > 0 && (itemId == id || beforeId == id || afterId == id);
        }

        private int undoTargetId() {
            if (beforeId > 0) return beforeId;
            if (afterId > 0) return afterId;
            return itemId;
        }

        private int relevantItemId() {
            return switch (type) {
                case "ITEM_PURCHASED" -> afterId > 0 ? afterId : itemId;
                case "ITEM_SOLD", "ITEM_DESTROYED" -> beforeId > 0 ? beforeId : itemId;
                default -> itemId > 0 ? itemId : beforeId > 0 ? beforeId : afterId;
            };
        }
    }
}
