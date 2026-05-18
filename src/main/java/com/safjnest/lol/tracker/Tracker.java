package com.safjnest.lol.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.core.Chronos;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.ItemUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

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

import java.sql.Timestamp;

public class Tracker {

    private static long period = TimeConstant.MINUTE * 10;

    private static List<GameQueueType> toTrack = List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.CHERRY);


    static void retriveSummoners() {
        try {
            QueryResult result = LeagueDB.getRegistredLolAccount(LeagueHandler.getCurrentSplitRange()[0]);
            BotLogger.info("[LPTracker] Start tracking summoners (" + result.size() + " accounts)");
            for (QueryRecord account : result) {
                Summoner summoner = null;
                try {
                    summoner = LeagueService.getSummonerByPuuid(account.get("puuid"), account.getAsLeagueShard("region"));
                    if (summoner == null) 
                        throw new Exception("account null ??????");
                    
                    LeagueHandler.clearCache(URLEndpoint.V5_MATCHLIST, summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO);
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, summoner, null);
            
                    try { Thread.sleep(350); }
                    catch (InterruptedException e) {e.printStackTrace();}
            
                    List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
                    if (matchIds.isEmpty()) continue;
            
                    String matchId = matchIds.get(0);
                    LeagueShard shard = summoner.getPlatform();
                    try {
                        shard = LeagueShard.valueOf(matchId.split("_")[0]);
                    } catch (Exception e) { }

                    if (Long.parseLong(matchId.split("_")[1]) == account.getAsLong("game_id")) continue;
                    else if (shard != summoner.getPlatform()) {
                        analyzeMatchHistory(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LeagueService.getSummonerByPuuid(summoner.getPUUID(), shard)).complete();
                        continue;
                    }

                    LOLMatch match = LeagueService.getMatch(matchId, shard);
                    if (match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) continue;
                    ChronoTask task = analyzeMatchHistory(match, summoner, account);
                    if (task != null) task.complete();
                } catch (Exception e) {
                    e.printStackTrace();
                    BotLogger.error(summoner.toString());
                }
            }
            BotLogger.info("[LPTracker] Finish tracking summoners. Next check at " + SafJNest.getFormattedDate(LocalDateTime.now().plusSeconds(period / 1000), "yyyy-MM-dd HH:mm:ss"));
        }
        catch (Exception e) {e.printStackTrace();}
    }

    /**
     * In the future, this function will not save the games but will simply analyze them and push the data into tables with already processed data to reduce db size.
     * <p>
     * For now, it will just save the data into the db
     * <p>
     * im lazy UwU
     */
    public static void analyzeQueue() {
        Set<LOLMatch> toAnalyze = popQueue();
        if (toAnalyze.isEmpty()) return;

        BotLogger.info("[LPTracker] Analyzing " + toAnalyze.size() + " queued matches");
        int i = 0;
        for (LOLMatch match : toAnalyze) {
            try {
                analyzeMatchHistory(match).completeWithException();
                BotLogger.info("[LPTracker] [" + i + " / " + toAnalyze.size() + "] Pushed match data for " + match.getGameId() + " (" + match.getPlatform() + " - " + match.getQueue() + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
            i++;
        }
    }

    public static void queueMatch(LOLMatch match) {
        if (match == null) return;
        RedisClient.sadd(RedisKey.TRACKER_PENDING_MATCH_LIST.of(), LeagueService.putMatch(match));
    }
    
    public static Set<LOLMatch> popQueue() {
        Set<String> ids = RedisClient.smembers(RedisKey.TRACKER_PENDING_MATCH_LIST.of());
        return ids.stream().map(id -> LeagueService.getMatch(id, LeagueShard.valueOf(id.split("_")[0]))).collect(Collectors.toSet());
    }
    
    public static Set<LOLMatch> copyQueue() {
        List<String> ids = RedisClient.lrangeAll(RedisKey.TRACKER_PENDING_MATCH_LIST.of());
        return ids.stream().map(id -> LeagueService.getMatch(id, LeagueShard.valueOf(id.split("_")[0]))).collect(Collectors.toSet());
    }

    public static Summoner checkSummoner(MatchParticipant participant, Summoner summoner) {
        if (summoner.getPUUID().equals(participant.getPuuid()))
            return summoner;
        
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("platform", summoner.getPlatform());
        data.put("puuid", participant.getPuuid());
        LeagueHandler.clearCache(URLEndpoint.V4_SUMMONER_BY_PUUID, data);

        summoner = LeagueService.getSummonerByPuuid(participant.getPuuid(), summoner.getPlatform());
        if (summoner.getPUUID().equals(participant.getPuuid()))
            return summoner;
        return null;
    }


//     ▄████████ ███▄▄▄▄      ▄████████  ▄█       ▄██   ▄    ▄███████▄     ▄████████
//    ███    ███ ███▀▀▀██▄   ███    ███ ███       ███   ██▄ ██▀     ▄██   ███    ███
//    ███    ███ ███   ███   ███    ███ ███       ███▄▄▄███       ▄███▀   ███    █▀
//    ███    ███ ███   ███   ███    ███ ███       ▀▀▀▀▀▀███  ▀█▀▄███▀▄▄  ▄███▄▄▄
//  ▀███████████ ███   ███ ▀███████████ ███       ▄██   ███   ▄███▀   ▀ ▀▀███▀▀▀
//    ███    ███ ███   ███   ███    ███ ███       ███   ███ ▄███▀         ███    █▄
//    ███    ███ ███   ███   ███    ███ ███▌    ▄ ███   ███ ███▄     ▄█   ███    ███
//    ███    █▀   ▀█   █▀    ███    █▀  █████▄▄██  ▀█████▀   ▀████████▀   ██████████
//                                      ▀


    public static ChronoTask analyzeMatchHistory(GameQueueType queue, Summoner summoner) {
        if (toTrack.indexOf(queue) == -1) return Chronos.NULL;

        QueryRecord row = LeagueDB.getRegistredLolAccount(LeagueDB.addLOLAccount(summoner), LeagueHandler.getCurrentSplitRange()[0]);
        //if (row.emptyValues() && queue == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return Chronos.NULL;

        try { Thread.sleep(350); }
        catch (InterruptedException e) {e.printStackTrace();}

        List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
        if (matchIds.isEmpty()) return Chronos.NULL;

        String matchId = matchIds.get(0);
        LeagueShard shard = summoner.getPlatform();
        try {
            shard = LeagueShard.valueOf(matchId.split("_")[0]);
        } catch (Exception e) { }

        if (Long.parseLong(matchId.split("_")[1]) == row.getAsLong("game_id")) return Chronos.NULL;
        else if (shard != summoner.getPlatform()) {
            return analyzeMatchHistory(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LeagueService.getSummonerByPuuid(summoner.getPUUID(), shard));
        }

        LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
        if (match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) return Chronos.NULL;
        return analyzeMatchHistory(match, summoner, row);
    }

    public static ChronoTask analyzeMatchHistory(LOLMatch match, Summoner summoner, QueryRecord dataGame) {
        ChronoTask task = () -> {
            if (!LeagueHandler.isCurrentSplit(match.getGameStartTimestamp()) && match.getQueue() == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return;
            if (isRemake(match)) return;
            
            int summoner_match_id = LeagueDB.saveMatch(match);
            LeagueHandler.updateSummonerDB(match);

            HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(match, match.getParticipants());

            List<TierDivisionType> ranks = new ArrayList<>();
            for (MatchParticipant partecipant : match.getParticipants()) {
                if (partecipant.getPuuid().equals(summoner.getPUUID())) {
                    ranks.add(pushSummoner(match, summoner_match_id, summoner, partecipant, dataGame, matchData.get(partecipant.getPuuid())));
                    continue;
                }

                Summoner toPush = LeagueService.getSummonerByPuuid(partecipant.getPuuid(), match.getPlatform());
                toPush = checkSummoner(partecipant, toPush);
                if (toPush == null) {
                    BotLogger.error("CLEAR " + partecipant.getPuuid());
                    continue;
                }
                try { 
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, toPush, null);
                    Thread.sleep(500); 
                }
                catch (InterruptedException e) {e.printStackTrace();}
                ranks.add(pushSummoner(match, summoner_match_id, toPush, partecipant, matchData.get(partecipant.getPuuid())));
            }
            TierType avgRank = TierDivisionUtils.getAvarageRank(ranks);
            LeagueDB.setMatchRank(summoner_match_id, avgRank);
            LeagueDB.setMatchEvent(summoner_match_id, createJSONEvents(matchData.get("match")));
            
            BotLogger.info("[LPTracker] Pushed match data for " + LeagueHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");
        };
        return task;
    }

    public static ChronoTask analyzeMatchHistory(LOLMatch match) {
        return () -> {
            int summoner_match_id = LeagueDB.saveMatch(match, true);
            if (summoner_match_id == 0) {
                BotLogger.info("[LPTracker] Match " + match.getGameId() + " already tracked");
                return;
            }
            LeagueHandler.updateSummonerDB(match);

            HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(match, match.getParticipants());

            List<TierDivisionType> ranks = new ArrayList<>();
            for (MatchParticipant partecipant : match.getParticipants()) {
                Summoner summoner = LeagueService.getSummonerByPuuid(partecipant.getPuuid(), match.getPlatform());
                if (summoner == null) continue;
                try { 
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, summoner, null);
                    Thread.sleep(500); 
                }
                catch (InterruptedException e) {e.printStackTrace();}

                TierDivisionType rank = pushSummoner(match, summoner_match_id, summoner, partecipant, matchData.get(partecipant.getPuuid()));
                ranks.add(rank);
            }
            TierType avgRank = TierDivisionUtils.getAvarageRank(ranks);
            LeagueDB.setMatchRank(summoner_match_id, avgRank);
            LeagueDB.setMatchEvent(summoner_match_id, createJSONEvents(matchData.get("match")));
        };
    }

//     ▄███████▄ ███    █▄     ▄████████    ▄█    █▄
//    ███    ███ ███    ███   ███    ███   ███    ███
//    ███    ███ ███    ███   ███    █▀    ███    ███
//    ███    ███ ███    ███   ███         ▄███▄▄▄▄███▄▄
//  ▀█████████▀  ███    ███ ▀███████████ ▀▀███▀▀▀▀███▀
//    ███        ███    ███          ███   ███    ███
//    ███        ███    ███    ▄█    ███   ███    ███
//   ▄████▀      ████████▀   ▄████████▀    ███    █▀
//

    public static TierDivisionType pushSummoner(LOLMatch match, int summonerMatch, Summoner summoner, MatchParticipant partecipant, HashMap<String, String> matchData) {
        QueryRecord row = LeagueDB.getRegistredLolAccount(LeagueDB.addLOLAccount(summoner), LeagueHandler.getCurrentSplitRange()[0]);
        return pushSummoner(match, summonerMatch, summoner, partecipant, row, matchData);
    }

    private static TierDivisionType pushSummoner(LOLMatch match, int summonerMatch, Summoner summoner, MatchParticipant participant, QueryRecord dataGame, HashMap<String, String> matchData) {
        if (match.getGameId() == dataGame.getAsLong("game_id")) return dataGame.getAsTier("rank");
        if (participant.getPuuid().equals("BOT")) return TierDivisionType.UNRANKED;

        List<LeagueEntry> entries = LeagueService.getLeagueEntries(summoner.getPUUID(), summoner.getPlatform());
        LeagueEntry league = entries.stream().filter(l -> l.getQueueType().commonName().equals("5v5 Ranked Solo")).findFirst().orElse(null);

        TierDivisionType oldDivision = dataGame.getAsTier("rank");
        TierDivisionType division = league != null ? league.getTierDivisionType() : TierDivisionType.UNRANKED;

        int lp = league != null ? league.getLeaguePoints() : 0;
        int gain = 0;

        boolean isPromotionToMaster = oldDivision == TierDivisionType.DIAMOND_I && division == TierDivisionType.MASTER_I;
        boolean isMasterPlus = division == TierDivisionType.MASTER_I || division == TierDivisionType.GRANDMASTER_I || division == TierDivisionType.CHALLENGER_I;

        if (dataGame.get("rank") == null || match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) gain = 0;
        else if ((isPromotionToMaster || !isMasterPlus) && division != dataGame.getAsTier("rank")) {
            gain = 100 - (Math.abs(lp - dataGame.getAsInt("lp")));
            gain = division.ordinal() < dataGame.getAsTier("rank").ordinal() ? gain : -gain;
        } else {
            gain = lp - dataGame.getAsInt("lp");
        }
        int summonerId = LeagueDB.addLOLAccount(summoner);
        ((ChronoTask) () -> {
            LeagueDB.updateSummonerEntries(summonerId, entries);
            //LeagueDB.updateSummonerMasteries(summonerId, summoner.getChampionMasteries());
        }).queue();

        LeagueDB.setSummonerData(summonerId, summonerMatch, participant, division, lp, gain, createJSONBuild(matchData));
        return division;
    }

//  ███    █▄      ███      ▄█   ▄█
//  ███    ███ ▀█████████▄ ███  ███
//  ███    ███    ▀███▀▀██ ███▌ ███
//  ███    ███     ███   ▀ ███▌ ███
//  ███    ███     ███     ███▌ ███
//  ███    ███     ███     ███  ███
//  ███    ███     ███     ███  ███▌    ▄
//  ████████▀     ▄████▀   █▀   █████▄▄██
//                              ▀


    public static boolean isRemake(LOLMatch match) {
        return match.getGameDuration() <= 330;
    }

    public static String createJSONBuild(HashMap<String, String> matchData) {
        JSONObject json = new JSONObject();
        JSONObject build = new JSONObject();

        JSONObject runes = new JSONObject();


        build.put("starter", matchData.getOrDefault("starter", "").split(","));
        build.put("build", matchData.getOrDefault("items", "").split(","));
        build.put("boots", matchData.getOrDefault("boots", "0"));

        if (matchData.containsKey("support_item"))
            build.put("support_item", matchData.get("support_item"));

        json.put("build", build);
        json.put("skill_order", matchData.getOrDefault("skill_order", "").split(","));

        runes.put("primary", matchData.get("perks-0").split(","));
        runes.put("secondary", matchData.get("perks-1").split(","));
        runes.put("stats", matchData.get("stats").split(","));

        json.put("runes", runes);
        json.put("summoner_spells", matchData.get("summoner_spells").split(","));

        String[] itemsArr = matchData.get("items").split(",");
        JSONObject itemsObj = new JSONObject();
        for (int i = 0; i < itemsArr.length; i++) {
            int itemId = itemsArr[i].isEmpty() ? 0 : Integer.parseInt(itemsArr[i]);
            itemsObj.put(String.valueOf(i), itemId);
        }
        json.put("items", itemsObj);

        if (matchData.containsKey("augments"))
            json.put("augments", matchData.get("augments").split(","));

        if (matchData.containsKey("prismatics")) 
            json.put("prismatics", matchData.get("prismatics").split(","));

        return json.toString();

    }

    public static String createJSONEvents(HashMap<String, String> matchData) {
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

        return json.toString();
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

            String itemsIds = partecipant.getItem0() + "," + partecipant.getItem1() + "," + partecipant.getItem2() + "," + partecipant.getItem3() + "," + partecipant.getItem4() + "," + partecipant.getItem5() + "," + partecipant.getItem6();
            

            matchData.get(partecipant.getPuuid()).put("summoner_spells", partecipant.getSummoner1Id() + "," + partecipant.getSummoner2Id());
            matchData.get(partecipant.getPuuid()).put("items", itemsIds);

            if (GameQueueTypeUtils.isCherry(match.getQueue())) {
                String augmentList = "";
                if (partecipant.getPlayerAugment1() != 0) augmentList = partecipant.getPlayerAugment1() + "";
                if (partecipant.getPlayerAugment2() != 0) augmentList += "," + partecipant.getPlayerAugment2();
                if (partecipant.getPlayerAugment3() != 0) augmentList += "," + partecipant.getPlayerAugment3();
                if (partecipant.getPlayerAugment4() != 0) augmentList += "," + partecipant.getPlayerAugment4();

                List<String> prismatics = List.of(itemsIds.split(",")).stream().filter(ItemUtils::isPrismatic).collect(Collectors.toList());
                if (!prismatics.isEmpty()) {
                    matchData.get(partecipant.getPuuid()).put("prismatics", String.join(",", prismatics));
                }
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
        Map<String, List<String>> matchItemData = new HashMap<>();

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
                            
                            if (ItemUtils.isBoots(item)) {
                                matchData.get(participantId).put("boots", item.getId() + "");
                                continue;
                            }

                            if (i != 1 && item.getDepth() != 3) continue;

                            List<String> itemList = matchItemData.computeIfAbsent(participantId + "-" + itemType, k -> new ArrayList<>());
                            itemList.add(item.getId() + "");
                            matchItemData.put(participantId + "-" + itemType, itemList);
                            matchData.get(participantId).put(itemType, String.join(",", itemList));
                    
                            break;
                        case ITEM_UNDO:
                        case ITEM_SOLD:
                            item = items.get(event.getBeforeId());
                            if (item == null) continue;
                            if (i != 1 && item.getDepth() != 3) continue;
                    
                            List<String> currentList = matchItemData.get(participantId + "-" + itemType);
                            if (currentList != null) {
                                currentList.remove(item.getId() + "");
                            }
                            matchData.get(participantId).put(itemType, String.join(",", currentList));
                    
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

    public static void retriveSampleGames(GameQueueType queue) {
        BotLogger.info("[LPTracker] Pushing sample matches");
        String currentPatch = LeagueHandler.getVersion().split("\\.")[0] + "." + LeagueHandler.getVersion().split("\\.")[1];
        String previousPatch = LeagueHandler.getPreviousVersion().split("\\.")[0] + "." + LeagueHandler.getPreviousVersion().split("\\.")[1];
    
        long[] splitRange = LeagueHandler.getCurrentSplitRange();
    
        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            ChronoTask shardTask = () -> {
                long threshold = (splitRange != null) ? LeagueDB.get().query("SELECT time_start FROM `match` WHERE patch_major = '"+ previousPatch + "' and region = '"+ shard + "' ORDER BY time_start DESC LIMIT 1").get(0).getAsEpochSecond("time_start") : 0;
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("platform", shard);
                data.put("queue", GameQueueType.RANKED_SOLO_5X5);
                LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_CHALLENGER, data);
                try { Thread.sleep(500); } catch (InterruptedException e) {}
    
                List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI()
                    .getLeagueByTierDivision(shard, GameQueueType.RANKED_SOLO_5X5, TierDivisionType.CHALLENGER_I, 0);
    
                record MatchEntry(LeagueEntry entry, Summoner summoner, String matchId) {}
                List<MatchEntry> allMatches = new ArrayList<>();
                Set<String> seenMatchIds = new HashSet<>();
    
                for (LeagueEntry entry : entries) {
                    try {
                        LeagueService.puWeaktLeagueEntry(shard, entry);
                        Summoner summoner = LeagueService.getSummonerByPuuid(entry.getPuuid(), shard);
                        List<String> matchIds = new ArrayList<>();
                        for (int start = 0; matchIds.size() == start; start += 100) {
                            matchIds.addAll(
                                summoner.getLeagueGames()
                                    .withQueue(queue)
                                    .withCount(100)
                                    .withStartTime(threshold)
                                    .withBeginIndex(start)
                                    .get()
                            );
                            try { Thread.sleep(500); } catch (InterruptedException e) {}
                        }
                        for (String matchId : matchIds) {
                            if (LeagueHandler.isMatchDBCached(matchId)) continue;
                            if (!seenMatchIds.add(matchId)) continue;
                            allMatches.add(new MatchEntry(entry, summoner, matchId));
                        }
                        try { Thread.sleep(1000); } catch (InterruptedException e) {}
                        System.out.println(shard + " - " + allMatches.size());
                    } catch (Exception e) { e.printStackTrace(); }
                }
                BotLogger.error("TOTAL: " + shard + " - " + allMatches.size());
                int i = 0;
                for (MatchEntry me : allMatches) {
                    TrackerState.awaitCondition(TrackerState.Priority.LOW);
                    try {
                        LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI()
                            .getMatch(shard.toRegionShard(), me.matchId());
                        if (!match.getGameVersion().startsWith(currentPatch)) continue;
                        i++;
                        BotLogger.info("[LPTracker] [" + i + "/" + allMatches.size() + "] Pushing " + me.entry().getTier() + " match " + shard + " - " + LeagueHandler.getFormattedSummonerName(me.summoner()) + " -> " + me.matchId());
                        analyzeMatchHistory(match).complete();
                        Thread.sleep(350);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            };
            shardTask.queue();
        }
    }

    public static void retriveChallengerEntries() {
        BotLogger.info("[LPTracker] Pushing challenger entries");
        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            for (GameQueueType queue : List.of(GameQueueType.RANKED_SOLO_5X5, GameQueueType.RANKED_FLEX_SR)) {
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("platform", shard);
                    data.put("queue", queue);
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_CHALLENGER, data);
                    Thread.sleep(500);
    
                    List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, queue, TierDivisionType.CHALLENGER_I, 0);
                    BotLogger.info("[LPTracker] Start analyzing " + entries.size() + " challengers for region " + shard);
                    LeagueDB.updateSummonerEntries(entries, shard);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }  
    }

    public static void retriveHighEloEntries() {
        BotLogger.info("[LPTracker] Pushing challenger entries");
        for (TierDivisionType tier : List.of(TierDivisionType.MASTER_I, TierDivisionType.GRANDMASTER_I, TierDivisionType.CHALLENGER_I)) {
            for (LeagueShard shard : LeagueShardUtils.getActives()) {
                TrackerState.awaitCondition(TrackerState.Priority.MID);
                for (GameQueueType queue : List.of(GameQueueType.RANKED_SOLO_5X5, GameQueueType.RANKED_FLEX_SR)) {
                    try {
                        URLEndpoint endpoint = tier == TierDivisionType.CHALLENGER_I ? URLEndpoint.V4_LEAGUE_CHALLENGER : (tier == TierDivisionType.GRANDMASTER_I ? URLEndpoint.V4_LEAGUE_GRANDMASTER : URLEndpoint.V4_LEAGUE_MASTER);
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("platform", shard);
                        data.put("queue", queue);
                        LeagueHandler.clearCache(endpoint, data);
                        Thread.sleep(500);
        
                        List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, queue, tier, 0);
                        LeagueDB.updateSummonerEntries(entries, shard);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }  
        }
    }

    public static void retriveAllEntries() {
        BotLogger.info("[LPTracker] Pushing all entries");
        List<LeagueShard> activeShards = List.of(
            LeagueShard.EUW1,
            LeagueShard.NA1,
            LeagueShard.KR,
            LeagueShard.EUN1
        );
        for (LeagueShard shard : activeShards) {
            ChronoTask task = () -> {
                    for (TierDivisionType tier : TierDivisionType.values()) {
                        int page = 1;
                        if (tier == TierDivisionType.CHALLENGER_I || tier == TierDivisionType.GRANDMASTER_I
                                || tier == TierDivisionType.UNRANKED || tier == TierDivisionType.MASTER_I)
                            continue;
                        if (tier.getDivision() != null && tier.getDivision().equals("V"))
                            continue;
                        try {
                            List<LeagueEntry> entries = new ArrayList<>();
                            do {
                                entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, GameQueueType.RANKED_SOLO_5X5, tier, page);
                                System.out.println("[LPTracker] Start analyzing page " + page + " of " + tier.name() + " for region " + shard + " | Entries: " + entries.size());
                                LeagueDB.updateSummonerEntries(entries, shard);
                                page++;
                                Thread.sleep(500);
                            } while (entries.size() > 0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

            };
            task.queue();
        }
        
    }

    public static void retriveSampleGamesPatch() {
        String currentPatch = LeagueHandler.getVersion().split("\\.")[0] + "." + LeagueHandler.getVersion().split("\\.")[1];
        BotLogger.info("[LPTracker] Pushing sample matches");
        List<LeagueShard> shards = List.of(LeagueShard.EUW1);
        for (LeagueShard shard : shards) {
            try {
                List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, GameQueueType.RANKED_SOLO_5X5, TierDivisionType.CHALLENGER_I, 0);

                BotLogger.info("[LPTracker] Start analyzing " + entries.size() + " matches for region " + shard);

                for (int j = 0; j < entries.size() ; j++) {
                    try {
                        LeagueEntry entry = entries.get(j);
                        Summoner summoner = LeagueService.getSummonerByPuuid(entry.getPuuid(), shard);
                        RiotAccount account = LeagueService.getRiotAccountFromSummoner(summoner);
                        BotLogger.info("[LPTracker] Analyzing summoner " + account.getName() + "#" + account.getTag() + " | " + j + "/" + entries.size());

                        List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
                        if (matchIds.isEmpty()) 
                            continue;

                        int k = 0;
                        LOLMatch match;
                        do {
                            String matchId = matchIds.get(k); 
                            match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
                            
                            BotLogger.info("[LPTracker] Pushing match data for region " + shard + " | " + k + "/5 -  " + j + "/" + entries.size());
                            analyzeMatchHistory(match).complete();
                            Thread.sleep(350);
                            k++;
                        } while (match.getGameVersion().startsWith(currentPatch) && k < matchIds.size());
                    } catch (Exception e) { e.printStackTrace(); }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    /**
     * WIP
     * @param champion
     * @param lane
     */
    public static HashMap<String, String> analyzeChampionData(int champion, LaneType lane) {
        String[] parts = LeagueHandler.getVersion().split("\\.", 3);
        String patch = parts[0] + "." + parts[1];
    
        QueryResult matchDatas = LeagueDB.get().query("SELECT bans FROM `match` WHERE patch_major = '" + patch + "'");
        QueryResult championDatas = LeagueDB.get().query("SELECT win FROM participant p JOIN `match` m ON p.match_id = m.id WHERE m.patch_major = '" + patch + "' AND p.champion = " + champion + " AND p.lane = '" + lane + "'");
    
        int totalGames = matchDatas.size();
        int totalBans = 0;
        int totalPicks = championDatas.size();
        int totalWins = 0;
    
        for (QueryRecord record : matchDatas) {
            JSONObject bansObj = new JSONObject(record.get("bans"));
            for (String key : bansObj.keySet()) {
                JSONArray bans = bansObj.getJSONArray(key);
                for (int i = 0; i < bans.length(); i++) {
                    if (champion == bans.getInt(i)) totalBans++;
                }
            }
        }
    
        for (QueryRecord record : championDatas) {
            if (record.getAsBoolean("win")) totalWins++;
        }
    
        double winrate  = totalPicks > 0 ? (double) totalWins / totalPicks * 100 : 0;
        double pickrate = totalGames > 0 ? (double) totalPicks / totalGames * 100 : 0;
        double banrate  = totalGames > 0 ? (double) totalBans  / totalGames * 100 : 0;
    
        HashMap<String, String> result = new HashMap<>();
        result.put("games",    String.valueOf(totalGames));
        result.put("picks",    String.valueOf(totalPicks));
        result.put("bans",     String.valueOf(totalBans));
        result.put("wins",     String.valueOf(totalWins));
        result.put("losses",   String.valueOf(totalPicks - totalWins));
        result.put("winrate",  String.valueOf(Math.round(winrate  * 100.0) / 100.0));
        result.put("pickrate", String.valueOf(Math.round(pickrate * 100.0) / 100.0));
        result.put("banrate",  String.valueOf(Math.round(banrate  * 100.0) / 100.0));
    
        return result;
    }

    public static void retriveMatchHistory(Summoner summoner) {
        try {
            List<String> matchIds = new ArrayList<>();
            List<String> retrivedMatchIds;
    
            do {
                retrivedMatchIds = summoner.getLeagueGames().withCount(100).withBeginIndex(matchIds.size()).get();
                matchIds.addAll(retrivedMatchIds);
                Thread.sleep(350);
            } while (retrivedMatchIds.size() > 0);
    
            int i = 0;
            for (String matchId : matchIds) {
                try {
                    i++;
                    if (LeagueHandler.isMatchDBCached(matchId)) continue;
                    if (!LeagueHandler.isMatchLocallyCached(matchId, summoner.getPlatform())) {
                        Thread.sleep(350);
                    }
                    LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), matchId);
                    if (match == null) continue;
                    System.out.println("[" + i + "/" + matchIds.size() + "] " + match.getGameId() + " - " + match.getPlatform() + " - " + match.getQueue());
                    Tracker.queueMatch(match);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("[" + i + "/" + matchIds.size() + "] ");
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void retriveMatchHistory(Summoner summoner, GameQueueType queue) {
        try {
            List<String> matchIds = new ArrayList<>();
            List<String> retrivedMatchIds = summoner.getLeagueGames().withCount(100).withQueue(queue).get();
            do {
                matchIds.addAll(retrivedMatchIds);

                try { Thread.sleep(350); }
                catch (InterruptedException e) {e.printStackTrace();}

                int i = 0;
                for (String matchId : retrivedMatchIds) {
                    try {
                        LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), matchId);
                        if (match == null) continue;
                        System.out.println("[" + i + "/" + retrivedMatchIds.size() + "] " + match.getGameId() + " - " + match.getPlatform() + " - " + match.getQueue());
                        Tracker.queueMatch(match);
                        i++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                retrivedMatchIds = summoner.getLeagueGames().withCount(100).withQueue(queue).withBeginIndex(matchIds.size()).get();
            } while (retrivedMatchIds.size() > 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
