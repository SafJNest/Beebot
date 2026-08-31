package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.champion.ChampionStatsData;
import com.safjnest.lol.model.match.Match;

public final class MatchMemoryUtils {

    private MatchMemoryUtils() {}

    public static void release(Match match) {
        if (match == null) return;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        release(match, seen);
    }

    public static void release(ChampionStatsData.RawMatch match) {
        if (match == null) return;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        release(match, seen);
    }

    public static void release(Object value) {
        if (value == null) return;
        release(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    // ============================================================================

    private static void release(Match match, Set<Object> seen) {
        if (!seen.add(match)) return;
        release(match.events, seen);
        release(match.eventData, seen);
        release(match.bans, seen);
        release(match.participants, seen);
        match.events = null;
        match.eventData = null;
        match.bans = null;
        match.participants = null;
    }

    private static void release(ChampionStatsData.RawMatch match, Set<Object> seen) {
        if (!seen.add(match)) return;
        if (match.metadata() != null) {
            release(match.metadata().events(), seen);
            release(match.metadata().bans(), seen);
        }
        release(match.participants(), seen);
    }

    private static void release(Object value, Set<Object> seen) {
        if (value == null) return;
        if (value instanceof Match match) {
            release(match, seen);
            return;
        }
        if (value instanceof ChampionStatsData.RawMatch match) {
            release(match, seen);
            return;
        }
        if (!seen.add(value)) return;
        if (value instanceof JSONObject object) {
            for (String key : new ArrayList<>(object.keySet())) release(object.opt(key), seen);
            object.keySet().clear();
            return;
        }
        if (value instanceof JSONArray array) {
            for (int index = array.length() - 1; index >= 0; index--) {
                release(array.opt(index), seen);
                array.remove(index);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : new ArrayList<>(map.values())) release(nested, seen);
            clear(map);
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object nested : new ArrayList<>(collection)) release(nested, seen);
            clear(collection);
        }
    }

    private static void clear(Map<?, ?> values) {
        try {
            values.clear();
        } catch (UnsupportedOperationException ignored) {}
    }

    private static void clear(Collection<?> values) {
        try {
            values.clear();
        } catch (UnsupportedOperationException ignored) {}
    }
}
