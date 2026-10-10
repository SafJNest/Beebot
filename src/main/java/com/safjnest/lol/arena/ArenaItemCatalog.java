package com.safjnest.lol.arena;

import java.util.HashMap;
import java.util.Map;

import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.utils.ItemUtils;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

public final class ArenaItemCatalog {

    public static final int PRISMATIC_ANVIL = 220007;
    private static final int PRISMATIC_EXCEPTION = 447111;

    private final Map<Integer, Kind> kinds;
    private final Map<String, Map<Integer, Classification>> classifications;

    public ArenaItemCatalog(Map<Integer, Kind> kinds) {
        this(kinds, Map.of());
    }

    public ArenaItemCatalog(Map<Integer, Kind> kinds,
            Map<String, Map<Integer, Classification>> classifications) {
        this.kinds = Map.copyOf(kinds == null ? Map.of() : kinds);
        Map<String, Map<Integer, Classification>> copy = new HashMap<>();
        if (classifications != null) for (Map.Entry<String, Map<Integer, Classification>> entry : classifications.entrySet()) {
            String patch = patchMajor(entry.getKey());
            if (patch != null && entry.getValue() != null) copy.put(patch, Map.copyOf(entry.getValue()));
        }
        this.classifications = Map.copyOf(copy);
    }

    public static ArenaItemCatalog fromItems(Map<Integer, Item> items) {
        Map<Integer, Kind> kinds = new HashMap<>();
        if (items != null) for (Map.Entry<Integer, Item> entry : items.entrySet()) {
            Item item = entry.getValue();
            if (item == null) continue;
            if (ItemUtils.isBoots(item)) kinds.put(entry.getKey(), Kind.BOOTS);
            else if (item.getDepth() == 3) kinds.put(entry.getKey(), Kind.ITEM);
        }
        return new ArenaItemCatalog(kinds);
    }

    public static ArenaItemCatalog fromItems(Map<Integer, Item> items,
            Map<String, Map<Integer, Classification>> classifications) {
        ArenaItemCatalog catalog = fromItems(items);
        return new ArenaItemCatalog(catalog.kinds, classifications);
    }

    public Kind kind(String patch, int itemId) {
        if (itemId <= 0 || itemId == PRISMATIC_ANVIL) return null;
        Classification override = classification(patch, itemId);
        if (override == Classification.UNKNOWN) return null;
        if (override == Classification.PRISMATIC || override == null && isPrismatic(itemId)) return Kind.PRISMATIC;
        return kinds.get(itemId);
    }

    public boolean isPrismatic(String patch, int itemId) {
        return kind(patch, itemId) == Kind.PRISMATIC;
    }

    private Classification classification(String patch, int itemId) {
        String major = patchMajor(patch);
        Classification value = major == null ? null : classifications.getOrDefault(major, Map.of()).get(itemId);
        if (value != null) return value;
        if (itemId == PRISMATIC_EXCEPTION) return Classification.NON_PRISMATIC;
        return null;
    }

    private static boolean isPrismatic(int itemId) {
        return itemId != PRISMATIC_EXCEPTION && ItemUtils.isPrismatic(itemId);
    }

    private static String patchMajor(String patch) {
        if (patch == null || patch.isBlank()) return null;
        String value = patch.trim();
        int separator = value.indexOf('.', value.indexOf('.') + 1);
        return separator < 0 ? value : value.substring(0, separator);
    }

    public enum Classification { PRISMATIC, NON_PRISMATIC, UNKNOWN }
}
