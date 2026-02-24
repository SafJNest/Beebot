package com.safjnest.lol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.model.MatchData;
import com.safjnest.lol.model.ParticipantData;
import com.safjnest.mongodb.MongoLeague;
import com.safjnest.mongodb.MongoLeague.SummonerTrackingInfo;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrameEvent;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import java.time.LocalDateTime;

public class MongoTracker {

  private static final long period = TimeConstant.MINUTE * 10;
  private static Set<LOLMatch> matchQueue = ConcurrentHashMap.newKeySet();

  static {
    ChronoTask track = () -> track();
    track.scheduleAtFixedRate(TimeConstant.MINUTE * 0, period, TimeUnit.MILLISECONDS);
    if (!App.isTesting()) {
      ChronoTask trackQueuedGames = () -> popSet();
      trackQueuedGames.scheduleAtFixedTime(0, 0, 0);
      ChronoTask trackSampleGames = () -> retriveSampleGames();
      trackSampleGames.scheduleAtFixedTime(2, 0, 0);
      ChronoTask retriveHighEloEntries = () -> retriveHighEloEntries();
      retriveHighEloEntries.scheduleAtFixedRate(TimeConstant.HOUR, TimeConstant.HOUR, TimeUnit.MILLISECONDS);
    }
  }

  private static void safeSleep(long millisec) {
    try { Thread.sleep(millisec); } 
    catch (Exception ignore) {}
  }


