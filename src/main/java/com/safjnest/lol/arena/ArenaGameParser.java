package com.safjnest.lol.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.arena.ParsedArenaGame.Augment;
import com.safjnest.lol.arena.ParsedArenaGame.Core;
import com.safjnest.lol.arena.ParsedArenaGame.Coverage;
import com.safjnest.lol.arena.ParsedArenaGame.Item;
import com.safjnest.lol.arena.ParsedArenaGame.Prismatic;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.utils.ChampionBuildTimelineUtils;

public final class ArenaGameParser {

    private static final int ARENA_EVIDENCE_VERSION = 1;
    private static final int FIRST_PRISMATIC_INVALID_ID = 220007;
    private static final int MAX_ARENA_PLACEMENT = 8;

    private ArenaGameParser() {}

    public static ParsedArenaGame parse(Match match, Participant participant) {
        Counters counters = new Counters();
        if (match == null || participant == null) {
            counters.missing("MATCH_OR_PARTICIPANT_ABSENT");
            return result(null, false, null, List.of(), List.of(), List.of(), counters);
        }

        int observedPlacement = participant.subTeamPlacement;
        Integer placement = observedPlacement > 0 ? observedPlacement : null;
        boolean validPlacement = observedPlacement >= 1 && observedPlacement <= MAX_ARENA_PLACEMENT;
        if (!validPlacement) counters.missing("PLACEMENT_INVALID_OR_ABSENT");
        boolean win = participant.win || observedPlacement >= 3;

        List<Augment> augments = readAugments(participant, counters);
        JSONObject evidence = arenaParticipantEvidence(match.eventData, participant.id, counters);
        List<Prismatic> prismatics = readPrismatic(evidence, counters);
        Core core = readCore(evidence, prismatics, counters);
        List<Item> items = readPostCoreItems(match.eventData, participant, core, counters);

        return result(placement, win, core, items, augments, prismatics, counters);
    }

    private static ParsedArenaGame result(Integer placement, boolean win, Core core, List<Item> items,
            List<Augment> augments, List<Prismatic> prismatics, Counters counters) {
        return new ParsedArenaGame(placement, win, core, items, augments, prismatics, counters.snapshot());
    }

    private static List<Augment> readAugments(Participant participant, Counters counters) {
        List<Integer> augments = participant.augments;
        if (augments == null || augments.isEmpty()) {
            counters.missing("AUGMENTS_ABSENT_OR_EMPTY");
            return List.of();
        }
        List<Augment> result = new ArrayList<>();
        for (int index = 0; index < augments.size(); index++) {
            Integer id = augments.get(index);
            if (id == null || id <= 0) {
                counters.ambiguous("AUGMENT_ID_INVALID");
            } else {
                result.add(new Augment(id, index + 1, null));
            }
        }
        return List.copyOf(result);
    }

    private static JSONObject arenaParticipantEvidence(Object rawEvents, int participantId, Counters counters) {
        JSONObject events = json(rawEvents);
        if (events == null) {
            counters.missing("MATCH_EVENTS_ABSENT_OR_INVALID");
            return null;
        }
        JSONObject evidence = events.optJSONObject("arena_evidence");
        if (evidence == null) {
            counters.missing("ARENA_EVIDENCE_ABSENT");
            return null;
        }
        if (evidence.optInt("version", -1) != ARENA_EVIDENCE_VERSION) {
            counters.ambiguous("ARENA_EVIDENCE_VERSION_UNSUPPORTED");
            return null;
        }
        JSONObject participants = evidence.optJSONObject("participants");
        JSONObject participantEvidence = participants == null ? null : participants.optJSONObject(String.valueOf(participantId));
        if (participantEvidence == null) counters.missing("ARENA_PARTICIPANT_EVIDENCE_ABSENT");
        return participantEvidence;
    }

    private static List<Prismatic> readPrismatic(JSONObject evidence, Counters counters) {
        JSONObject source = evidence == null ? null : evidence.optJSONObject("first_prismatic");
        if (source == null) {
            counters.missing("FIRST_PRISMATIC_EVIDENCE_ABSENT");
            return List.of();
        }
        String status = source.optString("status", "");
        if ("UNKNOWN".equals(status)) {
            counters.missing(reason(source, "FIRST_PRISMATIC_UNKNOWN"));
            return List.of();
        }
        if ("AMBIGUOUS".equals(status)) {
            counters.ambiguous(reason(source, "FIRST_PRISMATIC_AMBIGUOUS"));
            return List.of();
        }
        if (!"EXACT".equals(status)) {
            counters.ambiguous("FIRST_PRISMATIC_STATUS_INVALID");
            return List.of();
        }
        Integer id = integerField(source, "item_id");
        Long timestamp = longField(source, "timestamp");
        if (id == null || id <= 0 || timestamp == null || timestamp < 0) {
            counters.ambiguous("FIRST_PRISMATIC_DIRECT_FIELDS_INVALID");
            return List.of();
        }
        if (id == FIRST_PRISMATIC_INVALID_ID) {
            counters.rejected("FIRST_PRISMATIC_220007_EXCLUDED");
            return List.of();
        }
        return List.of(new Prismatic(id, timestamp));
    }

