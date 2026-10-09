package com.safjnest.lol.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.arena.ParsedArenaGame.Augment;
import com.safjnest.lol.arena.ParsedArenaGame.Coverage;
import com.safjnest.lol.arena.ParsedArenaGame.Equipment;
import com.safjnest.lol.arena.ParsedArenaGame.Observation;
import com.safjnest.lol.arena.ParsedArenaGame.OrderQuality;
import com.safjnest.lol.arena.ParsedArenaGame.OrderSource;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

public final class ArenaGameParser {

    private static final int MAX_CHOICE_POSITION = 6;
    private static final Set<String> ITEM_TYPES = Set.of("ITEM_PURCHASED", "ITEM_SOLD", "ITEM_UNDO", "ITEM_DESTROYED");

    private ArenaGameParser() {}

    public static ParsedArenaGame parse(Match match, Participant participant, ArenaItemCatalog catalog,
            boolean completeItemHistory) {
        Counters counters = new Counters();
        Integer placement = participant.subTeamPlacement > 0 ? participant.subTeamPlacement : null;
        if (placement == null || placement > 8) counters.missing("PLACEMENT_INVALID_OR_ABSENT");
        boolean win = participant.win || participant.subTeamPlacement >= 3;
        List<Event> events = readEvents(match.eventData, participant, counters);
        if (!completeItemHistory) counters.missing("ITEM_HISTORY_PARTIAL");
        Replay replay = purchases(events, counters);
        boolean timelineComplete = completeItemHistory && counters.hasTimeline && counters.orderKnown;
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        Map<Integer, Observation> boots = new LinkedHashMap<>();
        for (Event event : replay.acquisitions) {
            int id = event.acquiredId();
            if (id == ArenaItemCatalog.PRISMATIC_ANVIL || catalog.isIgnored(match.patch, id)) continue;
            Kind kind = catalog.kind(match.patch, id);
            if (kind == null) {
                counters.missing("ITEM_CLASSIFICATION_ABSENT");
                counters.classificationKnown = false;
                continue;
            }
            if (kind == Kind.BOOTS) boots.putIfAbsent(id, new Observation(kind, id, null, event.timestamp));
            else nodes.putIfAbsent(id, new Node(kind, id, event.timestamp, null, event.index, OrderSource.TIMELINE));
        }
        boolean finalEquipment = addFinalItems(participant, catalog, match.patch, nodes, boots, counters);
        addBootFallback(participant, catalog, match.patch, events, boots, counters);
        timelineComplete &= counters.classificationKnown;
        JSONObject resolverEvents = resolverEvents(replay.events, participant, counters.hasTimeline);
        FirstPrismaticResult resolved = FirstPrismaticResolver.resolve(match.patch, participant,
            resolverEvents, catalog.prismatics(), timelineComplete);
        if (resolved.type() == FirstPrismaticResolveType.TOOLTIP_FALLBACK)
            counters.ambiguous.merge("FIRST_PRISMATIC_TOOLTIP_FALLBACK", 1L, Long::sum);
        Node sidecar = firstEvidence(match.eventData, participant, catalog, match.patch, nodes, counters);
        if (sidecar != null) nodes.putIfAbsent(sidecar.id, sidecar);
        if (resolved.itemId() != null) nodes.putIfAbsent(resolved.itemId(),
            new Node(Kind.PRISMATIC, resolved.itemId(), null, tooltipSlot(participant, resolved.itemId()),
                Integer.MAX_VALUE, OrderSource.FIRST_PRISMATIC_RESOLVER));
        Integer first = sidecar == null ? resolved.itemId() : Integer.valueOf(sidecar.id);
        Node firstTimed = null;
        boolean allPrismaticsTimed = true;
        for (Node node : nodes.values()) {
            if (node.kind != Kind.PRISMATIC) continue;
            allPrismaticsTimed &= node.timestamp != null;
            if (node.timestamp != null && (firstTimed == null || compareTimeline(node, firstTimed) < 0)) firstTimed = node;
        }
        Node selected = first == null ? null : nodes.get(first);
        boolean preAnvilTimeline = firstTimed != null && (resolved.anvilTimestamps().isEmpty()
            || firstTimed.timestamp < resolved.anvilTimestamps().get(0));
        boolean timelineFirst = timelineComplete && allPrismaticsTimed && preAnvilTimeline;
        if (firstTimed != null && (timelineFirst
                || selected != null && selected.timestamp != null && compareTimeline(firstTimed, selected) < 0)) {
            if (first != null && first != firstTimed.id) counters.rejected("FIRST_PRISMATIC_RESOLVER_CONFLICT");
            first = firstTimed.id;
        }
        if (first == null) counters.missing("FIRST_PRISMATIC_NOT_FOUND");
        boolean firstExact = first != null && (timelineFirst || sidecar != null && first == sidecar.id
            || timelineComplete && first.equals(resolved.itemId())
                && resolved.type() != FirstPrismaticResolveType.TOOLTIP_FALLBACK
                && resolved.type() != FirstPrismaticResolveType.NOT_FOUND);
        List<Node> ordered = order(nodes, first, counters);
        boolean complete = !ordered.isEmpty() && counters.classificationKnown && !counters.orderConflict
            && (finalEquipment || timelineComplete) && (counters.orderKnown || finalEquipment);
        List<Equipment> equipment = new ArrayList<>();
        List<Observation> choices = new ArrayList<>(boots.values());
        int prismaticPosition = 0;
        int legendaryPosition = 0;
        boolean timed = false;
        boolean fallback = !timelineComplete;
        for (Node node : ordered) {
            Integer typePosition = node.kind == Kind.PRISMATIC ? ++prismaticPosition : ++legendaryPosition;
            if (!complete || node.kind == Kind.PRISMATIC && first == null) typePosition = null;
            if (node.kind == Kind.PRISMATIC && node.id == (first == null ? -1 : first)) typePosition = 1;
            if (node.kind == Kind.PRISMATIC && typePosition != null && typePosition > MAX_CHOICE_POSITION) {
                counters.missing("PRISMATIC_POSITION_OUT_OF_RANGE");
                typePosition = null;
            }
            Integer position = complete ? equipment.size() + 1 : null;
            OrderSource source = node.source;
            if (node.id == (first == null ? -1 : first) && node.timestamp == null)
                source = OrderSource.FIRST_PRISMATIC_RESOLVER;
            timed |= source == OrderSource.TIMELINE;
            fallback |= source != OrderSource.TIMELINE;
            equipment.add(new Equipment(node.kind, node.id, position, typePosition, node.timestamp, node.slot, source));
            choices.add(new Observation(node.kind, node.id, typePosition, node.timestamp));
            if (node.timestamp == null) counters.missing("EQUIPMENT_TIME_ABSENT");
            if (typePosition == null) counters.missing("EQUIPMENT_POSITION_ABSENT");
        }
        if (first == null && prismaticPosition > 0) complete = false;
        List<Augment> augments = new ArrayList<>();
        if (participant.augments == null || participant.augments.isEmpty()) counters.missing("AUGMENTS_ABSENT_OR_EMPTY");
        else for (int i = 0; i < participant.augments.size(); i++) {
            Integer id = participant.augments.get(i);
            if (i >= MAX_CHOICE_POSITION) { counters.missing("AUGMENT_POSITION_OUT_OF_RANGE"); continue; }
            if (id == null || id <= 0) counters.missing("AUGMENT_ID_INVALID_OR_ABSENT");
            else {
                augments.add(new Augment(id, i + 1));
                choices.add(new Observation(Kind.AUGMENT, id, i + 1, null));
            }
        }
        Integer boot = selectBoot(participant, boots, counters);
        OrderQuality quality = !complete ? OrderQuality.UNRESOLVED : !timed ? OrderQuality.FALLBACK
            : fallback ? OrderQuality.MIXED : OrderQuality.EXACT;
        return new ParsedArenaGame(placement, win, boot, equipment, augments, choices,
            first, resolved, firstExact, complete, quality, counters.snapshot());
    }

