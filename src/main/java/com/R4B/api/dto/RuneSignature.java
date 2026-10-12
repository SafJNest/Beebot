package com.R4B.api.dto;

import com.R4B.api.dto.*;
import com.R4B.lol.BuildValues;
import com.R4B.lol.NumberUtils;

import java.util.List;

public record RuneSignature(
        int primaryTree,
        int keystone,
        List<Integer> primaryRunes,
        int secondaryTree,
        List<Integer> secondaryRunes,
        List<Integer> statShards
) {

    public String toKey() {
        String raw = primaryTree + "|" + keystone + "|" + BuildValues.joinInts(primaryRunes) + "|"
                + secondaryTree + "|" + BuildValues.joinInts(secondaryRunes);
        return BuildValues.toBase64(raw);
    }

    public String statShardsKey() {
        return BuildValues.joinInts(statShards);
    }

    public static RuneSignature decode(String key) {
        String[] parts = BuildValues.fromBase64(key).split("\\|", -1);
        return new RuneSignature(
                NumberUtils.parseInt(parts[0]),
                NumberUtils.parseInt(parts[1]),
                parts.length > 2 ? BuildValues.parseDashList(parts[2]) : List.of(),
                NumberUtils.parseInt(parts[3]),
                parts.length > 4 ? BuildValues.parseDashList(parts[4]) : List.of(),
                parts.length > 5 ? BuildValues.parseDashList(parts[5]) : List.of());
    }
}
