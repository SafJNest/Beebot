package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.utils.BuildUtils;

import java.util.ArrayList;
import java.util.List;

public record RuneSignature(
        int primaryTree,
        int keystone,
        List<Integer> primaryRunes,
        int secondaryTree,
        List<Integer> secondaryRunes,
        List<Integer> statShards
) {

    public static RuneSignature from(JSONObject runesJson) {
        JSONArray primary   = runesJson.optJSONArray("primary");
        JSONArray secondary = runesJson.optJSONArray("secondary");
        JSONArray stats     = runesJson.optJSONArray("stats");

        if (primary == null || primary.length() < 2 || secondary == null) return null;

        List<Integer> statList = new ArrayList<>();
        if (stats != null) {
            for (int i = 0; i < stats.length(); i++)
                statList.add(stats.optInt(i, 0));
        }

        return new RuneSignature(
                primary.optInt(0, 0),
                primary.optInt(1, 0),
                BuildUtils.extractSorted(primary, 2),
                secondary.optInt(0, 0),
                BuildUtils.extractSorted(secondary, 1),
                List.copyOf(statList)
        );
    }

    public String toKey() {
        String raw = primaryTree + "|" + keystone + "|" + BuildUtils.joinInts(primaryRunes) + "|"
                + secondaryTree + "|" + BuildUtils.joinInts(secondaryRunes) + "|" + BuildUtils.joinInts(statShards);
        return BuildUtils.toBase64(raw);
    }

    public static RuneSignature decode(String key) {
        String[] p = BuildUtils.fromBase64(key).split("\\|", -1);
        return new RuneSignature(
                safeInt(p, 0),
                safeInt(p, 1),
                p.length > 2 ? BuildUtils.parseDashList(p[2]) : List.of(),
                safeInt(p, 3),
                p.length > 4 ? BuildUtils.parseDashList(p[4]) : List.of(),
                p.length > 5 ? BuildUtils.parseDashList(p[5]) : List.of());
    }

    private static int safeInt(String[] p, int i) {
        if (i >= p.length || p[i] == null || p[i].isBlank()) return 0;
        try { return Integer.parseInt(p[i].trim()); } catch (NumberFormatException e) { return 0; }
    }

}
