package com.safjnest.lol.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;

public class ParticipantData {

    public ParticipantData() {}

    //rank, gain
    public ParticipantData(MatchParticipant participant) {
        this.puuid = participant.getPuuid();

        this.win = participant.didWin();
        this.champion = participant.getChampionId();
        this.lane = participant.getLane();
        this.team = participant.getTeam();

        this.damage = participant.getTotalDamageDealtToChampions();
        this.damageBuilding = participant.getDamageDealtToBuildings();
        this.healing = participant.getTotalHeal();
        this.cs = participant.getTotalMinionsKilled() + participant.getNeutralMinionsKilled();
        this.goldEarned = participant.getGoldEarned();
        this.ward = participant.getWardsPlaced();
        this.wardKilled = participant.getWardsKilled();
        this.visionScore = participant.getVisionScore();
        this.kills = participant.getKills();
        this.deaths = participant.getDeaths();
        this.assists = participant.getAssists();
        this.doubles = participant.getDoubleKills();
        this.triples = participant.getTripleKills();
        this.quadruples = participant.getQuadraKills();
        this.pentas = participant.getPentaKills();
        this.level = participant.getChampionLevel();

        this.item0 = participant.getItem0();
        this.item1 = participant.getItem1();
        this.item2 = participant.getItem2();
        this.item3 = participant.getItem3();
        this.item4 = participant.getItem4();
        this.item5 = participant.getItem5();
        this.item6 = participant.getItem6();

        this.summonerSpell1 = participant.getSummoner1Id();
        this.summonerSpell2 = participant.getSummoner2Id();

        HashMap<String, Integer> pings = new HashMap<>();        
        pings.put("push", participant.getPushPings());
        pings.put("bait", participant.getBaitPings());
        pings.put("danger", participant.getDangerPings());
        pings.put("hold", participant.getHoldPings());
        pings.put("all_in", participant.getAllInPings());
        pings.put("basic", participant.getBasicPings());
        pings.put("command", participant.getCommandPings());
        pings.put("get_back", participant.getGetBackPings());
        pings.put("on_my_way", participant.getOnMyWayPings());
        pings.put("assist_me", participant.getAssistMePings());
        pings.put("need_vision", participant.getNeedVisionPings());
        pings.put("enemy_vision", participant.getEnemyVisionPings());
        pings.put("enemy_missing", participant.getEnemyMissingPings());
        pings.put("vision_cleared", participant.getVisionClearedPings());
        this.pings = pings;
    }


    public int id; //TODO: remove
    public int summonerId; //TODO: remove
    public String puuid;

    public int matchId; //TODO: i think i can remove this with mongo
    
    public boolean win;
    public int champion;
    public LaneType lane;
    public TeamType team;
    public TierDivisionType rank;
    public int gain;
    public int damage;
    public int damageBuilding;
    public int healing;
    public int cs;
    public int goldEarned;
    public int ward;
    public int wardKilled;
    public int visionScore;
    public HashMap<String, Integer> pings;
    public int subTeam;
    public int subTeamPlacement;

    public int level;

    public String kda; //TODO: remove
    public int kills;
    public int deaths;
    public int assists;
    public int doubles;
    public int triples;
    public int quadruples;
    public int pentas;
    
    public int item0;
    public int item1;
    public int item2;
    public int item3;
    public int item4;
    public int item5;
    public int item6;
    

    public int summonerSpell1;
    public int summonerSpell2;
    

    public List<Integer> primaryRunes = new ArrayList<>();
    public List<Integer> secondaryRunes = new ArrayList<>();
    public List<Integer> statsRunes = new ArrayList<>();
    

    public List<Integer> skillOrder = new ArrayList<>();
    

    public List<Integer> augments = new ArrayList<>();
    

    public List<Integer> starterItems = new ArrayList<>();
    public List<Integer> buildPath = new ArrayList<>();
    public int boots;
    
