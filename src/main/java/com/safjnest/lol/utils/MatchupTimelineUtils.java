package com.safjnest.lol.utils;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.model.match.Match;

public final class MatchupTimelineUtils {

    public static final long AT_15_MS = 15 * 60 * 1000L;

    private MatchupTimelineUtils() {}

    public static Data read(Match match) {
        if (match == null) return Data.empty();
        Object rawEvents = match.events != null ? match.events : match.eventData;
        return read(rawEvents);
    }

    public static Data read(Object rawEvents) {
        JSONObject events = json(rawEvents);
        if (events == null) return Data.empty();

        JSONObject refs = object(events, "participants");
        Map<String, Snapshot> snapshots = new HashMap<>();
        JSONArray snapshotArray = array(events, "snapshots");
        if (snapshotArray != null && refs != null) for (int i = 0; i < snapshotArray.length(); i++) {
            JSONObject snapshot = snapshotArray.optJSONObject(i);
            if (snapshot == null || snapshot.optInt("minute", -1) != 15) continue;
            JSONObject participants = snapshot.optJSONObject("participants");
            if (participants != null) for (String id : participants.keySet()) {
                String puuid = refs.optString(id, null);
                JSONObject stats = participants.optJSONObject(id);
                if (puuid != null && stats != null) snapshots.put(puuid, new Snapshot(
                    nullableInt(stats, "total_gold"), nullableInt(stats, "cs"),
                    nullableInt(stats, "xp"), nullableInt(stats, "level")));
            }
            break;
        }

        Map<String, Integer> kills = new HashMap<>();
        JSONArray killEvents = array(events, "champion_kills");
        boolean killsAvailable = killEvents != null;
        if (killEvents != null) for (int i = 0; i < killEvents.length(); i++) {
            JSONObject kill = killEvents.optJSONObject(i);
            if (kill == null || kill.optLong("timestamp", Long.MAX_VALUE) > AT_15_MS) continue;
            String killer = resolve(kill.opt("killer"), refs);
            if (killer != null) {
                kills.merge(killer, 1, Integer::sum);
            }
        }

        Map<String, Integer> plates = new HashMap<>();
        JSONArray plateEvents = array(events, "turret_plate_events");
        if (plateEvents != null) for (int i = 0; i < plateEvents.length(); i++) {
            JSONObject plate = plateEvents.optJSONObject(i);
            if (plate == null || plate.optLong("timestamp", Long.MAX_VALUE) > AT_15_MS) continue;
            String team = plate.optString("team", null);
            String lane = plate.optString("lane", null);
            if (team != null && lane != null) plates.merge(team + ':' + lane, 1, Integer::sum);
        }
        return new Data(snapshots, kills, plates, killsAvailable);
    }

    public static boolean hasTimeline(Object rawEvents) {
        JSONObject events = json(rawEvents);
        if (events == null) return false;
        JSONObject participants = object(events, "participants");
        if (participants == null || participants.keySet().isEmpty()) return false;
        return array(events, "snapshots") != null || array(events, "champion_kills") != null
            || array(events, "turret_plate_events") != null || array(events, "item_events") != null
            || array(events, "skill_events") != null || array(events, "level_events") != null;
    }

    public static int plates(Data data, String team, String lane) {
        if (data == null || team == null || lane == null) return 0;
        String mapLane = "UTILITY".equals(lane) ? "BOT" : lane;
        return data.plates().getOrDefault(team + ':' + mapLane, 0);
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

    private static Integer nullableInt(JSONObject object, String key) {
        return object.has(key) && !object.isNull(key) ? object.optInt(key) : null;
    }

    public record Snapshot(Integer gold, Integer cs, Integer xp, Integer level) {}

    public record Data(Map<String, Snapshot> snapshots, Map<String, Integer> kills,
                       Map<String, Integer> plates, boolean killsAvailable) {
        private static Data empty() { return new Data(Map.of(), Map.of(), Map.of(), false); }
    }
}