    // ============================================================================

    private static boolean addFinalItems(Participant participant, ArenaItemCatalog catalog, String patch,
            Map<Integer, Node> nodes, Map<Integer, Observation> boots, Counters counters) {
        int[] items = {participant.item0, participant.item1, participant.item2,
            participant.item3, participant.item4, participant.item5};
        boolean equipment = false;
        for (int slot = 0; slot < items.length; slot++) {
            int id = items[slot];
            if (id <= 0 || id == ArenaItemCatalog.PRISMATIC_ANVIL || catalog.isIgnored(patch, id)) continue;
            Kind kind = catalog.kind(patch, id);
            if (kind == null) {
                counters.missing("FINAL_ITEM_CLASSIFICATION_ABSENT");
                counters.classificationKnown = false;
            } else if (kind == Kind.BOOTS) boots.putIfAbsent(id, new Observation(kind, id, null, null));
            else {
                equipment = true;
                Node existing = nodes.get(id);
                if (existing == null) nodes.put(id, new Node(kind, id, null, slot, Integer.MAX_VALUE, OrderSource.TOOLTIP_FALLBACK));
                else if (existing.slot == null) nodes.put(id,
                    new Node(kind, id, existing.timestamp, slot, existing.index, existing.source));
            }
        }
        return equipment;
    }