  private static void track() {
    long splitStart = LeagueHandler.getCurrentSplitRange()[0];
    List<SummonerTrackingInfo> accounts = MongoLeague.getSummonerWithTracking(splitStart);
    BotLogger.info("[LPTracker] Start tracking summoners (" + accounts.size() + " accounts)");
    for (SummonerTrackingInfo info : accounts) {
      Summoner summoner = null;
      try {
        summoner = info.summoner.toSummoner();
        LeagueHandler.clearCache(URLEndpoint.V5_MATCHLIST, summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO);
        LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, summoner, null);
        safeSleep(350);

        List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
        if (matchIds.isEmpty()) continue;

        String matchId = matchIds.get(0);
        LeagueShard shard = summoner.getPlatform();
        try {
          shard = LeagueShard.valueOf(matchId.split("_")[0]);
        } catch (Exception e) { }

        if (Long.parseLong(matchId.split("_")[1]) == info.gameId) continue;
        if (shard != summoner.getPlatform()) {
          LOLMatch crossMatch = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
          if (crossMatch != null) analyzeMatch(crossMatch);
          continue;
        }

        LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
        if (match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) continue;
        analyzeMatch(match);
      } catch (Exception e) {
        e.printStackTrace();
        if (summoner != null) BotLogger.error(summoner.toString());
      }
    }
    BotLogger.info("[LPTracker] Finish tracking summoners. Next check at " + SafJNest.getFormattedDate(LocalDateTime.now().plusSeconds(period / 1000), "yyyy-MM-dd HH:mm:ss"));
  }

  /**
   * Analyzes a match, computes rank/lp/gain and build per participant, and saves to MongoDB.
   */
  private static void analyzeMatch(LOLMatch match) {
    if (match == null) return;
    if (!LeagueHandler.isCurrentSplit(match.getGameStartTimestamp()) && match.getQueue() == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return;
    if (MongoLeague.matchExists(match.getPlatform(), match.getGameId())) return;

    MongoLeague.saveSummoners(match);
    HashMap<String, HashMap<String, String>> buildData = analyzeMatchBuild(match, match.getParticipants());

    MatchData matchData = new MatchData(match);
    matchData.events = createJSONEvents(buildData.get("match"));

    List<TierDivisionType> ranks = new ArrayList<>();
    List<ParticipantData> participantsData = new ArrayList<>();
    for (MatchParticipant participant : match.getParticipants()) {
      Summoner summoner = LeagueHandler.getSummonerByPuuid(participant.getPuuid(), match.getPlatform());
      if (summoner == null) continue;
      try {
        LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, summoner, null);
        safeSleep(500);
      } catch (Exception e) { e.printStackTrace(); }
      ParticipantData participantData = pushSummonerMongo(match, summoner, participant, buildData.get(participant.getPuuid()));
      if (participantData != null) {
        ranks.add(participantData.rank != null ? participantData.rank : TierDivisionType.UNRANKED);
        participantsData.add(participantData);
      }
    }
    TierType avgRank = LeagueHandler.getAvarageRank(ranks);
    matchData.rank = avgRank;
    matchData.participants = participantsData;
    MongoLeague.saveMatch(matchData);
    BotLogger.info("[LPTracker] Pushed match data for " + match.getGameId() + " (" + match.getPlatform() + ")");
  }

  /**
   * Builds ParticipantData with rank, lp, gain and build from match + summoner + last game state.
   */
  private static ParticipantData pushSummonerMongo(LOLMatch match, Summoner summoner, MatchParticipant participant, HashMap<String, String> matchDataMap) {
    if (participant.getPuuid().equals("BOT")) return null;
    long splitStart = LeagueHandler.getCurrentSplitRange()[0];
    SummonerTrackingInfo lastState = MongoLeague.getLastGameState(participant.getPuuid(), splitStart);
    long lastGameId = lastState != null ? lastState.gameId : 0;
    TierDivisionType oldDivision = null;
    int oldLp = 0;
    if (lastState != null && lastState.rank != null) {
      try {
        oldDivision = TierDivisionType.valueOf(lastState.rank);
        oldLp = lastState.lp;
      } catch (Exception ignored) {}
    }

    if (match.getGameId() == lastGameId && oldDivision != null) {
      ParticipantData pd = new ParticipantData(participant);
      pd.rank = oldDivision;
      pd.gain = 0;
      pd.kda = participant.getKills() + "/" + participant.getDeaths() + "/" + participant.getAssists();
      if (matchDataMap != null) pd.setBuild(createJSONBuild(matchDataMap));
      return pd;
    }

    List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(summoner.getPlatform(), summoner.getPUUID());
    LeagueEntry league = entries.stream().filter(l -> l.getQueueType().commonName().equals("5v5 Ranked Solo")).findFirst().orElse(null);
    TierDivisionType division = league != null ? league.getTierDivisionType() : TierDivisionType.UNRANKED;
    int lp = league != null ? league.getLeaguePoints() : 0;
    int gain = 0;

    boolean isPromotionToMaster = oldDivision == TierDivisionType.DIAMOND_I && division == TierDivisionType.MASTER_I;
    boolean isMasterPlus = division == TierDivisionType.MASTER_I || division == TierDivisionType.GRANDMASTER_I || division == TierDivisionType.CHALLENGER_I;

    if (oldDivision == null || match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) gain = 0;
    else if ((isPromotionToMaster || !isMasterPlus) && division != oldDivision) {
      gain = 100 - (Math.abs(lp - oldLp));
      gain = division.ordinal() < oldDivision.ordinal() ? gain : -gain;
    } else {
      gain = lp - oldLp;
    }

    ((ChronoTask) () -> MongoLeague.updateSummonerEntries(summoner, entries)).queue();

    ParticipantData participantData = new ParticipantData(participant);
    participantData.rank = division;
    participantData.gain = gain;
    participantData.kda = participant.getKills() + "/" + participant.getDeaths() + "/" + participant.getAssists();
    if (matchDataMap != null) participantData.setBuild(createJSONBuild(matchDataMap));

    participantData.skillOrder = List.of(matchDataMap != null ? matchDataMap.getOrDefault("skill_order", "") : "")
        .stream().flatMap(s -> java.util.Arrays.stream(s.split(","))).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
    participantData.augments = List.of(matchDataMap != null && matchDataMap.containsKey("augments") ? matchDataMap.get("augments") : "")
        .stream().flatMap(s -> java.util.Arrays.stream(s.split(","))).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();

    return participantData;
  }

  public static void queueMatch(LOLMatch match) {
    matchQueue.add(match);
  }

  public static synchronized Set<LOLMatch> getMatchQueueCopy() {
    return new HashSet<>(matchQueue);
  }

  public static void popSet() {
    Set<LOLMatch> toAnalyze;
    synchronized (matchQueue) {
      if (matchQueue.isEmpty()) return;
      toAnalyze = new HashSet<>(matchQueue);
      matchQueue.clear();
    }
    BotLogger.info("[LPTracker] Analyzing " + toAnalyze.size() + " queued matches");
    int i = 0;
    for (LOLMatch match : toAnalyze) {
      try {
        analyzeMatch(match);
        BotLogger.info("[LPTracker] [" + i + " / " + toAnalyze.size() + "] Pushed match data for " + match.getGameId());
      } catch (Exception e) {
        e.printStackTrace();
      }
      i++;
    }
  }

  public static boolean isRemake(LOLMatch match) {
    return match.getGameDuration() <= 330;
  }

  public static String createJSONBuild(HashMap<String, String> matchData) {
    if (matchData == null) return "{}";
    JSONObject json = new JSONObject();
    JSONObject build = new JSONObject();
    build.put("starter", (matchData.getOrDefault("starter", "")).split(","));
    build.put("build", (matchData.getOrDefault("items", "")).split(","));
    build.put("boots", matchData.getOrDefault("boots", "0"));
    if (matchData.containsKey("support_item")) build.put("support_item", matchData.get("support_item"));
    json.put("build", build);
    json.put("skill_order", (matchData.getOrDefault("skill_order", "")).split(","));
    JSONObject runes = new JSONObject();
    String perks0 = matchData.get("perks-0");
    String perks1 = matchData.get("perks-1");
    String stats = matchData.get("stats");
    runes.put("primary", (perks0 != null ? perks0 : "").split(","));
    runes.put("secondary", (perks1 != null ? perks1 : "").split(","));
    runes.put("stats", (stats != null ? stats : "").split(","));
    json.put("runes", runes);
    json.put("summoner_spells", (matchData.getOrDefault("summoner_spells", "")).split(","));
    String[] itemsArr = (matchData.getOrDefault("items", "")).split(",");
    JSONObject itemsObj = new JSONObject();
    for (int idx = 0; idx < itemsArr.length; idx++) {
      int itemId = itemsArr[idx].isEmpty() ? 0 : Integer.parseInt(itemsArr[idx]);
      itemsObj.put(String.valueOf(idx), itemId);
    }
    json.put("items", itemsObj);
    if (matchData.containsKey("augments")) json.put("augments", matchData.get("augments").split(","));
    return json.toString();
  }

  public static HashMap<String, String> analyzeChampionData(int champion, LaneType lane) {
    return MongoLeague.analyzeChampionData(champion, lane);
  }

  public static void retriveMatchHistory(Summoner summoner) {
    retriveMatchHistory(summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO);
  }

  public static void retriveMatchHistory(Summoner summoner, GameQueueType queue) {
    try {
      List<String> matchIds = new ArrayList<>();
      List<String> retrieved = summoner.getLeagueGames().withCount(100).withQueue(queue).get();
      do {
        matchIds.addAll(retrieved);
        safeSleep(350);
        for (String matchId : retrieved) {
          try {
            LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), matchId);
            if (match != null) queueMatch(match);
          } catch (Exception e) { e.printStackTrace(); }
        }
        retrieved = summoner.getLeagueGames().withCount(100).withQueue(queue).withBeginIndex(matchIds.size()).get();
      } while (!retrieved.isEmpty());
    } catch (Exception e) { e.printStackTrace(); }
  }

  public static void retriveSampleGames() {
    BotLogger.info("[LPTracker] Pushing sample matches");
    DataCall.getCacheProvider().clear(URLEndpoint.V5_TIMELINE, new LinkedHashMap<>());
    String currentPatch = LeagueHandler.getVersion().split("\\.")[0] + "." + LeagueHandler.getVersion().split("\\.")[1];
    for (LeagueShard shard : LeagueHandler.getActiveShards()) {
      ChronoTask shardTask = () -> {
        try {
          Map<String, Object> data = new LinkedHashMap<>();
          data.put("platform", shard);
          data.put("queue", GameQueueType.RANKED_SOLO_5X5);
          LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_CHALLENGER, data);
          safeSleep(500);
          List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, GameQueueType.RANKED_SOLO_5X5, TierDivisionType.CHALLENGER_I, 0);
          BotLogger.info("[LPTracker] Start analyzing " + entries.size() + " matches for region " + shard);
          for (int j = 0; j < entries.size(); j++) {
            try {
              LeagueEntry entry = entries.get(j);
              Summoner summoner = LeagueHandler.getSummonerByPuuid(entry.getPuuid(), shard);
              if (summoner == null) continue;
              RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
              BotLogger.info("[LPTracker] Analyzing summoner " + account.getName() + "#" + account.getTag() + " | " + j + "/" + entries.size());
              List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
              if (matchIds.isEmpty()) continue;
              int k = 0;
              LOLMatch match;
              do {
                String matchId = matchIds.get(k);
                match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
                analyzeMatch(match);
                safeSleep(350);
                k++;
              } while (match.getGameVersion().startsWith(currentPatch) && k < matchIds.size());
            } catch (Exception e) { e.printStackTrace(); }
          }
        } catch (Exception e) { e.printStackTrace(); }
      };
      shardTask.queue();
    }
  }

  public static void retriveHighEloEntries() {
    BotLogger.info("[LPTracker] Pushing high elo entries");
    for (TierDivisionType tier : List.of(TierDivisionType.MASTER_I, TierDivisionType.GRANDMASTER_I, TierDivisionType.CHALLENGER_I)) {
      for (LeagueShard shard : LeagueHandler.getActiveShards()) {
        for (GameQueueType queue : List.of(GameQueueType.RANKED_SOLO_5X5, GameQueueType.RANKED_FLEX_SR)) {
          try {
            URLEndpoint endpoint = tier == TierDivisionType.CHALLENGER_I ? URLEndpoint.V4_LEAGUE_CHALLENGER : (tier == TierDivisionType.GRANDMASTER_I ? URLEndpoint.V4_LEAGUE_GRANDMASTER : URLEndpoint.V4_LEAGUE_MASTER);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("platform", shard);
            data.put("queue", queue);
            LeagueHandler.clearCache(endpoint, data);
            safeSleep(500);
            List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, queue, tier, 0);
            BotLogger.info("[LPTracker] Start analyzing " + entries.size() + " " + tier + " (" + queue + ") for region " + shard);
            for (LeagueEntry entry : entries) {
              try {
                Summoner s = LeagueHandler.getSummonerByPuuid(entry.getPuuid(), shard);
                if (s != null) {
                  MongoLeague.saveSummoner(s, null);
                  MongoLeague.updateSummonerEntries(s, List.of(entry));
                }
              } catch (Exception ex) { /* rate limit etc */ }
            }
          } catch (Exception e) { e.printStackTrace(); }
        }
      }
    }
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