    private static Core readCore(JSONObject evidence, List<Prismatic> prismatics, Counters counters) {
        JSONObject source = evidence == null ? null : evidence.optJSONObject("core_snapshot");
        if (source == null) {
            counters.missing("CORE_SNAPSHOT_EVIDENCE_ABSENT");
            return null;
        }
        String status = source.optString("status", "");
        if ("UNKNOWN".equals(status)) {
            counters.missing(reason(source, "CORE_SNAPSHOT_UNKNOWN"));
            return null;
        }
        if ("AMBIGUOUS".equals(status)) {
            counters.ambiguous(reason(source, "CORE_SNAPSHOT_AMBIGUOUS"));
            return null;
        }
        if (!"EXACT".equals(status)) {
            counters.ambiguous("CORE_SNAPSHOT_STATUS_INVALID");
            return null;
        }
        Long timestamp = longField(source, "timestamp");
        Integer bootsId = integerField(source, "boots_id");
        JSONArray rawItems = source.optJSONArray("item_ids");
        if (timestamp == null || timestamp < 0 || bootsId == null || bootsId <= 0
                || bootsId == FIRST_PRISMATIC_INVALID_ID || rawItems == null) {
            counters.ambiguous("CORE_SNAPSHOT_DIRECT_FIELDS_INVALID");
            return null;
        }
        List<Integer> snapshotItems = new ArrayList<>();
        for (int index = 0; index < rawItems.length(); index++) {
            Object rawId = rawItems.opt(index);
            Integer id = integerValue(rawId);
            if (id == null || id <= 0) {
                counters.ambiguous("CORE_SNAPSHOT_ITEM_ID_INVALID");
                return null;
            }
            if (id != FIRST_PRISMATIC_INVALID_ID) snapshotItems.add(id);
        }
        Collections.sort(snapshotItems);
        if (prismatics.isEmpty()) {
            counters.missing("CORE_WITHOUT_EXACT_FIRST_PRISMATIC");
            return null;
        }
        Prismatic firstPrismatic = prismatics.get(0);
        if (timestamp < firstPrismatic.timestampMillis()) {
            counters.ambiguous("CORE_SNAPSHOT_PRECEDES_FIRST_PRISMATIC");
            return null;
        }
        if (snapshotItems.isEmpty()) {
            counters.ambiguous("CORE_SNAPSHOT_ITEMS_EMPTY");
            return null;
        }
        if (!snapshotItems.contains(bootsId)) {
            counters.ambiguous("CORE_SNAPSHOT_BOOT_NOT_IN_ITEM_IDS");
            return null;
        }
        return new Core(firstPrismatic.id(), bootsId, List.copyOf(snapshotItems),
                firstPrismatic.timestampMillis(), timestamp);
    }

    private static List<Item> readPostCoreItems(Object rawEvents, Participant participant, Core core, Counters counters) {
        if (core == null) {
            counters.missing("POST_CORE_SEQUENCE_REQUIRES_EXACT_CORE");
            return List.of();
        }
        JSONObject events = json(rawEvents);
        JSONArray rawItems = events == null ? null : events.optJSONArray("item_events");
        JSONObject refs = events == null ? null : events.optJSONObject("participants");
        if (rawItems == null || refs == null || participant.puuid == null || participant.puuid.isBlank()) {
            counters.missing("POST_CORE_ITEM_EVENTS_OR_ATTRIBUTION_ABSENT");
            return List.of();
        }
        if (hasUnresolvedPostCoreTransition(rawItems, refs, participant.puuid, core)) {
            counters.ambiguous("POST_CORE_ITEM_TRANSITION_UNRESOLVED");
            return List.of();
        }
        List<ChampionBuildTimelineUtils.ItemEvent> normalized = ChampionBuildTimelineUtils.itemEvents(rawEvents, participant.puuid);
        TreeSet<Integer> coreIds = new TreeSet<>(core.snapshotItemIds());
        coreIds.add(core.bootsId());
        coreIds.add(core.firstPrismaticId());
        List<ChampionBuildTimelineUtils.ItemEvent> postCore = new ArrayList<>();
        for (ChampionBuildTimelineUtils.ItemEvent item : normalized) {
            if (item.timestampMillis() <= core.snapshotTimestampMillis()
                    || item.itemId() == FIRST_PRISMATIC_INVALID_ID || coreIds.contains(item.itemId())) continue;
            postCore.add(item);
        }
        postCore.sort(Comparator.comparingLong(ChampionBuildTimelineUtils.ItemEvent::timestampMillis));
        List<Item> result = new ArrayList<>(postCore.size());
        for (int index = 0; index < postCore.size(); index++) {
            ChampionBuildTimelineUtils.ItemEvent item = postCore.get(index);
            result.add(new Item(item.itemId(), index + 1, item.timestampMillis()));
        }
        return List.copyOf(result);
    }

