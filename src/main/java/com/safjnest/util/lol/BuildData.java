package com.safjnest.util.lol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class BuildData {

    private List<String> primaryRunes;
    private List<String> secondaryRunes;
    private List<String> statRunes;

    private List<String> starterItems;
    private List<String> buildItems;
    private String boots;

    private List<String> summonerSpells;
    private List<String> skillOrder;

    private LinkedHashMap<String, List<String>> buildMap;

    public String getPrimaryRunesRoot() {
        return primaryRunes.get(0);
    }

    public List<String> getPrimaryRunes() {
        return primaryRunes;
    }

    public String getSecondaryRunesRoot() {
        return secondaryRunes.get(0);
    }

    public List<String> getSecondaryRunes() {
        return secondaryRunes;
    }

    public List<String> getStatRunes() {
        return statRunes;
    }

    public String getOffense() {
        return statRunes.get(0);
    }

    public String getFlex() {
        return statRunes.get(1);
    }

    public String getDefense() {
        return statRunes.get(2);
    }

    public List<String> getStarterItems() {
        return starterItems;
    }

    public List<String> getBuildItems() {
        return buildItems;
    }

    public String getBoots() {
        return boots;
    }

    public List<String> getSummonerSpells() {
        return summonerSpells;
    }

    public String getD() {
        return summonerSpells.get(0);
    }

    public String getF() {
        return summonerSpells.get(1);
    }

    public List<String> getSkillOrder() {
        return skillOrder;
    }

    public LinkedHashMap<String, List<String>> getBuildMap() {
        return buildMap;
    }


    /**
     * da rifare assolutamente da capo
     * fatta random chatgpt solo per vedere se il comando funzionava
     * magari metterlo nel costrutture in cui passi il json o qualche puttanata dal genere
     * estendere anche per le build di mobalytics
     * @param json
     * @return
     */
    public static BuildData fromJson(String json) {
        BuildData data = new BuildData();
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode root = mapper.readTree(json);

            // Runes
            JsonNode runes = root.get("runes");
            data.primaryRunes = jsonArrayToList(runes.get("primary"));
            data.secondaryRunes = jsonArrayToList(runes.get("secondary"));
            data.statRunes = jsonArrayToList(runes.get("stats"));

            // Build
            JsonNode build = root.get("build");
            data.buildMap = new LinkedHashMap<>();

            if (build != null) {
                for (Iterator<String> it = build.fieldNames(); it.hasNext(); ) {
                    String key = it.next();
                    JsonNode value = build.get(key);

                    List<String> list;
                    if (value.isArray()) {
                        list = new ArrayList<>();
                        for (JsonNode item : value) {
                            list.add(item.asText());
                        }
                    } else {
                        list = List.of(value.asText());
                    }

                    data.buildMap.put(key, list);
                }

                data.starterItems = data.buildMap.getOrDefault("starter", Collections.emptyList());
                data.buildItems = data.buildMap.getOrDefault("build", Collections.emptyList());
                data.boots = data.buildMap.getOrDefault("boots", List.of()).stream().findFirst().orElse("");
            }

            data.summonerSpells = jsonArrayToList(root.get("summoner_spells"));
            data.skillOrder = jsonArrayToList(root.get("skill_order"));

        } catch (Exception e) {
            e.printStackTrace(); // gestiscilo meglio in produzione
        }

        return data;
    }

    private static List<String> jsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                list.add(item.asText());
            }
        }
        return list;
    }

    @Override
    public String toString() {
        return "BuildData{" +
                "primaryRunes=" + primaryRunes +
                ", secondaryRunes=" + secondaryRunes +
                ", statRunes=" + statRunes +
                ", starterItems=" + starterItems +
                ", buildItems=" + buildItems +
                ", boots='" + boots + '\'' +
                ", summonerSpells=" + summonerSpells +
                ", skillOrder=" + skillOrder +
                ", buildMap=" + buildMap +
                '}';
    }
}
