package com.safjnest.lol.build;

import com.safjnest.lol.LeagueHandler;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record BuildSignature(
        String starter,
        int boots,
        int suppItem,
        String core,
        String fullBuild,
        String spellOrder
) {

    private static final Set<Integer> BOOTS = Set.of(3006, 3009, 3020, 3047, 3111, 3117, 3158);
    private static final Set<Integer> SUPPORT_ITEMS = Set.of(3869, 3870, 3871, 3872, 3873, 3876, 3877, 3901, 3902, 3903);
    private static final Set<Integer> TRINKETS = Set.of(3340, 3364, 3363, 3465);
    private static final Set<Integer> CONSUMABLES = Set.of(2003, 2055, 2138, 2139, 2140, 2010);

    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson) {
        JSONObject buildObj = buildJson.optJSONObject("build");
        if (buildObj == null || buildObj.optJSONArray("build") == null) return null;

        int boots = BuildUtils.readInt(buildObj, "boots");
        int suppItem = BuildUtils.readInt(buildObj, "support_item");

        List<Integer> starterList = extractStarter(buildObj);
        List<Integer> fullBuildList = extractFullBuild(buildObj);
        if (suppItem == 0)
            suppItem = fullBuildList.stream().filter(SUPPORT_ITEMS::contains).findFirst().orElse(0);

        List<Integer> coreList = extractCore(fullBuildList);
        if (boots == 0 || coreList.size() < 2) return null;

        Collections.sort(starterList);

        String spellRaw = IntStream.range(0, skillOrderJson.length())
                .mapToObj(i -> skillOrderJson.optString(i, ""))
                .collect(Collectors.joining(""));
        String spellOrder = spellRaw.length() >= 18
                ? spellRaw.substring(0, 18)
                : spellRaw + "0".repeat(18 - spellRaw.length());

        return new BuildSignature(
                BuildUtils.joinInts(starterList),
                boots,
                suppItem,
                BuildUtils.joinInts(coreList),
                BuildUtils.joinInts(fullBuildList),
                spellOrder
        );
    }

    public List<Integer> starterItems()    { return BuildUtils.parseDashList(starter); }
    public List<Integer> coreItems()       { return BuildUtils.parseDashList(core); }
    public List<Integer> fullBuildItems()  { return BuildUtils.parseDashList(fullBuild); }

    public String toCoreKey() {
        String raw = starter  + "|" + suppItem + "|" + core;
        return BuildUtils.toBase64(raw);
    }

    public String toKey() {
        String raw = starter + "|" + boots + "|" + suppItem + "|" + core + "|" + fullBuild + "|" + spellOrder;
        return BuildUtils.toBase64(raw);
    }

    public static BuildSignature decode(String key) {
        String[] p = BuildUtils.fromBase64(key).split("\\|", -1);
        return new BuildSignature(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[3], p[4]);
    }

    // -------------------------------------------------------------------------

    private static List<Integer> extractStarter(JSONObject buildObj) {
        JSONArray arr = buildObj.optJSONArray("starter");
        if (arr == null) return Collections.emptyList();
    
        Map<Integer, Integer> consumablesCount = new HashMap<>();
        Integer boots = null;
        Integer trinket = null;
        Integer genericItem = null;
    
        for (int i = 0; i < arr.length(); i++) {
            int id = BuildUtils.parseAnyInt(arr.opt(i));
            if (id == 0) continue;
    
            if (TRINKETS.contains(id)) {
                continue;
            }
    
            if (BOOTS.contains(id)) {
                boots = id;
                continue;
            }
    
            if (CONSUMABLES.contains(id)) {
                if (id == 2055) {
                    consumablesCount.merge(id, 1, Integer::sum);
                } else {
                    int count = consumablesCount.getOrDefault(id, 0);
                    if (count < 2) {
                        consumablesCount.put(id, count + 1);
                    }
                }
                continue;
            }
    
            genericItem = id;
        }
    
        if (trinket == null) {
            trinket = 3340;
        }
    
        List<Integer> result = new ArrayList<>();
    
        for (Map.Entry<Integer, Integer> e : consumablesCount.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                result.add(e.getKey());
            }
        }
    
        if (boots != null) result.add(boots);
        if (trinket != null) result.add(trinket);
        if (genericItem != null) result.add(genericItem);
    
        return result;
    }

    private static List<Integer> extractFullBuild(JSONObject buildObj) {
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        JSONArray path = buildObj.optJSONArray("build");
        if (path != null) {
            for (int i = 0; i < path.length(); i++) {
                int id = BuildUtils.parseAnyInt(path.opt(i));
                if (!isSkippable(id)) ordered.add(id);
            }
        }
        return new ArrayList<>(ordered);
    }

    private static List<Integer> extractCore(List<Integer> fullBuild) {
        Integer first = null, second = null;
        for (Integer id : fullBuild) {
            if (id == null || id == 0 || BOOTS.contains(id) || SUPPORT_ITEMS.contains(id)) continue;
            if (first == null) { first = id; }
            else if (!Objects.equals(first, id)) { second = id; break; }
        }

        List<Integer> core = new ArrayList<>();
        if (first != null) core.add(first);
        if (second != null) core.add(second);
        return core;
    }

    private static boolean isSkippable(int id) {
        if (id == 0 || TRINKETS.contains(id) || CONSUMABLES.contains(id)) return true;
        Item item = LeagueHandler.itemsMap.get(id);
        if (item == null || (item.getDepth() < 3 && item.getMaps().get("30") == null)) return true;
        try {
            for (String from : item.getFrom())
                if (BOOTS.contains(Integer.parseInt(from))) return true;
        } catch (Exception ignored) {}
        return false;
    }

}