    private static boolean hasUnresolvedPostCoreTransition(
            JSONArray events, JSONObject refs, String puuid, Core core) {
        long boundary = core.snapshotTimestampMillis();
        TreeSet<Integer> coreIds = new TreeSet<>(core.snapshotItemIds());
        coreIds.add(core.bootsId());
        coreIds.add(core.firstPrismaticId());
        for (int index = 0; index < events.length(); index++) {
            JSONObject event = events.optJSONObject(index);
            if (event == null || !puuid.equals(resolve(event.opt("participant"), refs))
                    || event.optLong("timestamp", -1) <= boundary) continue;
            String type = event.optString("event", "");
            if (!List.of("ITEM_PURCHASED", "ITEM_UNDO", "ITEM_SOLD", "ITEM_DESTROYED").contains(type)) return true;
            int item = event.optInt("item", 0);
            int before = event.optInt("before", 0);
            int after = event.optInt("after", 0);
            if ("ITEM_PURCHASED".equals(type)) {
                if (before > 0 && after > 0 && before != after) return true;
                int acquired = after > 0 ? after : item;
                if (acquired != FIRST_PRISMATIC_INVALID_ID && coreIds.contains(acquired)) return true;
            }
            if (("ITEM_SOLD".equals(type) || "ITEM_DESTROYED".equals(type))) {
                int removed = before > 0 ? before : item;
                if (removed != FIRST_PRISMATIC_INVALID_ID && coreIds.contains(removed)) return true;
            }
        }
        return false;
    }

    private static String resolve(Object id, JSONObject refs) {
        if (id == null || id == JSONObject.NULL) return null;
        return refs.optString(String.valueOf(id), null);
    }

    private static String reason(JSONObject evidence, String fallback) {
        String reason = evidence.optString("reason", "");
        return reason.isBlank() ? fallback : reason;
    }

    private static Integer integerField(JSONObject source, String key) {
        return integerValue(source.opt(key));
    }

    private static Integer integerValue(Object raw) {
        if (!(raw instanceof Number number)) return null;
        long value = number.longValue();
        return number.doubleValue() == value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? (int) value : null;
    }

    private static Long longField(JSONObject source, String key) {
        Object raw = source.opt(key);
        if (!(raw instanceof Number number)) return null;
        long value = number.longValue();
        return number.doubleValue() == value ? value : null;
    }

    private static JSONObject json(Object raw) {
        if (raw instanceof JSONObject object) return object;
        if (raw instanceof String value && !value.isBlank()) {
            try { return new JSONObject(value); }
            catch (RuntimeException ignored) { return null; }
        }
        if (raw instanceof Map<?, ?> map) {
            try { return new JSONObject(map); }
            catch (RuntimeException ignored) { return null; }
        }
        return null;
    }

    private static final class Counters {
        private final Map<String, Integer> missing = new HashMap<>();
        private final Map<String, Integer> ambiguous = new HashMap<>();
        private final Map<String, Integer> rejected = new HashMap<>();

        private void missing(String reason) { missing.merge(reason, 1, Integer::sum); }
        private void ambiguous(String reason) { ambiguous.merge(reason, 1, Integer::sum); }
        private void rejected(String reason) { rejected.merge(reason, 1, Integer::sum); }

        private Coverage snapshot() {
            return new Coverage(total(missing), total(ambiguous), total(rejected), missing, ambiguous, rejected);
        }

        private int total(Map<String, Integer> values) {
            return values.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
