package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
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

        int primaryTree  = primary.optInt(0, 0);
        int keystone     = primary.optInt(1, 0);
        int secondaryTree = secondary.optInt(0, 0);

        List<Integer> pRunes = extractSorted(primary, 2);
        List<Integer> sRunes = extractSorted(secondary, 1);

        String statShardsStr = stats != null
                ? IntStream.range(0, stats.length())
                        .mapToObj(i -> String.valueOf(stats.optInt(i, 0)))
                        .collect(Collectors.joining("-"))
                : "";

        return new RuneSignature(
                primaryTree,
                keystone,
                joinInts(pRunes),
                secondaryTree,
                joinInts(sRunes),
                statShardsStr
        );
    }

    public List<Integer> primaryRuneItems()   { return parseDashList(primaryRunes); }
    public List<Integer> secondaryRuneItems() { return parseDashList(secondaryRunes); }
    public List<Integer> statShardItems()     { return parseDashList(statShards); }

    public String toKey() {
        String raw = primaryTree + "|" + keystone + "|" + primaryRunes + "|"
                + secondaryTree + "|" + secondaryRunes + "|" + statShards;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static RuneSignature decode(String key) {
        String[] p = new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8).split("\\|", -1);
        return new RuneSignature(
                Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2],
                Integer.parseInt(p[3]), p[4], p[5]
        );
    }

    private static List<Integer> extractSorted(JSONArray arr, int startIdx) {
        List<Integer> result = new ArrayList<>();
        for (int i = startIdx; i < arr.length(); i++) result.add(arr.optInt(i, 0));
        Collections.sort(result);
        return result;
    }

    static List<Integer> parseDashList(String source) {
        if (source == null || source.isBlank()) return Collections.emptyList();
        return Arrays.stream(source.split("-"))
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    static String joinInts(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }
}
