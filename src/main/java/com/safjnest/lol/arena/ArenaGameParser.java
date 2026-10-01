package com.safjnest.lol.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.arena.ParsedArenaGame.Core;
import com.safjnest.lol.arena.ParsedArenaGame.Coverage;
import com.safjnest.lol.arena.ParsedArenaGame.Observation;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

public final class ArenaGameParser {

    private static final int MAX_PRISMATIC_POSITION = 6;
    private static final Set<String> ITEM_TYPES = Set.of("ITEM_PURCHASED", "ITEM_SOLD", "ITEM_UNDO", "ITEM_DESTROYED");

    private ArenaGameParser() {}

    public static ParsedArenaGame parse(Match match, Participant participant, ArenaItemCatalog catalog,
            boolean completeItemHistory) {
        Counters counters = new Counters();
        List<Observation> choices = new ArrayList<>();
        Integer placement = participant.subTeamPlacement > 0 ? participant.subTeamPlacement : null;
        if (placement == null || placement > 8) counters.missing("PLACEMENT_INVALID_OR_ABSENT");
        boolean win = participant.win || participant.subTeamPlacement >= 3;
        List<Event> events = readEvents(match.eventData, participant, counters);
        boolean ordered = completeItemHistory && counters.orderKnown;
        if (!completeItemHistory) counters.missing("ITEM_HISTORY_PARTIAL");
        List<Event> acquisitions = purchases(events, counters);
        ordered &= counters.orderKnown;
        List<Integer> coreIds = new ArrayList<>();
        Set<Integer> itemIds = new HashSet<>();
        int prismaticPosition = 0;
        for (Event event : acquisitions) {
            int id = event.acquiredId();
            if (catalog.isIgnored(match.patch, id)) continue;
            Kind kind = catalog.kind(match.patch, id);
            if (kind == null) {
                counters.missing("ITEM_CLASSIFICATION_ABSENT");
                ordered = false;
                continue;
            }
            if (kind == Kind.PRISMATIC) {
                prismaticPosition++;
                Integer position = ordered && prismaticPosition <= MAX_PRISMATIC_POSITION ? prismaticPosition : null;
                if (prismaticPosition > MAX_PRISMATIC_POSITION) counters.missing("PRISMATIC_POSITION_OUT_OF_RANGE");
                choices.add(new Observation(kind, id, position, event.timestamp));
            } else if (kind == Kind.BOOTS) {
                choices.add(new Observation(kind, id, null, event.timestamp));
            } else if (itemIds.add(id)) {
                coreIds.add(id);
                choices.add(new Observation(kind, id, ordered ? coreIds.size() : null, event.timestamp));
            }
        }
        if (!ordered) {
            for (int i = 0; i < choices.size(); i++) {
                Observation choice = choices.get(i);
                choices.set(i, new Observation(choice.kind(), choice.id(), null, choice.timestampMillis()));
            }
        }
        addFirstEvidence(match.eventData, participant, catalog, match.patch, choices, counters);
        Set<String> observed = new HashSet<>();
        for (Observation choice : choices) observed.add(choice.kind() + ":" + choice.id());
        int[] finalItems = {participant.item0, participant.item1, participant.item2, participant.item3,
            participant.item4, participant.item5, participant.item6};
        for (int id : finalItems) {
            Kind kind = catalog.kind(match.patch, id);
            if (id > 0 && kind == null && !catalog.isIgnored(match.patch, id)) counters.missing("FINAL_ITEM_CLASSIFICATION_ABSENT");
            if (kind != null && id != ArenaItemCatalog.PRISMATIC_ANVIL && observed.add(kind + ":" + id))
                choices.add(new Observation(kind, id, null, null));
        }
        if (participant.boots > 0) {
            boolean purchaseObserved = false;
            for (Event event : events) {
                if ("ITEM_PURCHASED".equals(event.type) && (event.item == participant.boots || event.after == participant.boots))
                    purchaseObserved = true;
            }
            if (catalog.kind(match.patch, participant.boots) != Kind.BOOTS) counters.missing("BOOT_CLASSIFICATION_INVALID_OR_ABSENT");
            else if (!purchaseObserved && observed.add(Kind.BOOTS + ":" + participant.boots))
                choices.add(new Observation(Kind.BOOTS, participant.boots, null, null));
        }
        if (participant.augments == null || participant.augments.isEmpty()) counters.missing("AUGMENTS_ABSENT_OR_EMPTY");
        else for (int i = 0; i < participant.augments.size(); i++) {
            Integer id = participant.augments.get(i);
            if (id != null && id > 0) choices.add(new Observation(Kind.AUGMENT, id, i + 1, null));
            else counters.missing("AUGMENT_ID_INVALID_OR_ABSENT");
        }
        return new ParsedArenaGame(placement, win,
            ordered && !coreIds.isEmpty() ? new Core(coreIds) : null, choices, counters.snapshot());
    }

