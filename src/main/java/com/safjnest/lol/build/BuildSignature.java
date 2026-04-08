package com.safjnest.lol.build;

import com.safjnest.lol.LeagueHandler;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import org.json.JSONArray;
import org.json.JSONObject;

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

    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson) {
        JSONObject buildPathObj = buildJson.optJSONObject("build");
        if (buildPathObj == null) return null;

        List<Integer> starterList = extractStarter(buildPathObj);
        int boots    = readInt(buildPathObj, "boots");
        int suppItem = readInt(buildPathObj, "support_item");

        List<Integer> fullBuildList = extractFullBuild(buildPathObj);

        if (suppItem == 0)
            suppItem = fullBuildList.stream().filter(SUPPORT_ITEMS::contains).findFirst().orElse(0);

        List<Integer> coreList = extractCore(fullBuildList, suppItem);

        if (boots == 0 || coreList.size() < 2) return null;

        Collections.sort(starterList);

        return new BuildSignature(
                RuneSignature.joinInts(starterList),
                boots,
                suppItem,
                RuneSignature.joinInts(coreList),
                RuneSignature.joinInts(fullBuildList),
                "",
                IntStream.range(0, skillOrderJson.length())
                        .mapToObj(i -> skillOrderJson.optString(i, ""))
                        .collect(Collectors.joining(""))
        );
    }

    public List<Integer> starterItems()     { return RuneSignature.parseDashList(starter); }
    public List<Integer> coreItems()        { return RuneSignature.parseDashList(core); }
    public List<Integer> fullBuildItems()   { return RuneSignature.parseDashList(fullBuild); }
    public List<Integer> suggestionItems()  { return RuneSignature.parseDashList(suggestions); }

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
        if (p.length == 5)
            return new BuildSignature(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[3], "", p[4]);
        return new BuildSignature(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], p[5], p[6]);
    }

    // --- private helpers ---

    private static List<Integer> extractStarter(JSONObject buildPathObj) {
        List<Integer> list = new ArrayList<>();
        JSONArray arr = buildPathObj.optJSONArray("starter");
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            int id = parseAnyInt(arr.opt(i));
            if (id != 0) list.add(id);
        }
        return list;
    }

    private static List<Integer> extractFullBuild(JSONObject buildPathObj) {
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        JSONArray buildPath = buildPathObj.optJSONArray("build");
        if (buildPath != null) {
            for (int i = 0; i < buildPath.length(); i++) {
                int id = parseAnyInt(buildPath.opt(i));
                if (!isSkippable(id)) ordered.add(id);
            }
        }
        int boots    = readInt(buildPathObj, "boots");
        int suppItem = readInt(buildPathObj, "support_item");
        if (boots != 0) ordered.add(boots);
        if (suppItem != 0) ordered.add(suppItem);
        return new ArrayList<>(ordered);
    }

    private static List<Integer> extractCore(List<Integer> fullBuildList, int suppItem) {
        Integer first = null, second = null;
        for (Integer id : fullBuildList) {
            if (id == null || id == 0 || BOOTS.contains(id) || SUPPORT_ITEMS.contains(id)) continue;
            if (first == null) { first = id; }
            else if (!Objects.equals(first, id)) { second = id; break; }
        }

        List<Integer> core = new ArrayList<>();
        if (first != null) core.add(first);

        if (suppItem != 0) {
            if (first == null || !Objects.equals(first, suppItem)) core.add(suppItem);
        } else if (second != null) {
            core.add(second);
        }

        return uniqueOrdered(core);
    }

    private static boolean isSkippable(int id) {
        if (id == 0 || TRINKETS.contains(id) || CONSUMABLES.contains(id)) return true;
        Item item = LeagueHandler.itemsMap.get(id);
        if (item == null || item.getDepth() < 3) return true;
        try {
            for (String from : item.getFrom())
                if (BOOTS.contains(Integer.parseInt(from))) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static List<Integer> uniqueOrdered(List<Integer> source) {
        return new ArrayList<>(new LinkedHashSet<>(source));
    }

    private static int readInt(JSONObject obj, String key) {
        return (obj == null || !obj.has(key)) ? 0 : parseAnyInt(obj.opt(key));
    }

    private static int parseAnyInt(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        }
        return 0;
    }
}
