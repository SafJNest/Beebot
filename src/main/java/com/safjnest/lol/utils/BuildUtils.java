package com.safjnest.lol.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Augment;

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

    public static List<Integer> jsonArrayToIntList(JSONArray arr) {
        if (arr == null) return List.of();
        List<Integer> list = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) list.add(parseAnyInt(arr.opt(i)));
        return list;
    }

    public static JSONArray intListToJsonArray(List<Integer> ids) {
        JSONArray a = new JSONArray();
        if (ids != null) for (int id : ids) a.put(id);
        return a;
    }

    public static String toItemName(List<Integer> ids) {
        String name = "";
        for (int id : ids) {
            Item item = ItemUtils.getItem(id);
            if (item != null) name += item.getName() + ", ";
        }
        return name;
    }

    public static String toItemName(int id) {
        Item item = ItemUtils.getItem(id);
        return item != null ? item.getName() : null;
    }

    public static String toAugmentName(int id) {
        String name = "";
        Augment item = LeagueHandler.getAugments().stream().filter(a -> a.id().equals(String.valueOf(id))).findFirst().orElse(null);
        if (item != null) name += item.name() + ", ";
        else name += "Unknown Augment (" + id + ")";
        return name;
    }

    public static String toItemName(String ids) {
        return Arrays.stream(ids.split("-")).map(Integer::parseInt).map(ItemUtils::getItem).map(Item::getName).collect(Collectors.joining(", "));
    }
}
