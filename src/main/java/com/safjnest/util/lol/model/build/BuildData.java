package com.safjnest.util.lol.model.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.MobalyticsHandler;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

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

    private StaticChampion champion;
    private LaneType lane;

    private String winrate;
    private String matchCount;
    private String patch;

    public BuildData(QueryRecord query) {

      this.champion = LeagueHandler.getChampionById(query.getAsInt("champion"));
      this.lane = LaneType.values()[query.getAsInt("lane")];

      ObjectMapper mapper = new ObjectMapper();
      try {
          JsonNode root = mapper.readTree(query.get("build"));

          JsonNode runes = root.get("runes");
          this.primaryRunes = jsonArrayToList(runes.get("primary"));
          this.secondaryRunes = jsonArrayToList(runes.get("secondary"));
          this.statRunes = jsonArrayToList(runes.get("stats"));

          JsonNode build = root.get("build");
          this.buildMap = new LinkedHashMap<>();

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

              this.buildMap.put(key, list);
          }

          this.starterItems = this.buildMap.getOrDefault("starter", Collections.emptyList());
          this.buildItems = this.buildMap.getOrDefault("build", Collections.emptyList());
          this.boots = this.buildMap.getOrDefault("boots", List.of()).stream().findFirst().orElse("");
          

          this.summonerSpells = jsonArrayToList(root.get("summoner_spells"));
          this.skillOrder = jsonArrayToList(root.get("skill_order"));

      } catch (Exception e) {
          e.printStackTrace();
      }

    }

    public BuildData(StaticChampion champion, LaneType lane, String json) {
        this.champion = champion;
        this.lane = lane;

        String[] runes = MobalyticsHandler.getRunes(json);
        String[] roots = MobalyticsHandler.getRunesRoot(json);
        this.primaryRunes = List.of(roots[0], runes[0], runes[1], runes[2], runes[3]);
        this.secondaryRunes = List.of(roots[1], runes[4], runes[5]);
        this.statRunes = List.of(runes[6], runes[7], runes[8]);

        this.summonerSpells = List.of(MobalyticsHandler.getSummonerSpell(json));
        this.skillOrder = List.of(MobalyticsHandler.getSkillsOrder(json));

        this.buildMap = new LinkedHashMap<>();

        String[] starter = MobalyticsHandler.getBuild(json, 0);
        if (starter != null) this.buildMap.put("starter", List.of(starter));

        String[] core = MobalyticsHandler.getBuild(json, 2);
        if (core != null) this.buildMap.put("core", List.of(core));

        String[] fullBuild = MobalyticsHandler.getBuild(json, 3);
        if (fullBuild != null) this.buildMap.put("fullbuild", List.of(fullBuild));

        String[] situational = MobalyticsHandler.getBuild(json, 4);
        if (situational != null) this.buildMap.put("situational", List.of(situational));

        this.starterItems = this.buildMap.getOrDefault("starter", Collections.emptyList());
        this.buildItems = this.buildMap.getOrDefault("build", Collections.emptyList());

        List<String> bootsList = this.buildMap.getOrDefault("boots", List.of());
        this.boots = bootsList.isEmpty() ? "" : bootsList.get(0);

        this.patch = MobalyticsHandler.getPatch(json);
        
        String winRateRaw = MobalyticsHandler.getWinRate(json);
        this.winrate = winRateRaw.substring(0, winRateRaw.indexOf(".") + 3);

        this.matchCount = MobalyticsHandler.getMatchCount(json);
    }

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

    public StaticChampion getChampion() {
      return champion;
    }

    public LaneType getLane() {
      return lane;
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

    public String getPatch() {
        return patch;
    }

    public String getWinrate() {
        return winrate;
    }

    public String getGames() {
        return matchCount;
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