    private static void addBootFallback(Participant participant, ArenaItemCatalog catalog, String patch,
            List<Event> events, Map<Integer, Observation> boots, Counters counters) {
        if (participant.boots <= 0) return;
        boolean purchased = false;
        for (Event event : events) if ("ITEM_PURCHASED".equals(event.type)
                && (event.item == participant.boots || event.after == participant.boots)) purchased = true;
        if (catalog.kind(patch, participant.boots) != Kind.BOOTS) counters.missing("BOOT_CLASSIFICATION_INVALID_OR_ABSENT");
        else if (!purchased) boots.putIfAbsent(participant.boots, new Observation(Kind.BOOTS, participant.boots, null, null));
    }

    private static Integer selectBoot(Participant participant, Map<Integer, Observation> boots, Counters counters) {
        Integer selected = null;
        for (Integer id : boots.keySet()) {
            if (tooltipSlot(participant, id) != null) {
                if (selected != null) { counters.ambiguous("BOOT_IDENTITY_AMBIGUOUS"); return null; }
                selected = id;
            }
        }
        if (selected != null) return selected;
        if (boots.size() == 1) return boots.keySet().iterator().next();
        if (boots.size() > 1) counters.ambiguous("BOOT_IDENTITY_AMBIGUOUS");
        return null;
    }

    private static Integer tooltipSlot(Participant p, int id) {
        int[] items = {p.item0, p.item1, p.item2, p.item3, p.item4, p.item5};
        for (int slot = 0; slot < items.length; slot++) if (items[slot] == id) return slot;
        return null;
    }

