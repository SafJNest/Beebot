package com.safjnest.lol.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.utils.ItemUtils;

public final class ArenaItemCatalog {

    public static final int PRISMATIC_ANVIL = 220007;
    private final Map<Integer, Kind> kinds;
    private final Set<Integer> ignored;
    private final PrismaticItemClassifier prismatics;

    public ArenaItemCatalog(Map<Integer, Kind> kinds, PrismaticItemClassifier prismatics) {
        this(kinds, Set.of(), prismatics);
    }

    private ArenaItemCatalog(Map<Integer, Kind> kinds, Set<Integer> ignored, PrismaticItemClassifier prismatics) {
        this.kinds = Map.copyOf(kinds);
        this.ignored = Set.copyOf(ignored);
        this.prismatics = java.util.Objects.requireNonNull(prismatics);
    }

    public static ArenaItemCatalog fromItems(Map<Integer, no.stelar7.api.r4j.pojo.lol.staticdata.item.Item> items,
            PrismaticItemClassifier prismatics) {
        Map<Integer, Kind> kinds = new HashMap<>();
        Set<Integer> ignored = new HashSet<>();
        for (var entry : items.entrySet()) {
            var item = entry.getValue();
            if (ItemUtils.isBoots(item)) kinds.put(entry.getKey(), Kind.BOOTS);
            else if (item != null && item.getDepth() == 3) kinds.put(entry.getKey(), Kind.ITEM);
            else ignored.add(entry.getKey());
        }
        return new ArenaItemCatalog(kinds, ignored, prismatics);
    }

    public boolean isIgnored(String patch, int id) {
        return id != PRISMATIC_ANVIL && ignored.contains(id)
            && prismatics.classify(patch, id) == PrismaticItemClassifier.Classification.NON_PRISMATIC;
    }

    public Kind kind(String patch, int id) {
        if (id <= 0) return null;
        if (id == PRISMATIC_ANVIL) return null;
        if (prismatics.classify(patch, id) == PrismaticItemClassifier.Classification.UNKNOWN) return null;
        if (prismatics.isPrismatic(patch, id)) return Kind.PRISMATIC;
        return kinds.get(id);
    }

    public PrismaticItemClassifier prismatics() { return prismatics; }
}
