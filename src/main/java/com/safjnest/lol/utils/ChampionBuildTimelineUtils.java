package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ChampionBuildTimelineUtils {

    private static final long STARTER_WINDOW_MILLIS = 2 * 60 * 1000L;

    private ChampionBuildTimelineUtils() {}

    public static List<SkillUpgrade> skillUpgrades(Object rawEvents, String puuid) {
        if (puuid == null || puuid.isBlank()) return List.of();
        JSONObject events = json(rawEvents);
        if (events == null) return List.of();
        JSONObject refs = object(events, "participants");
        JSONArray skills = array(events, "skill_events");
        if (refs == null || skills == null) return List.of();

        List<LevelEvent> levels = new ArrayList<>();
        JSONArray levelEvents = array(events, "level_events");
        if (levelEvents != null) for (int i = 0; i < levelEvents.length(); i++) {
            JSONObject level = levelEvents.optJSONObject(i);
            if (level == null || !puuid.equals(resolve(level.opt("participant"), refs))) continue;
            long timestamp = level.optLong("timestamp", -1);
            int value = level.optInt("level", 0);
            if (timestamp >= 0 && value > 0) levels.add(new LevelEvent(timestamp, value));
        }
        levels.sort(Comparator.comparingLong(LevelEvent::timestampMillis));

        List<SkillUpgrade> result = new ArrayList<>();
        for (int i = 0; i < skills.length(); i++) {
            JSONObject skill = skills.optJSONObject(i);
            if (skill == null || !puuid.equals(resolve(skill.opt("participant"), refs))) continue;
            long timestamp = skill.optLong("timestamp", -1);
            int slot = skill.optInt("skill_slot", 0);
            if (timestamp < 0 || slot < 1 || slot > 4) continue;
            Integer championLevel = null;
            for (LevelEvent level : levels) {
                if (level.timestampMillis() > timestamp) break;
                championLevel = level.level();
            }
            result.add(new SkillUpgrade(championLevel, slot, timestamp));
        }
        result.sort(Comparator.comparingLong(SkillUpgrade::timestampMillis));
        return List.copyOf(result);
    }

    public static List<ItemEvent> itemEvents(Object rawEvents, String puuid) {
        return itemEvents(rawEvents, puuid, Long.MAX_VALUE);
    }

    public static List<ItemEvent> starterItemEvents(Object rawEvents, String puuid) {
        return itemEvents(rawEvents, puuid, STARTER_WINDOW_MILLIS);
    }

    private static List<ItemEvent> itemEvents(Object rawEvents, String puuid, long beforeTimestamp) {
        if (puuid == null || puuid.isBlank()) return List.of();
        JSONObject events = json(rawEvents);
        if (events == null) return List.of();
        JSONObject refs = object(events, "participants");
        JSONArray items = array(events, "item_events");
        if (refs == null || items == null) return List.of();

        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && item.optLong("timestamp", -1) >= 0
                    && item.optLong("timestamp", -1) < beforeTimestamp
                    && puuid.equals(resolve(item.opt("participant"), refs))) ordered.add(item);
        }
        ordered.sort(Comparator.comparingLong(item -> item.optLong("timestamp", Long.MAX_VALUE)));

        List<ItemAcquisition> active = new ArrayList<>();
        List<ItemAcquisition> previous = null;
        for (JSONObject event : ordered) {
            String type = event.optString("event", "");
            long timestamp = event.optLong("timestamp", -1);
            int item = event.optInt("item", 0);
            int before = event.optInt("before", 0);
            int after = event.optInt("after", 0);
            if (timestamp < 0) continue;
            if ("ITEM_UNDO".equals(type)) {
                if (previous != null) {
                    active = previous;
                    previous = null;
                } else if (after > 0) {
                    removeLast(active, after);
                } else if (before > 0 && active.stream().noneMatch(acquisition -> acquisition.itemId() == before)) {
                    active.add(new ItemAcquisition(before, timestamp));
                }
                continue;
            }

            previous = new ArrayList<>(active);
            if ("ITEM_PURCHASED".equals(type)) {
                if (before > 0 && after > 0 && before != after) removeLast(active, before);
                int acquired = after > 0 ? after : item;
                if (acquired > 0) active.add(new ItemAcquisition(acquired, timestamp));
            } else if ("ITEM_SOLD".equals(type) || "ITEM_DESTROYED".equals(type)) {
                removeLast(active, before > 0 ? before : item);
            }
        }
        active.sort(Comparator.comparingLong(ItemAcquisition::timestampMillis));
        List<ItemEvent> result = new ArrayList<>(active.size());
        for (ItemAcquisition acquisition : active)
            result.add(new ItemEvent(acquisition.itemId(), acquisition.timestampMillis()));
        return List.copyOf(result);
    }

    private static void removeLast(List<ItemAcquisition> acquisitions, int itemId) {
        for (int i = acquisitions.size() - 1; i >= 0; i--) {
            if (acquisitions.get(i).itemId() == itemId) {
                acquisitions.remove(i);
                return;
            }
        }
    }

    private static JSONObject json(Object raw) {
        if (raw instanceof JSONObject object) return object;
        if (raw instanceof String value && !value.isBlank()) {
            try { return new JSONObject(value); }
            catch (RuntimeException exception) { return null; }
        }
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            try { return new JSONObject(map); }
            catch (RuntimeException exception) { return null; }
        }
        return null;
    }

    private static JSONObject object(JSONObject source, String key) {
        JSONObject value = source.optJSONObject(key);
        if (value != null) return value;
        String raw = source.optString(key, "");
        if (raw.isBlank()) return null;
        try { return new JSONObject(raw); }
        catch (RuntimeException ignored) { return null; }
    }

    private static JSONArray array(JSONObject source, String key) {
        JSONArray value = source.optJSONArray(key);
        if (value != null) return value;
        String raw = source.optString(key, "");
        if (raw.isBlank()) return null;
        try { return new JSONArray(raw); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String resolve(Object id, JSONObject refs) {
        if (id == null || id == JSONObject.NULL) return null;
        String key = String.valueOf(id);
        return refs == null ? key : refs.optString(key, null);
    }

    public record SkillUpgrade(Integer championLevel, int skillSlot, long timestampMillis) {}

    public record ItemEvent(int itemId, long timestampMillis) {}

    private record ItemAcquisition(int itemId, long timestampMillis) {}

    private record LevelEvent(long timestampMillis, int level) {}
}
