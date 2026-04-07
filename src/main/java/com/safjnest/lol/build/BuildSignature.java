package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.LeagueHandler;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record BuildSignature(
        String starter,
        int boots,
        int suppItem,
        String core,
        String fullBuild,
        String suggestions,
        String spellOrder
) {

    private static final Set<Integer> BOOTS = Set.of(3006, 3009, 3020, 3047, 3111, 3117, 3158);
    private static final Set<Integer> SUPPORT_ITEMS = Set.of(3869, 3870, 3871, 3872, 3873, 3876, 3877, 3901, 3902, 3903);
    private static final Set<Integer> TRINKETS = Set.of(3340, 3364, 3363, 3465);
    private static final Set<Integer> CONSUMABLES = Set.of(2003, 2055, 2138, 2139, 2140, 2010);

    /**
     * Builds a normalized signature from the stored participant build payload.
     * Expected shape:
     * - root.build.build: ordered purchase path
     * - root.build.starter / root.build.boots / root.build.support_item
     */
    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson) {
        JSONObject buildPathObj = buildJson.optJSONObject("build");
        if (buildPathObj == null) {
            return null;
        }

        List<Integer> starterList = new ArrayList<>();
        JSONArray starterArr = buildPathObj.optJSONArray("starter");
        if (starterArr != null) {
            for (int i = 0; i < starterArr.length(); i++) {
                int id = readArrayInt(starterArr, i);
                // Keep starter as-is (except zeros): consumables/trinkets are legitimate starters.
                if (id != 0) {
                    starterList.add(id);
                }
            }
        }

        int boots = readObjectInt(buildPathObj, "boots");
        int suppItem = readObjectInt(buildPathObj, "support_item");

        List<Integer> fullBuildList = extractFullBuildPurchaseOrder(buildPathObj);
        List<Integer> coreFromBuildPath = extractCoreFromPurchaseOrder(fullBuildList, suppItem);
        if (suppItem == 0) {
            suppItem = fullBuildList.stream().filter(SUPPORT_ITEMS::contains).findFirst().orElse(0);
            if (suppItem != 0) {
                coreFromBuildPath = extractCoreFromPurchaseOrder(fullBuildList, suppItem);
            }
        }

        boolean coreComplete = boots != 0 && coreFromBuildPath.size() == 2;
        if (!coreComplete) return null;

        Collections.sort(starterList);
        List<Integer> coreForGrouping = new ArrayList<>(coreFromBuildPath);

        List<Integer> suggestionsList = extractSuggestions(fullBuildList, coreForGrouping, boots, suppItem);

        return new BuildSignature(
                starterList.stream().map(String::valueOf).collect(Collectors.joining("-")),
                boots,
                suppItem,
                coreForGrouping.stream().map(String::valueOf).collect(Collectors.joining("-")),
                fullBuildList.stream().map(String::valueOf).collect(Collectors.joining("-")),
                suggestionsList.stream().map(String::valueOf).collect(Collectors.joining("-")),
                IntStream.range(0, skillOrderJson.length())
                        .mapToObj(i -> skillOrderJson.optString(i, ""))
                        .collect(Collectors.joining(""))
        );
    }

    /**
     * Returns core as list of item ids.
     */
    public List<Integer> coreItems() {
        return parseDashList(core);
    }

    /**
     * Returns full build as list of item ids ordered by acquisition.
     */
    public List<Integer> fullBuildItems() {
        return parseDashList(fullBuild);
    }

    /**
     * Returns post-core suggestions as list of item ids.
     */
    public List<Integer> suggestionItems() {
        return parseDashList(suggestions);
    }

    public List<Integer> starterItems() {
        return parseDashList(starter);
    }

    private static List<Integer> parseDashList(String source) {
        if (source == null || source.isBlank()) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (String token : source.split("-")) {
            if (!token.isBlank()) {
                result.add(Integer.parseInt(token));
            }
        }
        return result;
    }

    /**
     * Key used to aggregate builds by core while preserving full items in {@link #toKey()}.
     */
    public String toCoreKey() {
        String raw = starter + "|" + boots + "|" + suppItem + "|" + core + "|" + spellOrder;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String toKey() {
        String raw = starter + "|" + boots + "|" + suppItem + "|" + core + "|" + fullBuild + "|" + suggestions + "|" + spellOrder;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static BuildSignature decode(String key) {
        String[] p = new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8).split("\\|", -1);
        // backward-compat for older keys: starter|boots|supp|items|spellOrder
        if (p.length == 5) {
            return new BuildSignature(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[3], "", p[4]);
        }
        return new BuildSignature(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], p[5], p[6]);
    }

    private static List<Integer> extractFullBuildPurchaseOrder(JSONObject buildPathObj) {
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();

        JSONArray buildPath = buildPathObj.optJSONArray("build");
        if (buildPath != null) {
            for (int i = 0; i < buildPath.length(); i++) {
                int id = readArrayInt(buildPath, i);
                if (isSkippable(id)) {
                    continue;
                }
                ordered.add(id);
            }
        }

        // If boots/support are provided as metadata (often strings), append them to complete full build payload.
        int boots = readObjectInt(buildPathObj, "boots");
        if (boots != 0) {
            ordered.add(boots);
        }
        int suppItem = readObjectInt(buildPathObj, "support_item");
        if (suppItem != 0) {
            ordered.add(suppItem);
        }

        return new ArrayList<>(ordered);
    }

    /**
     * Core selection rule:
     * - default: first two purchased non-boots/non-support items
     * - support lane: first purchased non-boots/non-support + support item
     */
    private static List<Integer> extractCoreFromPurchaseOrder(List<Integer> fullBuildList, int suppItem) {
        Integer firstNonSupport = null;
        Integer secondNonSupport = null;

        for (Integer id : fullBuildList) {
            if (id == null || id == 0) {
                continue;
            }
            if (BOOTS.contains(id) || SUPPORT_ITEMS.contains(id)) {
                continue;
            }

            if (firstNonSupport == null) {
                firstNonSupport = id;
            } else if (!Objects.equals(firstNonSupport, id)) {
                secondNonSupport = id;
                break;
            }
        }

        List<Integer> core = new ArrayList<>();
        if (firstNonSupport != null) {
            core.add(firstNonSupport);
        }

        if (suppItem != 0) {
            if (firstNonSupport == null || !Objects.equals(firstNonSupport, suppItem)) {
                core.add(suppItem);
            }
        } else if (secondNonSupport != null) {
            core.add(secondNonSupport);
        }

        return uniqueOrdered(core);
    }

    private static List<Integer> extractSuggestions(List<Integer> fullBuildList, List<Integer> coreForGrouping, int boots, int suppItem) {
        Set<Integer> excluded = new HashSet<>(coreForGrouping);
        if (boots != 0) {
            excluded.add(boots);
        }
        if (suppItem != 0) {
            excluded.add(suppItem);
        }

        List<Integer> suggestions = new ArrayList<>();
        for (Integer id : fullBuildList) {
            if (!excluded.contains(id)) {
                suggestions.add(id);
            }
        }
        return uniqueOrdered(suggestions);
    }

    private static List<Integer> uniqueOrdered(List<Integer> source) {
        return new ArrayList<>(new LinkedHashSet<>(source));
    }

    private static boolean isSkippable(int id) {
        Item item = LeagueHandler.itemsMap.get(id);
        if (item == null) return true;
        if (item.getDepth() < 3) return true;
        boolean isFromBoots = false;
        try {
            for (String from : item.getFrom()) {
                isFromBoots = BOOTS.contains(Integer.parseInt(from));
                if (isFromBoots) {
                    break;
                }
            }   
        } catch (Exception e) {
        }
        return id == 0 || TRINKETS.contains(id) || CONSUMABLES.contains(id) || isFromBoots;
    }

    private static int readObjectInt(JSONObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return 0;
        }
        Object raw = obj.opt(key);
        return parseAnyInt(raw);
    }

    private static int readArrayInt(JSONArray arr, int idx) {
        if (arr == null || idx < 0 || idx >= arr.length()) {
            return 0;
        }
        Object raw = arr.opt(idx);
        return parseAnyInt(raw);
    }

    private static int parseAnyInt(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }
}