    public void setBuild(String buildJson) {
        if (buildJson == null || buildJson.isEmpty()) {
            return;
        }
        
        JSONObject build = new JSONObject(buildJson);
        
        if (build.has("items")) {
            JSONObject items = build.getJSONObject("items");
            item0 = items.optInt("0", 0);
            item1 = items.optInt("1", 0);
            item2 = items.optInt("2", 0);
            item3 = items.optInt("3", 0);
            item4 = items.optInt("4", 0);
            item5 = items.optInt("5", 0);
            item6 = items.optInt("6", 0);
        }
        

        if (build.has("summoner_spells")) {
            JSONArray spells = build.getJSONArray("summoner_spells");
            if (spells.length() > 0) {
                summonerSpell1 = spells.optInt(0, 0);
            }
            if (spells.length() > 1) {
                summonerSpell2 = spells.optInt(1, 0);
            }
        }
        
        if (build.has("runes")) {
            JSONObject runes = build.getJSONObject("runes");
            
            if (runes.has("primary")) {
                JSONArray primary = runes.getJSONArray("primary");
                for (int i = 0; i < primary.length(); i++) {
                    primaryRunes.add(primary.optInt(i, 0));
                }
            }
            
            if (runes.has("secondary")) {
                JSONArray secondary = runes.getJSONArray("secondary");
                for (int i = 0; i < secondary.length(); i++) {
                    secondaryRunes.add(secondary.optInt(i, 0));
                }
            }
            
            if (runes.has("stats")) {
                JSONArray stats = runes.getJSONArray("stats");
                for (int i = 0; i < stats.length(); i++) {
                    statsRunes.add(stats.optInt(i, 0));
                }
            }
        }
        
        if (build.has("skill_order")) {
            JSONArray skills = build.getJSONArray("skill_order");
            for (int i = 0; i < skills.length(); i++) {
                skillOrder.add(skills.optInt(i, 0));
            }
        }
        

        if (build.has("augments")) {
            JSONArray augs = build.getJSONArray("augments");
            for (int i = 0; i < augs.length(); i++) {
                augments.add(augs.optInt(i, 0));
            }
        }
        
        if (build.has("build")) {
            JSONObject buildObj = build.optJSONObject("build", new JSONObject());
            
            if (buildObj.has("starter")) {
                JSONArray starter = buildObj.optJSONArray("starter", new JSONArray());
                for (int i = 0; i < starter.length(); i++) {
                    starterItems.add(starter.optInt(i, 0));
                }
            }
            
            if (buildObj.has("build")) {
                JSONArray buildPathArray = buildObj.optJSONArray("build", new JSONArray());
                for (int i = 0; i < buildPathArray.length(); i++) {
                    buildPath.add(buildPathArray.optInt(i, 0));
                }
            }
            
            if (buildObj.has("boots")) {
                boots = buildObj.optInt("boots", 0);
            }
        }
    }
    
    public void setBuild(JSONObject buildObj) {
        if (buildObj == null) {
            return;
        }
        
        if (buildObj.has("items")) {
            JSONObject items = buildObj.getJSONObject("items");
            item0 = items.optInt("0", 0);
            item1 = items.optInt("1", 0);
            item2 = items.optInt("2", 0);
            item3 = items.optInt("3", 0);
            item4 = items.optInt("4", 0);
            item5 = items.optInt("5", 0);
            item6 = items.optInt("6", 0);
        }
    }
    
    public JSONObject getBuildAsJson() {
        JSONObject build = new JSONObject();
        
        JSONObject items = new JSONObject();
        items.put("0", item0);
        items.put("1", item1);
        items.put("2", item2);
        items.put("3", item3);
        items.put("4", item4);
        items.put("5", item5);
        items.put("6", item6);
        build.put("items", items);
        
        JSONArray spells = new JSONArray();
        spells.put(summonerSpell1);
        spells.put(summonerSpell2);
        build.put("summoner_spells", spells);
        

        JSONObject runes = new JSONObject();
        runes.put("primary", new JSONArray(primaryRunes));
        runes.put("secondary", new JSONArray(secondaryRunes));
        runes.put("stats", new JSONArray(statsRunes));
        build.put("runes", runes);
        

        build.put("skill_order", new JSONArray(skillOrder));
        

        build.put("augments", new JSONArray(augments));

        JSONObject buildPath = new JSONObject();
        buildPath.put("starter", new JSONArray(starterItems));
        buildPath.put("build", new JSONArray(this.buildPath));
        buildPath.put("boots", boots);
        build.put("build", buildPath);
        
        return build;
    }

    public void setBuild(Document build) {
        if (build == null)
            return;

        // ITEMS
        Document items = build.get("items", Document.class);
        if (items != null) {
            item0 = items.getInteger("0", 0);
            item1 = items.getInteger("1", 0);
            item2 = items.getInteger("2", 0);
            item3 = items.getInteger("3", 0);
            item4 = items.getInteger("4", 0);
            item5 = items.getInteger("5", 0);
            item6 = items.getInteger("6", 0);
        }


        Document runes = build.get("runes", Document.class);
        if (runes != null) {
            primaryRunes = runes.getList("primary", Integer.class, new ArrayList<>());
            secondaryRunes = runes.getList("secondary", Integer.class, new ArrayList<>());
            statsRunes = runes.getList("stats", Integer.class, new ArrayList<>());
        }
        // AUGMENTS
        augments = build.getList("augments", Integer.class, new ArrayList<>());

        // BUILD PATH
        Document buildObj = build.get("build", Document.class);
        if (buildObj != null) {
            starterItems = buildObj.getList("starter", Integer.class, new ArrayList<>());
            buildPath = buildObj.getList("build", Integer.class, new ArrayList<>());
            boots = buildObj.getInteger("boots", 0);
        }
    }
}