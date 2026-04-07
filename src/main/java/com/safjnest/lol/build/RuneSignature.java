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
        JSONArray primary = runesJson.optJSONArray("primary");
        JSONArray secondary = runesJson.optJSONArray("secondary");
        JSONArray stats = runesJson.optJSONArray("stats");

        if (primary == null || primary.length() < 2 || secondary == null) return null;

        int primaryTree = primary.optInt(0, 0);
        int keystone = primary.optInt(1, 0);

        List<Integer> pRunes = new ArrayList<>();
        for (int i = 2; i < primary.length(); i++) pRunes.add(primary.optInt(i, 0));
        Collections.sort(pRunes);

        int secondaryTree = secondary.optInt(0, 0);
        List<Integer> sRunes = new ArrayList<>();
        for (int i = 1; i < secondary.length(); i++) sRunes.add(secondary.optInt(i, 0));
        Collections.sort(sRunes);

        String statShardsStr = stats != null
                ? IntStream.range(0, stats.length()).mapToObj(i -> String.valueOf(stats.optInt(i, 0))).collect(Collectors.joining("-"))
                : "";

        return new RuneSignature(
                primaryTree,
                keystone,
                pRunes.stream().map(String::valueOf).collect(Collectors.joining("-")),
                secondaryTree,
                sRunes.stream().map(String::valueOf).collect(Collectors.joining("-")),
                statShardsStr
        );
    }

    public String toKey() {
        String raw = primaryTree + "|" + keystone + "|" + primaryRunes + "|" + secondaryTree + "|" + secondaryRunes + "|" + statShards;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static RuneSignature decode(String key) {
        String[] p = new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8).split("\\|", -1);
        return new RuneSignature(Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2], Integer.parseInt(p[3]), p[4], p[5]);
    }
}
