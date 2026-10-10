package com.safjnest.lol.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.safjnest.lol.arena.ParsedArenaGame.Augment;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ArenaGameParser {

    private static final Set<String> ITEM_EVENTS = Set.of(
        "ITEM_PURCHASED", "ITEM_SOLD", "ITEM_UNDO", "ITEM_DESTROYED");

    private ArenaGameParser() {}

    public static ParsedArenaGame parse(Match match, Participant participant, ArenaItemCatalog catalog) {
        if (match == null || participant == null || catalog == null)
            throw new IllegalArgumentException("Match, participant and catalog are required");

        Object timeline = match.events != null ? match.events : match.eventData;
        List<Event> events = readEvents(timeline, participant);
        List<Event> acquisitions = acquisitions(events);
        LinkedHashSet<Integer> prismatics = new LinkedHashSet<>();
        LinkedHashSet<Integer> boots = new LinkedHashSet<>();
        List<Integer> legendaryItems = new ArrayList<>();
        Set<Integer> seenLegendaries = new HashSet<>();

        for (Event event : acquisitions) {
            if (event.involves(ArenaItemCatalog.PRISMATIC_ANVIL)) continue;
            int id = event.acquiredId();
            Kind kind = catalog.kind(match.patch, id);
            if (kind == Kind.PRISMATIC) prismatics.add(id);
            else if (kind == Kind.BOOTS) boots.add(id);
            else if (kind == Kind.ITEM && seenLegendaries.add(id)) legendaryItems.add(id);
        }

        int[] finalItems = finalItems(participant);
        LinkedHashSet<Integer> finalPrismatics = new LinkedHashSet<>();
        LinkedHashSet<Integer> finalBoots = new LinkedHashSet<>();
        for (int id : finalItems) {
            if (id <= 0 || id == ArenaItemCatalog.PRISMATIC_ANVIL) continue;
            Kind kind = catalog.kind(match.patch, id);
            if (kind == Kind.PRISMATIC) finalPrismatics.add(id);
            else if (kind == Kind.BOOTS) finalBoots.add(id);
        }

        Integer firstPrismatic = prismatics.isEmpty() ? null : prismatics.iterator().next();
        boolean fallback = false;
        if (firstPrismatic == null && !finalPrismatics.isEmpty()) {
            firstPrismatic = finalPrismatics.iterator().next();
            fallback = true;
        }
        prismatics.addAll(finalPrismatics);

        Integer boot = catalog.kind(match.patch, participant.boots) == Kind.BOOTS ? participant.boots : null;
        if (boot == null && finalBoots.size() == 1) boot = finalBoots.iterator().next();
        if (boot == null && boots.size() == 1) boot = boots.iterator().next();

        List<Augment> augments = new ArrayList<>();
        if (participant.augments != null) for (int index = 0; index < participant.augments.size(); index++) {
            Integer id = participant.augments.get(index);
            if (id != null && id > 0) augments.add(new Augment(id, index + 1));
        }

        return new ParsedArenaGame(participant.win || participant.subTeamPlacement >= 3,
            boot, firstPrismatic, fallback, List.copyOf(prismatics), legendaryItems, augments,
            !legendaryItems.isEmpty());
    }

    // ============================================================================

    private static List<Event> readEvents(Object raw, Participant participant) {
        JSONObject root = json(raw);
        JSONArray items = array(root, "item_events");
        if (items == null) return List.of();
        JSONObject references = object(root, "participants");
        if (!validReferences(references, participant)) return List.of();

        List<Event> events = new ArrayList<>();
        Set<EventIdentity> seen = new HashSet<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject source = items.optJSONObject(index);
            if (source == null) continue;
            Integer actor = integer(source.opt("participant"));
            Integer actorId = integer(source.opt("participantId"));
            if (actor == null || actor <= 0 || actor != participant.id
                    || source.has("participantId") && !Objects.equals(actor, actorId)) continue;

            String type = source.optString("event");
            Integer item = integer(source.opt("item"));
            Integer before = integer(source.opt("before"));
            Integer after = integer(source.opt("after"));
            Long timestamp = time(source.opt("timestamp"));
            if (!ITEM_EVENTS.contains(type) || item == null || before == null || after == null
                    || item < 0 || before < 0 || after < 0 || timestamp == null) continue;

            Event event = new Event(index, type, item, before, after, timestamp);
            if (seen.add(new EventIdentity(type, item, before, after, timestamp))) events.add(event);
        }
        events.sort(Comparator.comparingLong(Event::timestamp).thenComparingInt(Event::index));
        return events;
    }

    private static List<Event> acquisitions(List<Event> events) {
        List<Event> result = new ArrayList<>();
        Event previous = null;
        for (Event event : events) {
            if ("ITEM_PURCHASED".equals(event.type()) && event.acquiredId() > 0
                    && !(event.item() > 0 && event.after() > 0 && event.item() != event.after())) {
                result.add(event);
            } else if ("ITEM_UNDO".equals(event.type()) && previous != null
                    && "ITEM_PURCHASED".equals(previous.type()) && event.identifies(previous)) {
                result.remove(previous);
            }
            previous = event;
        }
        return result;
    }

    private static boolean validReferences(JSONObject references, Participant participant) {
        if (references == null) return true;
        if (participant.id <= 0 || participant.puuid == null || participant.puuid.isBlank()
                || !participant.puuid.equals(references.optString(String.valueOf(participant.id)))) return false;
        for (String key : references.keySet()) if (!key.equals(String.valueOf(participant.id))
                && !"0".equals(key) && participant.puuid.equals(references.optString(key))) return false;
        return true;
    }

    private static int[] finalItems(Participant participant) {
        return new int[]{participant.item0, participant.item1, participant.item2,
            participant.item3, participant.item4, participant.item5};
    }

    private static Integer integer(Object raw) {
        if (!(raw instanceof Number number)) return null;
        long value = number.longValue();
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
            && number.doubleValue() == value ? (int) value : null;
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
        if (value instanceof JSONObject object) return object;
        if (value instanceof Map<?, ?> map) return new JSONObject(map);
        return null;
    }

    private static JSONArray array(JSONObject root, String key) {
        if (root == null) return null;
        Object value = root.opt(key);
        if (value instanceof JSONArray array) return array;
        if (value instanceof String text) try { return new JSONArray(text); }
        catch (RuntimeException ignored) { return null; }
        return null;
    }

    private record EventIdentity(String type, int item, int before, int after, long timestamp) {}
    private record Event(int index, String type, int item, int before, int after, long timestamp) {
        private int acquiredId() { return after > 0 ? after : item; }
        private boolean involves(int id) { return item == id || before == id || after == id; }
        private boolean identifies(Event target) {
            Set<Integer> ids = new HashSet<>();
            if (item > 0) ids.add(item);
            if (before > 0) ids.add(before);
            if (after > 0) ids.add(after);
            if (ids.isEmpty()) return false;
            for (int id : ids) if (!target.involves(id)) return false;
            return true;
        }
    }
}
