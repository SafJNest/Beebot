package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.LeagueHandler;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class BuildUtils {

    public static List<Integer> extractSorted(JSONArray arr, int from) {
        List<Integer> list = new ArrayList<>();
        for (int i = from; i < arr.length(); i++) list.add(arr.optInt(i, 0));
        Collections.sort(list);
        return list;
    }
    
    public static List<Integer> parseDashList(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        return Arrays.stream(s.split("-")).filter(t -> !t.isBlank()).map(Integer::parseInt).collect(Collectors.toList());
    }

    public static String joinInts(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }

    public static int readInt(JSONObject obj, String key) {
        return (obj == null || !obj.has(key)) ? 0 : parseAnyInt(obj.opt(key));
    }

    public static int parseAnyInt(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) { try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {} }
        return 0;
    }

    public static String toBase64(String string) {
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64(String string) {
        return new String(Base64.getDecoder().decode(string), StandardCharsets.UTF_8);
    }

    public static List<Integer> toIntList(JSONArray arr) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.getInt(i));
        return list;
    }

    public static String toItemName(List<Integer> ids) {
        String name = "";
        for (int id : ids) {
            Item item = LeagueHandler.itemsMap.get(id);
            if (item != null) name += item.getName() + ", ";
        }
        return name;
    }

    public static String toItemName(int id) {
        Item item = LeagueHandler.itemsMap.get(id);
        return item != null ? item.getName() : null;
    }

    public static String toItemName(String ids) {
        return Arrays.stream(ids.split("-")).map(Integer::parseInt).map(LeagueHandler.itemsMap::get).map(Item::getName).collect(Collectors.joining(", "));
    }
}
