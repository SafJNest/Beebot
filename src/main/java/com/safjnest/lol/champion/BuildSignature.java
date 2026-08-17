package com.safjnest.lol.champion;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.ItemUtils;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record BuildSignature(
        List<Integer> starter,
        int boots,
        int suppItem,
        List<Integer> core,
        List<Integer> fullBuild,
        List<Integer> spellOrder,
        List<Integer> prismatics,
        List<Integer> augments,
        List<Integer> summonerSpells
) {

    private static final Set<Integer> SUPPORT_ITEMS = Set.of(3869, 3870, 3871, 3872, 3873, 3876, 3877, 3901, 3902, 3903);
    private static final Set<Integer> TRINKETS = Set.of(3340, 3364, 3363, 3465, 3348);
    private static final Set<Integer> CONSUMABLES = Set.of(2003, 2055, 2138, 2139, 2140, 2010);
    private static final int COMPLETED_ITEM_DEPTH = 3;

    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson, JSONArray prismaticsJson, JSONArray augmentsJson, JSONArray summonerSpellsJson, Filter filter) {
        JSONObject buildObj = buildJson.optJSONObject("build");
        if (buildObj == null || buildObj.optJSONArray("build") == null) return null;

        int boots = BuildUtils.readInt(buildObj, "boots") == ItemUtils.BASE_BOOTS ? 0 : BuildUtils.readInt(buildObj, "boots");
        int suppItem = BuildUtils.readInt(buildObj, "support_item");

        List<Integer> starterList = extractStarter(filter, buildObj);
        List<Integer> fullBuildList = extractFullBuild(buildObj);
        if (suppItem == 0)
            suppItem = fullBuildList.stream().filter(SUPPORT_ITEMS::contains).findFirst().orElse(0);

        List<Integer> coreList = extractCore(fullBuildList, starterList);
        if (boots == 0 || coreList.size() < 2) return null;

        Collections.sort(starterList);

        String spellRaw = IntStream.range(0, skillOrderJson.length())
                .mapToObj(i -> skillOrderJson.optString(i, ""))
                .collect(Collectors.joining(""));
        List<Integer> spellOrderList = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) {
            int v = i < spellRaw.length() ? Character.getNumericValue(spellRaw.charAt(i)) : 0;
            spellOrderList.add(v >= 1 && v <= 4 ? v : 0);
        }

        List<Integer> prismaticIds = new ArrayList<>();
        if (prismaticsJson != null && prismaticsJson.length() > 0) {
            for (int i = 0; i < prismaticsJson.length(); i++) {
                int id = BuildUtils.parseAnyInt(prismaticsJson.opt(i));
                if (id != 0) prismaticIds.add(id);
            }
        }

        List<Integer> augmentIds = new ArrayList<>();
        if (augmentsJson != null && augmentsJson.length() > 0) {
            for (int i = 0; i < augmentsJson.length(); i++) {
                int id = BuildUtils.parseAnyInt(augmentsJson.opt(i));
                if (id != 0) augmentIds.add(id);
            }
        }

        List<Integer> summonerSpellIds = new ArrayList<>();
        if (summonerSpellsJson != null && summonerSpellsJson.length() > 0) {
            for (int i = 0; i < summonerSpellsJson.length(); i++) {
                int id = BuildUtils.parseAnyInt(summonerSpellsJson.opt(i));
                if (id != 0) summonerSpellIds.add(id);
            }
        }

        Collections.sort(summonerSpellIds);

        return new BuildSignature(
                List.copyOf(starterList),
                boots,
                suppItem,
                List.copyOf(coreList),
                List.copyOf(fullBuildList),
                List.copyOf(spellOrderList),
                List.copyOf(prismaticIds),
                List.copyOf(augmentIds),
                List.copyOf(summonerSpellIds)
        );
    }

    public String toCoreKey() {
        String raw = BuildUtils.joinInts(starter) + "|" + suppItem + "|" + BuildUtils.joinInts(core);
        return BuildUtils.toBase64(raw);
    }

    public String toKey() {
        String raw = BuildUtils.joinInts(starter) + "|" + boots + "|" + suppItem + "|"
                + BuildUtils.joinInts(core) + "|" + BuildUtils.joinInts(fullBuild) + "|"
                + BuildUtils.joinInts(spellOrder) + "|" + BuildUtils.joinInts(prismatics) + "|"
                + BuildUtils.joinInts(augments) + "|" + BuildUtils.joinInts(summonerSpells);
        return BuildUtils.toBase64(raw);
    }

    public static BuildSignature decode(String key) {
        String[] p = BuildUtils.fromBase64(key).split("\\|", -1);
        List<Integer> defaultSpell = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) defaultSpell.add(0);
        return new BuildSignature(
                p.length > 0 ? BuildUtils.parseDashList(p[0]) : List.of(),
                safeIntSeg(p, 1),
                safeIntSeg(p, 2),
                p.length > 3 ? BuildUtils.parseDashList(p[3]) : List.of(),
                p.length > 4 ? BuildUtils.parseDashList(p[4]) : List.of(),
                p.length > 5 ? decodeSpellOrderSegment(p[5]) : defaultSpell,
                p.length > 6 ? BuildUtils.parseDashList(p[6]) : List.of(),
                p.length > 7 ? BuildUtils.parseDashList(p[7]) : List.of(),
                p.length > 8 ? BuildUtils.parseDashList(p[8]) : List.of());
    }

    private static int safeIntSeg(String[] p, int i) {
        if (i >= p.length || p[i] == null || p[i].isBlank()) return 0;
        try { return Integer.parseInt(p[i].trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** Supports dash-separated ints or legacy 18-digit string without separators. */
    private static List<Integer> decodeSpellOrderSegment(String s) {
        if (s == null || s.isBlank()) {
            List<Integer> z = new ArrayList<>(18);
            for (int i = 0; i < 18; i++) z.add(0);
            return z;
        }
        if (s.contains("-")) {
            List<Integer> x = new ArrayList<>(BuildUtils.parseDashList(s));
            while (x.size() < 18) x.add(0);
            return x.size() > 18 ? new ArrayList<>(x.subList(0, 18)) : x;
        }
        List<Integer> o = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) {
            int v = i < s.length() ? Character.getNumericValue(s.charAt(i)) : 0;
            o.add(v >= 1 && v <= 4 ? v : 0);
        }
        return o;
    }

    // -------------------------------------------------------------------------

    private static List<Integer> extractStarter(Filter filter, JSONObject buildObj) {
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

            if (ItemUtils.getBoots().contains(id)) {
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

        if (trinket == null && (filter == null || !GameQueueTypeUtils.isCherry(filter.queue())))
            trinket = 3340;

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

    private static List<Integer> extractCore(List<Integer> fullBuild, List<Integer> starterList) {
        Integer first = null, second = null;
        for (Integer id : fullBuild) {
            Item item = ItemUtils.getItem(id);
            if (id == null || id == 0 || starterList.contains(id) || ItemUtils.isBoots(item) || SUPPORT_ITEMS.contains(id) || ItemUtils.isPrismatic(item)) continue;
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
        Item item = ItemUtils.getItem(id);
        if (item == null || item.getDepth() < COMPLETED_ITEM_DEPTH) return true;
        try {
            for (String from : item.getFrom())
                if (ItemUtils.getBoots().contains(Integer.parseInt(from))) return true;
        } catch (Exception ignored) {}
        return false;
    }

}
