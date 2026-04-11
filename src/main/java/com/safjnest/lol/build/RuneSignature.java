package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record RuneSignature(
        int primaryTree,
        int keystone,
        String primaryRunes,
        int secondaryTree,
        String secondaryRunes,
        String statShards
) {

    public static RuneSignature from(JSONObject runesJson) {
        JSONArray primary   = runesJson.optJSONArray("primary");
        JSONArray secondary = runesJson.optJSONArray("secondary");
        JSONArray stats     = runesJson.optJSONArray("stats");

        if (primary == null || primary.length() < 2 || secondary == null) return null;

        String shards = "";
        if (stats != null) {
            shards = IntStream.range(0, stats.length()).mapToObj(i -> String.valueOf(stats.optInt(i, 0))).collect(Collectors.joining("-"));
        }

        return new RuneSignature(
                primary.optInt(0, 0),
                primary.optInt(1, 0),
                BuildUtils.joinInts(BuildUtils.extractSorted(primary, 2)),
                secondary.optInt(0, 0),
                BuildUtils.joinInts(BuildUtils.extractSorted(secondary, 1)),
                shards
        );
    }

    public List<Integer> primaryRuneItems()   { return BuildUtils.parseDashList(primaryRunes); }
    public List<Integer> secondaryRuneItems() { return BuildUtils.parseDashList(secondaryRunes); }
    public List<Integer> statShardItems()     { return BuildUtils.parseDashList(statShards); }

    public String toKey() {
        String raw = primaryTree + "|" + keystone + "|" + primaryRunes + "|" + secondaryTree + "|" + secondaryRunes + "|" + statShards;
        return BuildUtils.toBase64(raw);
    }

    public static RuneSignature decode(String key) {
        String[] p = BuildUtils.fromBase64(key).split("\\|", -1);
        return new RuneSignature(Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]), p[4], p[5]);
    }

}
