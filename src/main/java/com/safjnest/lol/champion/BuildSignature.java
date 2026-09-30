package com.safjnest.lol.champion;

import com.safjnest.lol.utils.NumberUtils;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.ItemUtils;
import com.safjnest.lol.utils.ChampionBuildTimelineUtils;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private static final Set<Integer> SUPPORT_ITEMS = Set.of(3865, 3866, 3867, 3869, 3870, 3871, 3872, 3873, 3876, 3877, 3901, 3902, 3903);
    private static final Set<Integer> TRINKETS = Set.of(3340, 3364, 3363, 3465, 3348);
    private static final Set<Integer> CONSUMABLES = Set.of(2003, 2055, 2138, 2139, 2140, 2010);
    private static final int COMPLETED_ITEM_DEPTH = 3;

    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson, JSONArray augmentsJson, JSONArray summonerSpellsJson, Filter filter) {
        return from(buildJson, skillOrderJson, augmentsJson, summonerSpellsJson, filter, List.of(), List.of());
    }

    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson,
                                      JSONArray augmentsJson, JSONArray summonerSpellsJson, Filter filter,
                                      List<ChampionBuildTimelineUtils.ItemEvent> itemEvents,
                                      List<ChampionBuildTimelineUtils.SkillUpgrade> skillUpgrades) {
        return from(buildJson, skillOrderJson, augmentsJson, summonerSpellsJson, filter,
                itemEvents, List.of(), skillUpgrades);
    }

    public static BuildSignature from(JSONObject buildJson, JSONArray skillOrderJson,
                                      JSONArray augmentsJson, JSONArray summonerSpellsJson, Filter filter,
                                      List<ChampionBuildTimelineUtils.ItemEvent> itemEvents,
                                      List<ChampionBuildTimelineUtils.ItemEvent> starterEvents,
                                      List<ChampionBuildTimelineUtils.SkillUpgrade> skillUpgrades) {
        JSONObject buildObj = buildJson.optJSONObject("build");
        if (buildObj == null || buildObj.optJSONArray("build") == null) return null;

        List<Integer> inventory = BuildUtils.jsonArrayToIntList(buildObj.optJSONArray("build"));
        List<Integer> fullBuildList = extractFullBuild(buildObj, itemEvents);
        int boots = inventory.stream().filter(id -> ItemUtils.getBoots().contains(id))
                .filter(id -> id != ItemUtils.BASE_BOOTS).findFirst().orElse(0);
        int supportItem = inventory.stream().filter(SUPPORT_ITEMS::contains).findFirst().orElse(0);

        List<Integer> starterList = extractStarter(filter, starterEvents);

        List<Integer> coreList = extractCore(fullBuildList, starterList);
        if (coreList.size() < 2) return null;

        Collections.sort(starterList);

        List<Integer> spellOrderList = skillOrder(skillOrderJson, skillUpgrades);

        List<Integer> prismaticIds = new ArrayList<>();
        for (int id : inventory) if (ItemUtils.isPrismatic(id)) prismaticIds.add(id);

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
                supportItem,
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
        String[] parts = BuildUtils.fromBase64(key).split("\\|", -1);
        List<Integer> emptySkillOrder = new ArrayList<>(18);
        for (int level = 0; level < 18; level++) emptySkillOrder.add(0);
        return new BuildSignature(
                parts.length > 0 ? BuildUtils.parseDashList(parts[0]) : List.of(),
                NumberUtils.parseInt(parts[1]),
                NumberUtils.parseInt(parts[2]),
                parts.length > 3 ? BuildUtils.parseDashList(parts[3]) : List.of(),
                parts.length > 4 ? BuildUtils.parseDashList(parts[4]) : List.of(),
                parts.length > 5 ? decodeSpellOrderSegment(parts[5]) : emptySkillOrder,
                parts.length > 6 ? BuildUtils.parseDashList(parts[6]) : List.of(),
                parts.length > 7 ? BuildUtils.parseDashList(parts[7]) : List.of(),
                parts.length > 8 ? BuildUtils.parseDashList(parts[8]) : List.of());
    }


    /** Supports dash-separated ints or legacy 18-digit string without separators. */
    private static List<Integer> decodeSpellOrderSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            List<Integer> emptySkillOrder = new ArrayList<>(18);
            for (int level = 0; level < 18; level++) emptySkillOrder.add(0);
            return emptySkillOrder;
        }
        if (segment.contains("-")) {
            List<Integer> skillOrder = new ArrayList<>(BuildUtils.parseDashList(segment));
            while (skillOrder.size() < 18) skillOrder.add(0);
            return skillOrder.size() > 18
                    ? new ArrayList<>(skillOrder.subList(0, 18))
                    : skillOrder;
        }
        List<Integer> skillOrder = new ArrayList<>(18);
        for (int level = 0; level < 18; level++) {
            int slot = level < segment.length() ? Character.getNumericValue(segment.charAt(level)) : 0;
            skillOrder.add(slot >= 1 && slot <= 4 ? slot : 0);
        }
        return skillOrder;
    }

    // -------------------------------------------------------------------------

    private static List<Integer> extractStarter(Filter filter, List<ChampionBuildTimelineUtils.ItemEvent> starterEvents) {
        Map<Integer, Integer> consumablesCount = new HashMap<>();
        Integer boots = null;
        Integer trinket = null;
        Integer genericItem = null;

        if (starterEvents != null) for (ChampionBuildTimelineUtils.ItemEvent event : starterEvents) {
            int id = event.itemId();
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

    static List<Integer> extractFullBuild(JSONObject buildObj, List<ChampionBuildTimelineUtils.ItemEvent> itemEvents) {
        List<Integer> inventory = BuildUtils.jsonArrayToIntList(buildObj.optJSONArray("build"));
        List<Integer> orderedInventory = orderByTimeline(inventory, itemEvents);
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        for (int id : orderedInventory) if (!isSkippable(id)) ordered.add(id);
        return new ArrayList<>(ordered);
    }

    static List<Integer> orderByTimeline(List<Integer> inventory, List<ChampionBuildTimelineUtils.ItemEvent> itemEvents) {
        List<Integer> result = new ArrayList<>(inventory.size());
        Map<Integer, Integer> remaining = new HashMap<>();
        for (int id : inventory) if (id > 0) remaining.merge(id, 1, Integer::sum);
        if (itemEvents != null) for (ChampionBuildTimelineUtils.ItemEvent event : itemEvents) {
            int count = remaining.getOrDefault(event.itemId(), 0);
            if (count == 0) continue;
            result.add(event.itemId());
            if (count == 1) remaining.remove(event.itemId());
            else remaining.put(event.itemId(), count - 1);
        }
        for (int id : inventory) {
            int count = remaining.getOrDefault(id, 0);
            if (count == 0) continue;
            result.add(id);
            if (count == 1) remaining.remove(id);
            else remaining.put(id, count - 1);
        }
        return result;
    }

    static List<Integer> skillOrder(JSONArray fallback, List<ChampionBuildTimelineUtils.SkillUpgrade> upgrades) {
        List<Integer> result = new ArrayList<>(Collections.nCopies(18, 0));
        boolean hasTimelineSkills = upgrades != null && !upgrades.isEmpty();
        if (hasTimelineSkills) {
            int fallbackLevel = 1;
            for (ChampionBuildTimelineUtils.SkillUpgrade upgrade : upgrades) {
                int level = upgrade.championLevel() == null ? fallbackLevel : upgrade.championLevel();
                fallbackLevel = Math.max(fallbackLevel + 1, level + 1);
                if (level >= 1 && level <= result.size() && upgrade.skillSlot() >= 1 && upgrade.skillSlot() <= 4)
                    result.set(level - 1, upgrade.skillSlot());
            }
            return result;
        }
        if (fallback == null) return result;
        StringBuilder rawSkillOrder = new StringBuilder();
        for (int index = 0; index < fallback.length(); index++) rawSkillOrder.append(fallback.optString(index, ""));
        for (int i = 0; i < result.size(); i++) {
            int slot = i < rawSkillOrder.length() ? Character.getNumericValue(rawSkillOrder.charAt(i)) : 0;
            if (slot >= 1 && slot <= 4) result.set(i, slot);
        }
        return result;
    }

    private static List<Integer> extractCore(List<Integer> fullBuild, List<Integer> starterList) {
        Integer first = null, second = null;
        for (Integer id : fullBuild) {
            Item item = ItemUtils.getItem(id);
            if (id == null || id == 0 || starterList.contains(id) || ItemUtils.isBoots(item) || SUPPORT_ITEMS.contains(id) || ItemUtils.isPrismatic(id)) continue;
            if (first == null) { first = id; }
            else if (!Objects.equals(first, id)) { second = id; break; }
        }

        List<Integer> core = new ArrayList<>();
        if (first != null) core.add(first);
        if (second != null) core.add(second);
        return core;
    }

    private static boolean isSkippable(int id) {
        return isSkippable(id, ItemUtils.getItem(id));
    }

    static boolean isSkippable(int id, Item item) {
        if (id == 0 || TRINKETS.contains(id) || CONSUMABLES.contains(id)) return true;
        if (item == null) return false;
        if (item.getDepth() < COMPLETED_ITEM_DEPTH) return true;
        try {
            for (String from : item.getFrom())
                if (ItemUtils.getBoots().contains(Integer.parseInt(from))) return true;
        } catch (Exception ignored) {}
        return false;
    }

}
