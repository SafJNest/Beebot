package com.R4B.lol;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
public final class BuildValues {
    
    public static List<Integer> parseDashList(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        return Arrays.stream(s.split("-")).filter(t -> !t.isBlank()).map(Integer::parseInt).collect(Collectors.toList());
    }

    public static String joinInts(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }

    public static String toBase64(String string) {
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64(String string) {
        return new String(Base64.getDecoder().decode(string), StandardCharsets.UTF_8);
    }
}
