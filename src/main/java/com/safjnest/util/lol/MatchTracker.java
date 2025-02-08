package com.safjnest.util.lol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.App;
import com.safjnest.core.Chronos;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrameEvent;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import java.time.LocalDateTime;

public class MatchTracker {

    private static long period = TimeConstant.MINUTE * 10;

    private static List<GameQueueType> toTrack = List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.CHERRY);
    private static Set<LOLMatch> matchQueue = ConcurrentHashMap.newKeySet();

    public static int UNKNOWN_RANK = TierDivisionType.UNRANKED.ordinal() + 1;

	static {
        if(!App.TEST_MODE) {
            ChronoTask track = () -> retriveSummoners();
            track.scheduleAtFixedRate(TimeConstant.MINUTE * 1, period, TimeUnit.MILLISECONDS);

            ChronoTask trackQueuedGames = () -> popSet();
            trackQueuedGames.scheduleAtFixedTime(0, 0, 0);
            //trackQueuedGames.scheduleAtFixedRate(TimeConstant.MINUTE * 1, TimeConstant.MINUTE * 1, TimeUnit.MILLISECONDS);
        }
	}


    private static void retriveSummoners() {
        try {
            if (App.TEST_MODE) return;

            QueryCollection result = DatabaseHandler.getRegistredLolAccount(LeagueHandler.getCurrentSplitRange()[0]);
            BotLogger.info("[LPTracker] Start tracking summoners (" + result.size() + " accounts)");

            for (QueryRecord account : result) {
                System.out.println(account.get("account_id"));
                Summoner summoner = LeagueHandler.getSummonerByAccountId(account.get("account_id"), LeagueShard.values()[Integer.valueOf(account.get("league_shard"))]);
                if (summoner == null) continue;
                
                LeagueHandler.clearCache(URLEndpoint.V5_MATCHLIST, summoner);
                LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY, summoner);
        
                try { Thread.sleep(350); }
                catch (InterruptedException e) {e.printStackTrace();}
        
                List<String> matchIds = summoner.getLeagueGames().get();
                if (matchIds.isEmpty()) continue;
        
                String matchId = matchIds.get(0);
                LeagueShard shard = summoner.getPlatform();
                try {
                    shard = LeagueShard.valueOf(matchId.split("_")[0]);
                } catch (Exception e) { }

                if (Long.parseLong(matchId.split("_")[1]) == account.getAsLong("game_id")) continue;
                else if (shard != summoner.getPlatform()) {
                    analyzeMatchHistory(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LeagueHandler.getSummonerByPuiid(summoner.getPUUID(), shard)).complete();
                    continue;
                }

                LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
                if (match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) continue;
                ChronoTask task = analyzeMatchHistory(match, summoner, account);
                if (task != null) task.complete();
                
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
    public static void popSet() {
        Set<LOLMatch> toAnalyze = null;
        
        synchronized (matchQueue) {
            if (matchQueue.isEmpty()) return;

            toAnalyze = new HashSet<>(matchQueue);
            matchQueue.clear();
        }
        
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
        matchQueue.add(match);
    }

    public synchronized static Set<LOLMatch> getMatchQueueCopy() {
        return new HashSet<>(matchQueue);
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

        QueryRecord row = DatabaseHandler.getRegistredLolAccount(summoner.getAccountId(), LeagueHandler.getCurrentSplitRange()[0]);
        //if (row.emptyValues() && queue == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return Chronos.NULL;

        try { Thread.sleep(350); }
        catch (InterruptedException e) {e.printStackTrace();}

        List<String> matchIds = summoner.getLeagueGames().get();
        if (matchIds.isEmpty()) return Chronos.NULL;

        String matchId = matchIds.get(0);
        LeagueShard shard = summoner.getPlatform();
        try {
            shard = LeagueShard.valueOf(matchId.split("_")[0]);
        } catch (Exception e) { }

        if (Long.parseLong(matchId.split("_")[1]) == row.getAsLong("game_id")) return Chronos.NULL;
        else if (shard != summoner.getPlatform()) {
            return analyzeMatchHistory(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LeagueHandler.getSummonerByPuiid(summoner.getPUUID(), shard));
        }

        LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), matchId);
        if (match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) return Chronos.NULL;
        return analyzeMatchHistory(match, summoner, row);
    }

    public static ChronoTask analyzeMatchHistory(LOLMatch match, Summoner summoner, QueryRecord dataGame) {
        ChronoTask task = () -> {
            if (!LeagueHandler.isCurrentSplit(match.getGameStartTimestamp()) && match.getQueue() == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return;

            int summoner_match_id = DatabaseHandler.setMatchData(match);
            LeagueHandler.updateSummonerDB(match);

            HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(match, match.getParticipants());


            for (MatchParticipant partecipant : match.getParticipants()) {
                if (partecipant.getPuuid().equals(summoner.getPUUID())) {
                    pushSummoner(match, summoner_match_id, summoner, partecipant, dataGame, matchData.get(partecipant.getPuuid())).complete();
                    continue;
                }

                Summoner toPush = LeagueHandler.getSummonerByPuiid(partecipant.getPuuid(), match.getPlatform());
                if (toPush == null) continue;

                try { 
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY, toPush);
                    Thread.sleep(500); 
                }
                catch (InterruptedException e) {e.printStackTrace();}
                pushSummoner(match, summoner_match_id, toPush, partecipant, matchData.get(partecipant.getPuuid())).complete();
            }
            BotLogger.info("[LPTracker] Pushed match data for " + LeagueHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");
        };
        return task;
    }

    public static ChronoTask analyzeMatchHistory(LOLMatch match) {
        return () -> {
            int summoner_match_id = DatabaseHandler.setMatchData(match, true);
            if (summoner_match_id == 0) {
                BotLogger.info("[LPTracker] Match " + match.getGameId() + " already tracked");
                return;
            }
            LeagueHandler.updateSummonerDB(match);

            HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(match, match.getParticipants());

            for (MatchParticipant partecipant : match.getParticipants()) {
                try { Thread.sleep(2000); }
                catch (InterruptedException e) {e.printStackTrace();}

                Summoner summoner = LeagueHandler.getSummonerByPuiid(partecipant.getPuuid(), match.getPlatform());
                if (summoner == null) continue;

                pushSummoner(match, summoner_match_id, summoner, partecipant, matchData.get(partecipant.getPuuid())).complete();
            }
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

    public static ChronoTask pushSummoner(LOLMatch match, int summonerMatch, Summoner summoner, MatchParticipant partecipant, HashMap<String, String> matchData) {
        QueryRecord row = DatabaseHandler.getRegistredLolAccount(summoner.getAccountId(), LeagueHandler.getCurrentSplitRange()[0]);
        return pushSummoner(match, summonerMatch, summoner, partecipant, row, matchData);
    }

    private static ChronoTask pushSummoner(LOLMatch match, int summonerMatch, Summoner summoner, MatchParticipant participant, QueryRecord dataGame, HashMap<String, String> matchData) {
        return () -> {
            boolean win = participant.didWin();
            int champion = participant.getChampionId();
            String kda = participant.getKills() + "/" + participant.getDeaths() + "/" + participant.getAssists();
            LaneType lane = participant.getChampionSelectLane() != null ? participant.getChampionSelectLane() : participant.getLane();
            TeamType side = participant.getTeam();

            if (match.getGameId() == dataGame.getAsLong("game_id")) return;

            LeagueEntry league = summoner.getLeagueEntry().stream().filter(l -> l.getQueueType().commonName().equals("5v5 Ranked Solo")).findFirst().orElse(null);

            TierDivisionType oldDivision = dataGame.getAsInt("rank") != UNKNOWN_RANK ? TierDivisionType.values()[dataGame.getAsInt("rank")] : null;
            TierDivisionType division = league != null ? league.getTierDivisionType() : TierDivisionType.UNRANKED;

            int rank = division.ordinal();
            int lp = league != null ? league.getLeaguePoints() : 0;
            int gain = 0;

            boolean isPromotionToMaster = oldDivision == TierDivisionType.DIAMOND_I && division == TierDivisionType.MASTER_I;
            boolean isMasterPlus = division == TierDivisionType.MASTER_I || division == TierDivisionType.GRANDMASTER_I || division == TierDivisionType.CHALLENGER_I;

            if (dataGame.get("rank") == null || match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) gain = 0;
            else if ((isPromotionToMaster || !isMasterPlus) && rank != dataGame.getAsInt("rank")) {
                gain = 100 - (Math.abs(lp - dataGame.getAsInt("lp")));
                gain = rank < dataGame.getAsInt("rank") ? gain : -gain;
            } else {
                gain = lp - dataGame.getAsInt("lp");
            }

            DatabaseHandler.setSummonerData(summoner.getAccountId(), summonerMatch, win, kda, rank, lp, gain, champion, lane, side, createJSONBuild(matchData));
        };
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

        if (matchData.containsKey("augments"))
            json.put("augments", matchData.get("augments").split(","));

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
            matchData.get(partecipant.getPuuid()).put("summoner_spells", partecipant.getSummoner1Id() + "," + partecipant.getSummoner2Id());

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

        for (int i = 0; i < timeline.getFrames().size(); i++) {
            for (TimelineFrameEvent event : timeline.getFrames().get(i).getEvents()) {
                Item item;
                String participantId = String.valueOf(event.getParticipantId());
                String itemType = i == 1 ? "starter" : "items";

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
                    default:
                        break;
                }
            }
        }

        timeline.getParticipants().forEach(partecipant -> {
            matchData.put(partecipant.getPuuid(), matchData.get(String.valueOf(partecipant.getParticipantId())));
            matchData.remove(String.valueOf(partecipant.getParticipantId()));

        });
        return matchData;
    }

    private static Item isSuppItemFromId(int itemId) {
        if (itemId == 0) return null;
        Item item = LeagueHandler.getRiotApi().getDDragonAPI().getItems().get(itemId);
        if (item == null) return null;//old item or removed one? not sure
        if (item.getFrom() == null) return null;
        return item.getFrom().contains("3867") ? item : null;
    }



    public static ChronoTask retriveOldGames(Summoner summoner) {
        return () -> {
            int page = 0;
            int idx = 0;
            List<String> ids;
            do {
                ids = summoner.getLeagueGames().withBeginIndex(page).withCount(100).withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).withStartTime(1727265600L).get();
                QueryCollection result = DatabaseHandler.getSummonerData(summoner.getAccountId());
                for (String matchId : ids) {
                    idx++;
                    if (result.arrayColumn("game_id").contains(matchId.split("_")[1]))
                        continue;

                    LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), matchId);

                    int summonerMatch = DatabaseHandler.setMatchData(match);
                    HashMap<String, String> matchData = analyzeMatchBuild(match, match.getParticipants()).get(summoner.getPUUID());

                    MatchParticipant partecipant = null;
                    for (MatchParticipant p : match.getParticipants()) {
                        if (p.getPuuid().equals(summoner.getPUUID()))
                            partecipant = p;
                    }


                    boolean win = partecipant.didWin();
                    int champion = partecipant.getChampionId();
                    String kda = partecipant.getKills() + "/" + partecipant.getDeaths() + "/" + partecipant.getAssists();
                    LaneType lane = partecipant.getChampionSelectLane() != null ? partecipant.getChampionSelectLane() : partecipant.getLane();
                    TeamType side = partecipant.getTeam();

                    int lp = 0;
                    int gain = 0;

                    DatabaseHandler.setSummonerData(summoner.getAccountId(), summonerMatch, win, kda, UNKNOWN_RANK, lp, gain, champion, lane, side, createJSONBuild(matchData));
                    BotLogger.info("[LPTracker] Pushed old match ( " + idx + " ) data for " + LeagueHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");

                    try {
                        Thread.sleep(1500);
                    } catch (Exception e) {}
                }
                page += 100;
            } while (ids.size() == 100);
        };
    }

    public static ChronoTask retriveSampleGames(int page) {
        //refactor this function
        return new ChronoTask() {
            @Override
            public void run() {
                BotLogger.info("[LPTracker] Pushing sample matches");
                List<LeagueShard> shards = List.of(LeagueShard.KR, LeagueShard.EUW1, LeagueShard.NA1);
                for (LeagueShard shard : shards) {
                    for (int i =  TierDivisionType.DIAMOND_IV.ordinal(); i <= TierDivisionType.SILVER_IV.ordinal(); i++) {
                        TierDivisionType rank = TierDivisionType.values()[i];
                        if (rank.getDivision().equals("V")) continue;

                        List<LeagueEntry> entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, GameQueueType.RANKED_SOLO_5X5, rank, page);
                        for (int j = 0; j < 20; j++) {
                            LeagueEntry entry = entries.get(j);
                            Summoner summoner = LeagueHandler.getSummonerBySummonerId(entry.getSummonerId(), shard);

                            BotLogger.info("[LPTracker] Pushed match data for region " + shard + " and rank " + rank);
                            analyzeMatchHistory(GameQueueType.TEAM_BUILDER_RANKED_SOLO, summoner).complete();
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * WIP
     * @param champion
     * @param lane
     */
    public static HashMap<String, String> analyzeChampionData(int champion, LaneType lane) {
        QueryCollection matchDatas = DatabaseHandler.safJQuery("SELECT * FROM summoner_match");
        QueryCollection championDatas = DatabaseHandler.safJQuery("SELECT * FROM summoner_tracking WHERE champion = " + champion + " AND lane = " + lane.ordinal());

        HashMap<String, String> result = new HashMap<>();

        int totalGames = matchDatas.size();

        int totalBans = 0;
        int totalPicks = 0;

        int totalWins = 0;
        int totalLosses = 0;

        for (QueryRecord record : matchDatas) {
            JSONObject ban = new JSONObject(record.get("bans"));
            JSONArray bans;
            if (ban.has("1")) {
                bans = (JSONArray) ban.get("1");
                for (int i = 0; i < bans.length(); i++) {
                    if (champion == bans.getInt(i)) totalBans++;
                }
            }
            if (ban.has("2")) {
                bans = (JSONArray) ban.get("2");
                for (int i = 0; i < bans.length(); i++) {
                    if (champion == bans.getInt(i)) totalBans++;
                }
            }
        }
        for (QueryRecord record : championDatas) {
            totalPicks++;
            if (record.getAsBoolean("win")) totalWins++;
            else totalLosses++;
        }

        double winrate = (double) totalWins / totalPicks * 100;
        double banrate = (double) totalBans / totalGames * 100;
        double pickrate = (double) totalPicks / totalGames * 100;

        result.put("games", String.valueOf(totalGames));
        result.put("bans", String.valueOf(totalBans));
        result.put("picks", String.valueOf(totalPicks));
        result.put("wins", String.valueOf(totalWins));
        result.put("losses", String.valueOf(totalLosses));
        result.put("winrate", String.valueOf(Math.round(winrate * 100.0) / 100.0));
        result.put("banrate", String.valueOf(Math.round(banrate * 100.0) / 100.0));
        result.put("pickrate", String.valueOf(Math.round(pickrate * 100.0) / 100.0));
        
        return result;
    }

}