    private static List<Node> order(Map<Integer, Node> nodes, Integer first, Counters counters) {
        List<Node> remaining = new ArrayList<>(nodes.values());
        Map<Integer, Set<Integer>> before = new HashMap<>();
        for (Node node : remaining) before.put(node.id, new HashSet<>());
        for (Node a : remaining) for (Node b : remaining) {
            if (a.id == b.id) continue;
            if (a.timestamp != null && b.timestamp != null && compareTimeline(a, b) < 0) before.get(b.id).add(a.id);
            if (first != null && a.id == first && b.kind == Kind.PRISMATIC) before.get(b.id).add(a.id);
        }
        Comparator<Node> preference = Comparator.comparing((Node n) -> n.slot, Comparator.nullsLast(Integer::compareTo))
            .thenComparingInt(n -> n.index).thenComparingInt(n -> n.id);
        List<Node> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Node selected = null;
            for (Node node : remaining) if (before.get(node.id).isEmpty()
                    && (selected == null || preference.compare(node, selected) < 0)) selected = node;
            if (selected == null) {
                counters.ambiguous("EQUIPMENT_ORDER_CONFLICT");
                counters.orderConflict = true;
                remaining.sort(preference);
                result.addAll(remaining);
                break;
            }
            result.add(selected);
            remaining.remove(selected);
            for (Set<Integer> predecessors : before.values()) predecessors.remove(selected.id);
        }
        return result;
    }

    private static int compareTimeline(Node a, Node b) {
        int compare = Long.compare(a.timestamp, b.timestamp);
        return compare == 0 ? Integer.compare(a.index, b.index) : compare;
    }

    private static List<Event> readEvents(Object raw, Participant participant, Counters counters) {
        JSONObject root = json(raw);
        JSONArray items = array(root, "item_events");
        JSONObject refs = object(root, "participants");
        counters.hasTimeline = items != null;
        if (items == null) { counters.missing("ITEM_EVENTS_ABSENT"); return List.of(); }
        boolean validRefs = validReferences(refs, participant);
        List<Event> result = new ArrayList<>();
        Set<EventIdentity> seen = new HashSet<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject source = items.optJSONObject(index);
            if (source == null) { counters.ambiguous("ITEM_EVENT_INVALID"); continue; }
            Integer actor = integer(source.opt("participant"));
            Integer rawActor = integer(source.opt("participantId"));
            if (Integer.valueOf(0).equals(actor) || Integer.valueOf(0).equals(rawActor)) {
                counters.rejected("PARTICIPANT_ZERO"); counters.orderKnown = false; continue;
            }
            if (source.has("participantId") && !java.util.Objects.equals(actor, rawActor)) {
                counters.ambiguous("ITEM_EVENT_UNATTRIBUTABLE"); continue;
            }
            if (validRefs && actor != null && actor > 0 && actor != participant.id) continue;
            if (!validRefs || actor == null || actor != participant.id) {
                counters.ambiguous("ITEM_EVENT_UNATTRIBUTABLE"); continue;
            }
            String type = source.optString("event");
            Integer item = integer(source.opt("item"));
            Integer before = integer(source.opt("before"));
            Integer after = integer(source.opt("after"));
            Long time = time(source.opt("timestamp"));
            if (!ITEM_TYPES.contains(type) || item == null || before == null || after == null
                    || item < 0 || before < 0 || after < 0) {
                counters.ambiguous("ITEM_TRANSITION_UNSUPPORTED_OR_INVALID"); continue;
            }
            if (time == null) { counters.missing("ITEM_TIME_ABSENT_OR_INVALID"); counters.orderKnown = false; }
            EventIdentity identity = new EventIdentity(type, item, before, after, time);
            if (!seen.add(identity)) { counters.rejected("DUPLICATE_ITEM_EVENT"); continue; }
            result.add(new Event(index, type, item, before, after, time));
        }
        result.sort(Comparator.comparing((Event e) -> e.timestamp, Comparator.nullsLast(Long::compareTo))
            .thenComparingInt(e -> e.index));
        return result;
    }

    private static Replay purchases(List<Event> events, Counters counters) {
        List<Event> acquisitions = new ArrayList<>();
        Set<Event> suppressed = new HashSet<>();
        Event previous = null;
        for (Event event : events) {
            switch (event.type) {
                case "ITEM_PURCHASED" -> {
                    if (event.acquiredId() <= 0 || event.after > 0 && event.item > 0 && event.item != event.after) {
                        counters.ambiguous("PURCHASE_ID_CONFLICT_OR_ABSENT");
                        suppressed.add(event);
                    } else acquisitions.add(event);
                    previous = event;
                }
                case "ITEM_SOLD", "ITEM_DESTROYED" -> previous = event;
                case "ITEM_UNDO" -> {
                    if (previous == null || event.timestamp == null || previous.timestamp == null || !event.identifies(previous)) {
                        counters.ambiguous("UNDO_TARGET_AMBIGUOUS");
                        Set<Integer> ids = event.ids();
                        if (!ids.isEmpty()) {
                            acquisitions.removeIf(a -> ids.contains(a.acquiredId()));
                            for (Event candidate : events) if (!java.util.Collections.disjoint(ids, candidate.ids())) suppressed.add(candidate);
                        } else if (event.timestamp == null) {
                            acquisitions.clear(); suppressed.addAll(events);
                        } else if (previous != null && "ITEM_PURCHASED".equals(previous.type)) {
                            acquisitions.remove(previous); suppressed.add(previous);
                        }
                        suppressed.add(event);
                    } else if (previous.ids().contains(ArenaItemCatalog.PRISMATIC_ANVIL)) {
                        for (Event candidate : events) if (java.util.Objects.equals(candidate.timestamp, previous.timestamp)
                                && candidate.ids().contains(ArenaItemCatalog.PRISMATIC_ANVIL)) {
                            acquisitions.remove(candidate); suppressed.add(candidate);
                        }
                        suppressed.add(event);
                    } else if ("ITEM_PURCHASED".equals(previous.type)) {
                        acquisitions.remove(previous); suppressed.add(previous); suppressed.add(event);
                    }
                    previous = null;
                }
                default -> counters.ambiguous("ITEM_TRANSITION_UNSUPPORTED_OR_INVALID");
            }
        }
        for (Event event : events) if ("ITEM_UNDO".equals(event.type) && event.timestamp == null && event.ids().isEmpty()) {
            acquisitions.clear(); suppressed.addAll(events); break;
        }
        List<Event> usable = new ArrayList<>();
        for (Event event : events) if (!suppressed.contains(event)) usable.add(event);
        return new Replay(acquisitions, usable);
    }

    private static JSONObject resolverEvents(List<Event> events, Participant participant, boolean hasTimeline) {
        JSONObject root = new JSONObject();
        root.put("participants", new JSONObject().put(String.valueOf(participant.id), participant.puuid));
        if (!hasTimeline) return root;
        JSONArray items = new JSONArray();
        for (Event event : events) {
            if (event.timestamp == null) continue;
            items.put(new JSONObject().put("participant", participant.id).put("event", event.type)
                .put("timestamp", event.timestamp).put("item", event.item).put("before", event.before).put("after", event.after));
        }
        return root.put("item_events", items);
    }

    private static Node firstEvidence(Object raw, Participant participant, ArenaItemCatalog catalog, String patch,
            Map<Integer, Node> nodes, Counters counters) {
        JSONObject root = json(raw);
        JSONObject evidence = object(root, "arena_evidence");
        if (evidence == null) return null;
        if (evidence.optInt("version", -1) != 1) { counters.ambiguous("ARENA_EVIDENCE_VERSION_UNSUPPORTED"); return null; }
        if (!validReferences(object(root, "participants"), participant)) {
            counters.rejected("FIRST_PRISMATIC_UNATTRIBUTABLE"); return null;
        }
        JSONObject source = object(object(object(evidence, "participants"), String.valueOf(participant.id)), "first_prismatic");
        if (source == null) return null;
        String status = source.optString("status");
        if (!"EXACT".equals(status)) {
            if ("AMBIGUOUS".equals(status)) counters.ambiguous("FIRST_PRISMATIC_EVIDENCE_AMBIGUOUS");
            else counters.missing("FIRST_PRISMATIC_NOT_EXACT");
            return null;
        }
        Integer id = integer(source.opt("item_id"));
        Long timestamp = time(source.opt("timestamp"));
        if (id == null || catalog.kind(patch, id) != Kind.PRISMATIC) { counters.rejected("FIRST_PRISMATIC_ID_INVALID"); return null; }
        for (Node node : nodes.values()) if (node.kind == Kind.PRISMATIC && node.id != id
                && timestamp != null && node.timestamp != null && timestamp >= node.timestamp) {
            counters.rejected("FIRST_PRISMATIC_EVIDENCE_CONFLICT"); return null;
        }
        return new Node(Kind.PRISMATIC, id, timestamp, null, -1, OrderSource.FIRST_PRISMATIC_RESOLVER);
    }

    private static boolean validReferences(JSONObject refs, Participant participant) {
        if (participant.id <= 0 || participant.puuid == null || participant.puuid.isBlank()) return false;
        if (refs == null) return true;
        if (!participant.puuid.equals(refs.optString(String.valueOf(participant.id)))) return false;
        for (String key : refs.keySet()) if (!key.equals(String.valueOf(participant.id))
                && participant.puuid.equals(refs.optString(key)) && !"0".equals(key)) return false;
        return true;
    }

    private static Integer integer(Object raw) {
        if (!(raw instanceof Number number)) return null;
        long value = number.longValue();
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && number.doubleValue() == value ? (int) value : null;
    }

    private static Long time(Object raw) {
        if (!(raw instanceof Number number)) return null;
        long value = number.longValue();
        return value >= 0 && number.doubleValue() == value ? value : null;
    }

    private static JSONObject json(Object raw) {
        if (raw instanceof JSONObject object) return object;
        if (raw instanceof Map<?, ?> map) return new JSONObject(map);
        if (raw instanceof String text) try { return new JSONObject(text); }
        catch (RuntimeException ignored) { return null; }
        return null;
    }

    private static JSONObject object(JSONObject root, String key) { return root == null ? null : json(root.opt(key)); }

    private static JSONArray array(JSONObject root, String key) {
        if (root == null) return null;
        Object value = root.opt(key);
        if (value instanceof JSONArray array) return array;
        if (value instanceof String text) try { return new JSONArray(text); }
        catch (RuntimeException ignored) { return null; }
        return null;
    }

    private record EventIdentity(String type, int item, int before, int after, Long timestamp) {}

    private record Event(int index, String type, int item, int before, int after, Long timestamp) {
        private int acquiredId() { return after > 0 ? after : item; }
        private Set<Integer> ids() {
            Set<Integer> ids = new HashSet<>();
            if (item > 0) ids.add(item);
            if (before > 0) ids.add(before);
            if (after > 0) ids.add(after);
            return ids;
        }
        private boolean identifies(Event target) {
            Set<Integer> ids = ids();
            if (ids.isEmpty()) return false;
            for (int id : ids) if (!target.ids().contains(id)) return false;
            return true;
        }
    }

    private record Replay(List<Event> acquisitions, List<Event> events) {}
    private record Node(Kind kind, int id, Long timestamp, Integer slot, int index, OrderSource source) {}

    private static final class Counters {
        private final Map<String, Long> missing = new HashMap<>();
        private final Map<String, Long> ambiguous = new HashMap<>();
        private final Map<String, Long> rejected = new HashMap<>();
        private boolean hasTimeline;
        private boolean orderKnown = true;
        private boolean classificationKnown = true;
        private boolean orderConflict;
        private void missing(String reason) { missing.merge(reason, 1L, Long::sum); }
        private void ambiguous(String reason) { ambiguous.merge(reason, 1L, Long::sum); orderKnown = false; }
        private void rejected(String reason) { rejected.merge(reason, 1L, Long::sum); }
        private Coverage snapshot() { return new Coverage(missing, ambiguous, rejected); }
    }
}
