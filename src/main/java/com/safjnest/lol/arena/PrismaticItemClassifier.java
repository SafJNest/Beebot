package com.safjnest.lol.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.utils.ItemUtils;

public final class PrismaticItemClassifier {

    private static final int PRISMATIC_ANVIL_ID = 220007;
    private static final Set<Integer> notPrismatic = Set.of(447111);
    private final Map<String, Map<Integer, Classification>> byPatch;

    public PrismaticItemClassifier(Map<String, Map<Integer, Classification>> source) {
        Map<String, Map<Integer, Classification>> copy = new HashMap<>();
        if (source != null) for (Map.Entry<String, Map<Integer, Classification>> entry : source.entrySet()) {
            String patch = patchMajor(entry.getKey());
            if (patch != null && entry.getValue() != null) copy.put(patch, Map.copyOf(entry.getValue()));
        }
        byPatch = Map.copyOf(copy);
    }

    public Classification classify(String patch, int itemId) {
        if (itemId <= 0) return Classification.NON_PRISMATIC;
        if (itemId == PRISMATIC_ANVIL_ID) return Classification.NON_PRISMATIC;
        if (notPrismatic.contains(itemId)) return Classification.NON_PRISMATIC;
        String major = patchMajor(patch);
        Classification patchClassification = major == null ? null : byPatch.getOrDefault(major, Map.of()).get(itemId);
        if (patchClassification != null) return patchClassification;
        return ItemUtils.isPrismatic(itemId) ? Classification.PRISMATIC : Classification.NON_PRISMATIC;
    }

    public boolean isPrismatic(String patch, int itemId) {
        return classify(patch, itemId) == Classification.PRISMATIC;
    }

    private static String patchMajor(String patch) {
        if (patch == null || patch.isBlank()) return null;
        String value = patch.trim();
        int firstSeparator = value.indexOf('.');
        if (firstSeparator < 0) return value;
        int secondSeparator = value.indexOf('.', firstSeparator + 1);
        return secondSeparator < 0 ? value : value.substring(0, secondSeparator);
    }

    public enum Classification {
        PRISMATIC,
        NON_PRISMATIC,
        UNKNOWN
    }
}
