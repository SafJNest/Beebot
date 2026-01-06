package com.safjnest.lol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.model.MatchData;
import com.safjnest.lol.model.ParticipantData;
import com.safjnest.lol.model.SummonerData;
import com.safjnest.mongodb.MongoLeague;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrameEvent;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public class MongoTracker {

  private static ConcurrentHashMap<LeagueShard, Set<LOLMatch>> queue;

  static {  
    ChronoTask track = () -> track();
    track.scheduleAtFixedRate(TimeConstant.MINUTE * 0, TimeConstant.MINUTE * 10, TimeUnit.MILLISECONDS);

    //ChronoTask trackQueuedGames = () -> popSet();
    //trackQueuedGames.scheduleAtFixedTime(0, 0, 0);

    //ChronoTask trackSampleGames = () -> retriveSampleGames();
    //trackSampleGames.scheduleAtFixedTime(2, 0, 0);
    
    //ChronoTask retriveHighEloEntries = () -> retriveHighEloEntries();
    //retriveHighEloEntries.scheduleAtFixedRate(TimeConstant.HOUR, TimeConstant.HOUR, TimeUnit.MILLISECONDS);
    
  }

  private static void safeSleep(long millisec) {
    try { Thread.sleep(millisec); } 
    catch (Exception ignore) {}
  }


  private static void track() {
    List<SummonerData> summoners = MongoLeague.getSummonerWithTracking();
    BotLogger.info("[LPTracker] Start tracking summoners (" + summoners.size() + " accounts)");
    for (SummonerData summonerData : summoners) {
      Summoner summoner = null;
      try {
        summoner = summonerData.toSummoner();
        
        LeagueHandler.clearCache(URLEndpoint.V5_MATCHLIST, summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO);
        LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, summoner, null);

        safeSleep(350);

        List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get(); //Funny that this could give error but now without this could give error :)
        if (matchIds.isEmpty()) continue;

        String matchId = matchIds.get(0);
        LeagueShard shard = summoner.getPlatform();
        try {
          shard = LeagueShard.valueOf(matchId.split("_")[0]);
        } catch (Exception e) { }

        LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
        if (match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) continue;

        analyzeMatch(match);
        
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static void analyzeMatch(LOLMatch match) {
    HashMap<String, HashMap<String, String>> events = analyzeMatchBuild(match, match.getParticipants());
    
    MatchData matchData = new MatchData(match);
    matchData.events = createJSONEvents(events.get("match"));

    List<ParticipantData> participantsData = new ArrayList<>();
    for (MatchParticipant participant : match.getParticipants()) {
      ParticipantData participantData = analyzeParticipant(participant, events.get(participant.getPuuid()));
      participantsData.add(participantData);
    }
    matchData.participants = participantsData;
    MongoLeague.saveMatch(matchData);
  }

  private static ParticipantData analyzeParticipant(MatchParticipant participant, HashMap<String, String> data) {
    ParticipantData participantData = new ParticipantData(participant);

    participantData.skillOrder = 
        List.of(data.getOrDefault("skill_order", "").split(","))
            .stream()
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .toList();

    participantData.augments = 
        List.of(data.getOrDefault("augments", "").split(","))
            .stream()
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .toList();

    

    return participantData;
  }

  public static HashMap<String, HashMap<String, String>> analyzeMatchBuild(LOLMatch match, List<MatchParticipant> partecipants) {
    Map<Integer, Item> items = LeagueHandler.getRiotApi().getDDragonAPI().getItems();

    HashMap<String, HashMap<String, String>> matchData = new HashMap<>();
    for (MatchParticipant partecipant : partecipants) {
        LaneType lane = partecipant.getChampionSelectLane() != null ? partecipant.getChampionSelectLane() : partecipant.getLane();

        matchData.put(partecipant.getPuuid(), new HashMap<>());
        matchData.get(partecipant.getPuuid()).put("win", partecipant.didWin() ? "1" : "0");
        matchData.get(partecipant.getPuuid()).put("lane", String.valueOf(lane.ordinal()));
        matchData.get(partecipant.getPuuid()).put("champion", String.valueOf(partecipant.getChampionId()));
        matchData.get(partecipant.getPuuid()).put("stats", partecipant.getPerks().getStatPerks().getDefense() + "," + partecipant.getPerks().getStatPerks().getFlex() + "," + partecipant.getPerks().getStatPerks().getOffense());
        for (int i = 0; i < 2; i++) {
            for (PerkSelection perk : partecipant.getPerks().getPerkStyles().get(i).getSelections()) {
                String perkList = matchData.get(partecipant.getPuuid()).getOrDefault("perks-" + i, "");
                if (perkList.isEmpty()) perkList = perk.getPerk() + "";
                else perkList += "," + perk.getPerk();
                matchData.get(partecipant.getPuuid()).put("perks-" + i, perkList);
            }
            matchData.get(partecipant.getPuuid()).put("perks-" + i, partecipant.getPerks().getPerkStyles().get(i).getStyle() + "," + matchData.get(partecipant.getPuuid()).get("perks-" + i));
        }
        matchData.get(partecipant.getPuuid()).put("summoner_spells", partecipant.getSummoner1Id() + "," + partecipant.getSummoner2Id());
        matchData.get(partecipant.getPuuid()).put("items", partecipant.getItem0() + "," + partecipant.getItem1() + "," + partecipant.getItem2() + "," + partecipant.getItem3() + "," + partecipant.getItem4() + "," + partecipant.getItem5() + "," + partecipant.getItem6());

        if (match.getQueue() == GameQueueType.CHERRY) {
            String augmentList = "";
            if (partecipant.getPlayerAugment1() != 0) augmentList = partecipant.getPlayerAugment1() + "";
            if (partecipant.getPlayerAugment2() != 0) augmentList += "," + partecipant.getPlayerAugment2();
            if (partecipant.getPlayerAugment3() != 0) augmentList += "," + partecipant.getPlayerAugment3();
            if (partecipant.getPlayerAugment4() != 0) augmentList += "," + partecipant.getPlayerAugment4();

            matchData.get(partecipant.getPuuid()).put("augments", augmentList);
        }

        /**
         * i cant get the evolution of support item from the event
         * so i can just check all the slot and see which item i have and how i built it
         */
        if (lane == LaneType.UTILITY) {
            String supportItem = null;
            if (isSuppItemFromId(partecipant.getItem0()) != null)
                supportItem = String.valueOf(partecipant.getItem0());
            else if (isSuppItemFromId(partecipant.getItem1()) != null)
                supportItem = String.valueOf(partecipant.getItem1());
            else if (isSuppItemFromId(partecipant.getItem2()) != null)
                supportItem = String.valueOf(partecipant.getItem2());
            else if (isSuppItemFromId(partecipant.getItem3()) != null)
                supportItem = String.valueOf(partecipant.getItem3());
            else if (isSuppItemFromId(partecipant.getItem4()) != null)
                supportItem = String.valueOf(partecipant.getItem4());
            else if (isSuppItemFromId(partecipant.getItem5()) != null)
                supportItem = String.valueOf(partecipant.getItem5());
            else if (isSuppItemFromId(partecipant.getItem6()) != null)
                supportItem = String.valueOf(partecipant.getItem6());

            if (supportItem != null) matchData.get(partecipant.getPuuid()).put("support_item", supportItem);
        }

    }

    LOLTimeline timeline = match.getTimeline();
    timeline.getParticipants().forEach(partecipant -> {
        matchData.put(String.valueOf(partecipant.getParticipantId()), matchData.get(partecipant.getPuuid()));
        matchData.remove(partecipant.getPuuid());
    });
    matchData.put("match", new HashMap<>());

    for (int i = 0; i < timeline.getFrames().size(); i++) {
        for (TimelineFrameEvent event : timeline.getFrames().get(i).getEvents()) {
          Item item;
          String participantId = String.valueOf(event.getParticipantId());
          String itemType = i == 1 ? "starter" : "items";

          try {
            switch (event.getType()) {
              case ITEM_PURCHASED:
                item = items.get(event.getItemId());
                if (item == null) continue;

                if (item.getFrom() != null && item.getFrom().contains("1001")) {
                    matchData.get(participantId).put("boots", item.getId() + "");
                    continue;
                }

                if (i != 1 && item.getDepth() != 3) continue;

                String itemList = matchData.get(participantId).getOrDefault(itemType, "");
                if (itemList.isEmpty()) itemList = item.getId() + "";
                else itemList += "," + item.getId();
                matchData.get(participantId).put(itemType, itemList);
                break;
              case ITEM_UNDO:
              case ITEM_SOLD:
                item = items.get(event.getBeforeId());
                if (item == null) continue;
                if (i != 1 && item.getDepth() != 3) continue;

                String[] itemsList = matchData.get(participantId).get(itemType).split(",");
                String undoList = "";
                for (String itemStr : itemsList) {
                    if (!itemStr.equals(item.getId() + "")) {
                        if (!undoList.isEmpty()) undoList += ",";
                        undoList += itemStr;
                    }
                }
                matchData.get(participantId).put(itemType, undoList);
                break;
              case SKILL_LEVEL_UP:
                String skillList = matchData.get(participantId).getOrDefault("skill_order", "");
                if (skillList.isEmpty()) skillList = event.getSkillSlot() + "";
                else skillList += "," + event.getSkillSlot();
                matchData.get(participantId).put("skill_order", skillList);
                break;
              case ELITE_MONSTER_KILL:    
                if (event.getMonsterType() == null) continue;
                String monsterEvents = matchData.get("match").getOrDefault("monster_events", "");

                String monster = event.getMonsterType().name();
                String subType = event.getMonsterSubType() != null ? event.getMonsterSubType().name() : "";
                int killerId = event.getKillerId();
                List<Integer> assistIds = event.getAssistingParticipantIds() != null ? event.getAssistingParticipantIds() : new ArrayList<>();

                String eventJson = "{\"monster\":\"" + monster + "\",\"subtype\":\"" + subType + "\",\"killer\":" + killerId + ",\"assists\":[";
                for (int assistId : assistIds) {
                    if (assistId == 0) continue;
                    if (eventJson.endsWith("[")) eventJson += assistId;
                    else eventJson += "," + assistId;
                }
                eventJson += "]}";
                if (monsterEvents.isEmpty()) monsterEvents = eventJson;
                else monsterEvents += "," + eventJson;
                matchData.get("match").put("monster_events", monsterEvents);
                break;
              case BUILDING_KILL:
                String buildingEvents = matchData.get("match").getOrDefault("building_events", "");
                String building = event.getBuildingType() != null ? event.getBuildingType().name() : "";
                int killerIdBuilding = event.getKillerId();
                List<Integer> assistIdsBuilding = event.getAssistingParticipantIds() != null ? event.getAssistingParticipantIds() : new ArrayList<>();
                String eventJsonBuilding = "{\"building\":\"" + building + "\",\"killer\":" + killerIdBuilding + ",\"assists\":[";
                for (int assistId : assistIdsBuilding) {
                    if (assistId == 0) continue;
                    if (eventJsonBuilding.endsWith("[")) eventJsonBuilding += assistId;
                    else eventJsonBuilding += "," + assistId;
                }
                eventJsonBuilding += "]}";
                if (buildingEvents.isEmpty()) buildingEvents = eventJsonBuilding;
                else buildingEvents += "," + eventJsonBuilding;
                matchData.get("match").put("building_events", buildingEvents);
                break;
              default:
                break;
            }
          } catch (Exception e) {
              
          }
            
        }
    }

    HashMap<String, String> matchParticipants = new HashMap<>();
    timeline.getParticipants().forEach(partecipant -> {
        matchParticipants.put(String.valueOf(partecipant.getParticipantId()), partecipant.getPuuid());

        matchData.put(partecipant.getPuuid(), matchData.get(String.valueOf(partecipant.getParticipantId())));
        matchData.remove(String.valueOf(partecipant.getParticipantId()));

    });
    JSONObject matchJson = new JSONObject(matchParticipants);

    matchData.get("match").put("participants", matchJson.toString());
    return matchData;
  }

  private static Item isSuppItemFromId(int itemId) {
      if (itemId == 0) return null;
      Item item = LeagueHandler.getRiotApi().getDDragonAPI().getItems().get(itemId);
      if (item == null) return null;//old item or removed one? not sure
      if (item.getFrom() == null) return null;
      return item.getFrom().contains("3867") ? item : null;
  }

    public static JSONObject createJSONEvents(HashMap<String, String> matchData) {
        JSONObject json = new JSONObject();

        for (String key : new String[]{"monster_events", "building_events", "participants"}) {
            String raw = matchData.getOrDefault(key, "").trim();
            if (raw.isEmpty()) {
                json.put(key, new JSONArray());
                continue;
            }

            if (key.equals("participants")) {
                try {
                    JSONObject parsed = new JSONObject(raw);
                    json.put(key, parsed);
                    continue;
                } catch (Exception e) { }
            }
            
            try {
                JSONArray parsed = new JSONArray("[" + raw + "]");
                json.put(key, parsed);
            } catch (Exception e) { }
        }

        return json;
    }





  
}