    // ============================================================================

    private static List<Event> readEvents(Object raw, Participant participant, Counters counters) {
        JSONObject root = json(raw);
        JSONArray items = array(root, "item_events");
        JSONObject refs = object(root, "participants");
        if (items == null) {
            counters.missing("ITEM_EVENTS_ABSENT");
            counters.orderKnown = false;
            return List.of();
        }
        boolean validRefs = validReferences(refs, participant);
        List<Event> result = new ArrayList<>();
        Set<EventIdentity> seen = new HashSet<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject source = items.optJSONObject(index);
            if (source == null) {
                counters.ambiguous("ITEM_EVENT_INVALID");
                continue;
            }
            Integer actor = integer(source.opt("participant"));
            if (actor != null && actor == 0) {
                counters.rejected("PARTICIPANT_ZERO");
                counters.orderKnown = false;
                continue;
            }
            if (actor != null && actor != participant.id && refs != null && refs.has(String.valueOf(actor))) continue;
            if (!validRefs || actor == null || actor != participant.id) {
                counters.ambiguous("ITEM_EVENT_UNATTRIBUTABLE");
                continue;
            }
            String type = source.optString("event");
            Integer item = integer(source.opt("item"));
            Integer before = integer(source.opt("before"));
            Integer after = integer(source.opt("after"));
            Long time = time(source.opt("timestamp"));
            if (!ITEM_TYPES.contains(type) || item == null || before == null || after == null
                    || item < 0 || before < 0 || after < 0) {
                counters.ambiguous("ITEM_TRANSITION_UNSUPPORTED_OR_INVALID");
                continue;
            }
            if (time == null) {
                counters.missing("ITEM_TIME_ABSENT_OR_INVALID");
                counters.orderKnown = false;
            }
            EventIdentity identity = new EventIdentity(type, item, before, after, time);
            if (!seen.add(identity)) {
                counters.rejected("DUPLICATE_ITEM_EVENT");
                continue;
            }
            result.add(new Event(index, type, item, before, after, time));
        }
        result.sort(Comparator.comparing((Event e) -> e.timestamp, Comparator.nullsLast(Long::compareTo))
            .thenComparingInt(e -> e.index));
        return result;
    }

    private static List<Event> purchases(List<Event> events, Counters counters) {
        List<Event> acquisitions = new ArrayList<>();
        Event previous = null;
        for (Event event : events) {
            switch (event.type) {
                case "ITEM_PURCHASED" -> {
                    if (event.acquiredId() <= 0 || (event.after > 0 && event.item > 0 && event.item != event.after)) {
                        counters.ambiguous("PURCHASE_ID_CONFLICT_OR_ABSENT");
                    } else acquisitions.add(event);
                    previous = event;
                }
                case "ITEM_SOLD", "ITEM_DESTROYED" -> previous = event;
                case "ITEM_UNDO" -> {
                    if (previous == null || event.timestamp == null || previous.timestamp == null || !event.identifies(previous)) {
                        counters.ambiguous("UNDO_TARGET_AMBIGUOUS");
                        Set<Integer> ids = event.ids();
                        if (!ids.isEmpty()) acquisitions.removeIf(acquisition -> ids.contains(acquisition.acquiredId()));
                        else if (event.timestamp == null) acquisitions.clear();
                        else if (previous != null && "ITEM_PURCHASED".equals(previous.type)) acquisitions.remove(previous);
                    } else if ("ITEM_PURCHASED".equals(previous.type)) acquisitions.remove(previous);
                    previous = null;
                }
                default -> counters.ambiguous("ITEM_TRANSITION_UNSUPPORTED_OR_INVALID");
            }
        }
        for (Event event : events) {
            if ("ITEM_UNDO".equals(event.type) && event.timestamp == null && event.ids().isEmpty()) {
                acquisitions.clear();
                break;
            }
        }
        return acquisitions;
    }

    private static void addFirstEvidence(Object raw, Participant participant, ArenaItemCatalog catalog, String patch,
            List<Observation> choices, Counters counters) {
        JSONObject root = json(raw);
        JSONObject evidence = object(root, "arena_evidence");
        if (evidence == null) return;
        if (evidence.optInt("version", -1) != 1) {
            counters.ambiguous("ARENA_EVIDENCE_VERSION_UNSUPPORTED");
            return;
        }
        JSONObject refs = object(root, "participants");
        if (!validReferences(refs, participant)) {
            counters.rejected("FIRST_PRISMATIC_UNATTRIBUTABLE");
            return;
        }
        JSONObject source = object(object(object(evidence, "participants"), String.valueOf(participant.id)), "first_prismatic");
        if (source == null) return;
        String status = source.optString("status");
        if (!"EXACT".equals(status)) {
            if ("AMBIGUOUS".equals(status)) counters.ambiguous("FIRST_PRISMATIC_EVIDENCE_AMBIGUOUS");
            else if ("UNKNOWN".equals(status)) counters.missing("FIRST_PRISMATIC_NOT_EXACT");
            else counters.rejected("FIRST_PRISMATIC_STATUS_INVALID");
            return;
        }
        Integer id = integer(source.opt("item_id"));
        Long timestamp = time(source.opt("timestamp"));
        if (id == null || catalog.kind(patch, id) != Kind.PRISMATIC) {
            counters.rejected("FIRST_PRISMATIC_ID_INVALID");
            return;
        }
        List<Observation> prismatics = new ArrayList<>();
        for (Observation choice : choices) if (choice.kind() == Kind.PRISMATIC) prismatics.add(choice);
        if (!prismatics.isEmpty()) {
            Observation first = prismatics.get(0);
            if (first.id() == id && (java.util.Objects.equals(first.timestampMillis(), timestamp)
                    || (timestamp == null && Integer.valueOf(1).equals(first.position())))) return;
            if (timestamp != null && first.timestampMillis() != null && timestamp >= first.timestampMillis()) {
                counters.rejected("FIRST_PRISMATIC_EVIDENCE_CONFLICT");
                return;
            }
            for (int i = 0; i < choices.size(); i++) {
                Observation choice = choices.get(i);
                if (choice.kind() != Kind.PRISMATIC) continue;
                Integer position = timestamp != null && choice.timestampMillis() != null
                    && timestamp < choice.timestampMillis() && choice.position() != null
                    && choice.position() < MAX_PRISMATIC_POSITION ? choice.position() + 1 : null;
                choices.set(i, new Observation(choice.kind(), choice.id(), position, choice.timestampMillis()));
            }
        }
        choices.add(new Observation(Kind.PRISMATIC, id, 1, timestamp));
    }

    private static boolean validReferences(JSONObject refs, Participant participant) {
        if (refs == null || participant.id <= 0 || participant.puuid == null || participant.puuid.isBlank()
                || !participant.puuid.equals(refs.optString(String.valueOf(participant.id)))) return false;
        for (String key : refs.keySet()) {
            if (!key.equals(String.valueOf(participant.id)) && participant.puuid.equals(refs.optString(key))
                    && !"0".equals(key)) return false;
        }
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

    private static JSONObject object(JSONObject root, String key) {
        if (root == null) return null;
        Object value = root.opt(key);
        return json(value);
    }

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
            for (int id : ids) if (id != target.item && id != target.before && id != target.after) return false;
            return true;
        }
    }

    private static final class Counters {
        private final Map<String, Long> missing = new HashMap<>();
        private final Map<String, Long> ambiguous = new HashMap<>();
        private final Map<String, Long> rejected = new HashMap<>();
        private boolean orderKnown = true;
        private void missing(String reason) { missing.merge(reason, 1L, Long::sum); }
        private void ambiguous(String reason) { ambiguous.merge(reason, 1L, Long::sum); orderKnown = false; }
        private void rejected(String reason) { rejected.merge(reason, 1L, Long::sum); }
        private Coverage snapshot() { return new Coverage(missing, ambiguous, rejected); }
    }
}
