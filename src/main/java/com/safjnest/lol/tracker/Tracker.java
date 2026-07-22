package com.safjnest.lol.tracker;

import java.util.ArrayList;
import java.util.Collections;
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
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.TimeConstant;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.KillType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.impl.lol.builders.matchv5.match.MatchListBuilder;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrame;
import no.stelar7.api.r4j.pojo.lol.match.v5.TimelineFrameEvent;
import no.stelar7.api.r4j.pojo.lol.match.v5.TimelineParticipantFrame;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import java.time.LocalDateTime;

public class Tracker {

    private static long period = TimeConstant.MINUTE * 10;
    private static final long timelineSnapshotInterval = TimeConstant.MINUTE * 5;

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
            LeagueDB.updateSummonerEntries(summonerId, entries, summoner.getPlatform());
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

        for (String key : new String[]{"monster_events", "building_events", "champion_kills", "ward_events", "turret_plate_events", "item_events", "skill_events", "level_events", "objective_events", "game_events", "snapshots"}) {
            String raw = matchData.getOrDefault(key, "").trim();
            if (raw.isEmpty() || raw.equals("null")) {
                json.put(key, new JSONArray());
                continue;
            }

            try {
                JSONArray parsed = raw.startsWith("[") ? new JSONArray(raw) : new JSONArray("[" + raw + "]");
                json.put(key, parsed);
            } catch (Exception e) {
                json.put(key, new JSONArray());
            }
        }

        String participants = matchData.getOrDefault("participants", "").trim();
        if (participants.isEmpty()) {
            json.put("participants", new JSONObject());
        } else {
            try {
                json.put("participants", new JSONObject(participants));
            } catch (Exception e) {
                json.put("participants", new JSONObject());
            }
        }

        String dragonSoul = matchData.getOrDefault("dragon_soul", "").trim();
        if (dragonSoul.isEmpty() || dragonSoul.equals("null")) {
            json.put("dragon_soul", JSONObject.NULL);
        } else {
            try {
                json.put("dragon_soul", new JSONObject(dragonSoul));
            } catch (Exception e) {
                json.put("dragon_soul", JSONObject.NULL);
            }
        }

        return json.toString();
    }

    private static JSONObject createTimelineEvent(TimelineFrameEvent event) {
        JSONObject json = new JSONObject();
        json.put("timestamp", event.getTimestamp());
        return json;
    }

    private static JSONArray createAssists(List<Integer> assistingParticipantIds) {
        JSONArray assists = new JSONArray();
        if (assistingParticipantIds == null) return assists;

        for (Integer participantId : assistingParticipantIds) {
            if (participantId != null && participantId != 0) assists.put(participantId);
        }
        return assists;
    }

    private static String getParticipantTeam(List<MatchParticipant> participants, int participantId) {
        for (MatchParticipant participant : participants) {
            if (participant.getParticipantId() == participantId && participant.getTeam() != null) {
                return participant.getTeam().name();
            }
        }
        return null;
    }

    private static void addChampionKill(JSONArray championKills, Map<String, JSONObject> indexedKills, TimelineFrameEvent event, String killerTeam, boolean firstBlood) {
        String key = event.getTimestamp() + "-" + event.getKillerId();
        JSONObject kill = indexedKills.get(key);
        if (kill == null) {
            kill = createTimelineEvent(event);
            kill.put("killer", event.getKillerId());
            indexedKills.put(key, kill);
            championKills.put(kill);
        }

        if (killerTeam != null) kill.put("killer_team", killerTeam);
        if (firstBlood) {
            kill.put("kill_type", "first_blood");
            if (!kill.has("assists")) kill.put("assists", new JSONArray());
            return;
        }

        kill.put("victim", event.getVictimId());
        kill.put("assists", createAssists(event.getAssistingParticipantIds()));
        kill.put("bounty", event.getBounty());
        kill.put("shutdown_bounty", event.getShutdownBounty());

        int multiKillLength = event.getMultiKillLength();
        if (multiKillLength > 1 && !kill.has("kill_type")) {
            kill.put("kill_type", "multi_" + multiKillLength);
        }
    }

    private static JSONObject createSnapshot(TimelineFrame frame, boolean finalSnapshot) {
        JSONObject snapshot = new JSONObject();
        snapshot.put("timestamp", frame.getTimestamp());
        if (finalSnapshot) snapshot.put("final", true);

        JSONObject participants = new JSONObject();
        Map<String, TimelineParticipantFrame> participantFrames = frame.getParticipantFrames();
        if (participantFrames != null) {
            for (Map.Entry<String, TimelineParticipantFrame> entry : participantFrames.entrySet()) {
                TimelineParticipantFrame participantFrame = entry.getValue();
                if (participantFrame == null) continue;

                String participantId = participantFrame.getParticipantId() > 0 ? String.valueOf(participantFrame.getParticipantId()) : entry.getKey();
                JSONObject stats = new JSONObject();
                stats.put("total_gold", participantFrame.getTotalGold());
                stats.put("current_gold", participantFrame.getCurrentGold());
                stats.put("cs", participantFrame.getMinionsKilled() + participantFrame.getJungleMinionsKilled());
                participants.put(participantId, stats);
            }
        }
        snapshot.put("participants", participants);
        return snapshot;
    }

    private static void addSnapshot(JSONArray snapshots, TimelineFrame frame, boolean finalSnapshot) {
        if (frame == null) return;

        for (int i = 0; i < snapshots.length(); i++) {
            JSONObject existing = snapshots.getJSONObject(i);
            if (existing.getLong("timestamp") == frame.getTimestamp()) {
                if (finalSnapshot) existing.put("final", true);
                return;
            }
        }
        snapshots.put(createSnapshot(frame, finalSnapshot));
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

        JSONArray monsterEvents = new JSONArray();
        JSONArray buildingEvents = new JSONArray();
        JSONArray championKills = new JSONArray();
        JSONArray wardEvents = new JSONArray();
        JSONArray turretPlateEvents = new JSONArray();
        JSONArray itemEvents = new JSONArray();
        JSONArray skillEvents = new JSONArray();
        JSONArray levelEvents = new JSONArray();
        JSONArray objectiveEvents = new JSONArray();
        JSONArray gameEvents = new JSONArray();
        JSONArray snapshots = new JSONArray();
        Map<String, JSONObject> indexedChampionKills = new HashMap<>();
        JSONObject dragonSoul = null;
        long dragonSoulTimestamp = -1;
        long gameDuration = match.getGameDuration() == null ? Long.MAX_VALUE : match.getGameDuration().longValue() * 1000L;
        long nextSnapshotTimestamp = timelineSnapshotInterval;
        TimelineFrame previousFrame = null;
        TimelineFrame finalFrame = null;

        for (int i = 0; i < timeline.getFrames().size(); i++) {
            TimelineFrame frame = timeline.getFrames().get(i);
            for (TimelineFrameEvent event : frame.getEvents()) {
                if (event == null || event.getType() == null) continue;

                Item item;
                String participantId = String.valueOf(event.getParticipantId());
                String itemType = i == 1 ? "starter" : "items";

                try {
                    switch (event.getType()) {
                        case ITEM_PURCHASED:
                        case ITEM_SOLD:
                        case ITEM_UNDO:
                        case ITEM_DESTROYED: {
                            JSONObject itemEvent = createTimelineEvent(event);
                            itemEvent.put("event", event.getType().name());
                            itemEvent.put("participant", event.getParticipantId());
                            itemEvent.put("item", event.getItemId());
                            itemEvent.put("before", event.getBeforeId());
                            itemEvent.put("after", event.getAfterId());
                            itemEvent.put("gold_gain", event.getGoldGain());
                            itemEvents.put(itemEvent);
                            if (event.getType().name().equals("ITEM_DESTROYED")) break;

                            int itemId = event.getType().name().equals("ITEM_PURCHASED") ? event.getItemId() : event.getBeforeId();
                            item = items.get(itemId);
                            if (item == null) break;

                            if (event.getType().name().equals("ITEM_PURCHASED")) {
                                if (ItemUtils.isBoots(item)) {
                                    if (matchData.get(participantId) != null) matchData.get(participantId).put("boots", item.getId() + "");
                                    break;
                                }

                                if (i != 1 && item.getDepth() != 3) break;

                                List<String> itemList = matchItemData.computeIfAbsent(participantId + "-" + itemType, k -> new ArrayList<>());
                                itemList.add(item.getId() + "");
                                if (matchData.get(participantId) != null) matchData.get(participantId).put(itemType, String.join(",", itemList));
                            } else {
                                if (i != 1 && item.getDepth() != 3) break;

                                List<String> currentList = matchItemData.get(participantId + "-" + itemType);
                                if (currentList != null) {
                                    currentList.remove(item.getId() + "");
                                    if (matchData.get(participantId) != null) matchData.get(participantId).put(itemType, String.join(",", currentList));
                                }
                            }
                            break;
                        }
                        case SKILL_LEVEL_UP: {
                            JSONObject skillEvent = createTimelineEvent(event);
                            skillEvent.put("participant", event.getParticipantId());
                            skillEvent.put("skill_slot", event.getSkillSlot());
                            skillEvents.put(skillEvent);

                            if (matchData.get(participantId) == null) break;
                            String skillList = matchData.get(participantId).getOrDefault("skill_order", "");
                            if (skillList.isEmpty()) skillList = event.getSkillSlot() + "";
                            else skillList += "," + event.getSkillSlot();
                            matchData.get(participantId).put("skill_order", skillList);
                            break;
                        }
                        case LEVEL_UP: {
                            JSONObject levelEvent = createTimelineEvent(event);
                            levelEvent.put("participant", event.getParticipantId());
                            levelEvent.put("level", event.getLevel());
                            levelEvents.put(levelEvent);
                            break;
                        }
                        case CHAMPION_KILL: {
                            String killerTeam = event.getKillerTeamId() != null ? event.getKillerTeamId().name() : getParticipantTeam(partecipants, event.getKillerId());
                            addChampionKill(championKills, indexedChampionKills, event, killerTeam, false);
                            break;
                        }
                        case CHAMPION_SPECIAL_KILL: {
                            if (event.getKillType() != KillType.KILL_FIRST_BLOOD) break;
                            String killerTeam = event.getKillerTeamId() != null ? event.getKillerTeamId().name() : getParticipantTeam(partecipants, event.getKillerId());
                            addChampionKill(championKills, indexedChampionKills, event, killerTeam, true);
                            break;
                        }
                        case ELITE_MONSTER_KILL: {
                            if (event.getMonsterType() == null) break;

                            String monster = event.getMonsterType().name();
                            String subType = event.getMonsterSubType() != null ? event.getMonsterSubType().name() : "";
                            String killerTeam = event.getKillerTeamId() != null ? event.getKillerTeamId().name() : getParticipantTeam(partecipants, event.getKillerId());

                            JSONObject monsterEvent = createTimelineEvent(event);
                            monsterEvent.put("subtype", subType);
                            monsterEvent.put("assists", createAssists(event.getAssistingParticipantIds()));
                            monsterEvent.put("killer", event.getKillerId());
                            monsterEvent.put("monster", monster);
                            if (killerTeam != null) monsterEvent.put("killer_team", killerTeam);
                            monsterEvents.put(monsterEvent);

                            if (monster.equals("DRAGON") && !subType.isEmpty() && !subType.equals("UNKNOWN") && !subType.equals("ELDER_DRAGON") && event.getTimestamp() >= dragonSoulTimestamp) {
                                dragonSoulTimestamp = event.getTimestamp();
                                dragonSoul = createTimelineEvent(event);
                                dragonSoul.put("subtype", subType);
                                dragonSoul.put("team", killerTeam == null ? JSONObject.NULL : killerTeam);
                                dragonSoul.put("source", "last_non_elder_dragon");
                            }
                            break;
                        }
                        case BUILDING_KILL: {
                            JSONObject buildingEvent = createTimelineEvent(event);
                            buildingEvent.put("assists", createAssists(event.getAssistingParticipantIds()));
                            buildingEvent.put("killer", event.getKillerId());
                            buildingEvent.put("building", event.getBuildingType() != null ? event.getBuildingType().name() : "");
                            if (event.getTowerType() != null) buildingEvent.put("tower", event.getTowerType().name());
                            if (event.getLaneType() != null) buildingEvent.put("lane", event.getLaneType().name());
                            if (event.getTeamId() != null) buildingEvent.put("team", event.getTeamId().name());
                            buildingEvents.put(buildingEvent);
                            break;
                        }
                        case WARD_PLACED:
                        case WARD_KILL: {
                            JSONObject wardEvent = createTimelineEvent(event);
                            wardEvent.put("event", event.getType().name());
                            wardEvent.put("participant", event.getParticipantId());
                            wardEvent.put("creator", event.getCreatorId());
                            if (event.getWardType() != null) wardEvent.put("ward", event.getWardType().name());
                            wardEvents.put(wardEvent);
                            break;
                        }
                        case TURRET_PLATE_DESTROYED: {
                            JSONObject turretPlateEvent = createTimelineEvent(event);
                            turretPlateEvent.put("killer", event.getKillerId());
                            if (event.getTeamId() != null) turretPlateEvent.put("team", event.getTeamId().name());
                            if (event.getLaneType() != null) turretPlateEvent.put("lane", event.getLaneType().name());
                            turretPlateEvents.put(turretPlateEvent);
                            break;
                        }
                        case DRAGON_SOUL_GIVEN:
                        case OBJECTIVE_BOUNTY_PRESTART:
                        case OBJECTIVE_BOUNTY_FINISH: {
                            JSONObject objectiveEvent = createTimelineEvent(event);
                            objectiveEvent.put("type", event.getType().name());
                            if (event.getTeamId() != null) objectiveEvent.put("team", event.getTeamId().name());
                            objectiveEvents.put(objectiveEvent);
                            break;
                        }
                        case ASCENDED_EVENT:
                        case CAPTURE_POINT:
                        case PORO_KING_SUMMON:
                        case PAUSE_START:
                        case PAUSE_END:
                        case GAME_END:
                        case CHAMPION_TRANSFORM:
                        case FEAT_UPDATE: {
                            JSONObject gameEvent = createTimelineEvent(event);
                            gameEvent.put("type", event.getType().name());
                            if (event.getParticipantId() != 0) gameEvent.put("participant", event.getParticipantId());
                            if (event.getTeamId() != null) gameEvent.put("team", event.getTeamId().name());
                            if (event.getWinningTeam() != null) gameEvent.put("winning_team", event.getWinningTeam().name());
                            if (event.getTransformType() != null) gameEvent.put("transform_type", event.getTransformType().name());
                            gameEvents.put(gameEvent);
                            break;
                        }
                        default:
                            break;
                    }
                } catch (Exception e) {
                }
            }

            long frameTimestamp = frame.getTimestamp();
            while (frameTimestamp >= nextSnapshotTimestamp && nextSnapshotTimestamp <= gameDuration) {
                TimelineFrame snapshotFrame = frameTimestamp == nextSnapshotTimestamp ? frame : previousFrame;
                addSnapshot(snapshots, snapshotFrame, false);
                nextSnapshotTimestamp += timelineSnapshotInterval;
            }
            if (frameTimestamp <= gameDuration) finalFrame = frame;
            previousFrame = frame;
        }

        addSnapshot(snapshots, finalFrame, true);

        matchData.get("match").put("monster_events", monsterEvents.toString());
        matchData.get("match").put("building_events", buildingEvents.toString());
        matchData.get("match").put("champion_kills", championKills.toString());
        matchData.get("match").put("ward_events", wardEvents.toString());
        matchData.get("match").put("turret_plate_events", turretPlateEvents.toString());
        matchData.get("match").put("item_events", itemEvents.toString());
        matchData.get("match").put("skill_events", skillEvents.toString());
        matchData.get("match").put("level_events", levelEvents.toString());
        matchData.get("match").put("objective_events", objectiveEvents.toString());
        matchData.get("match").put("game_events", gameEvents.toString());
        matchData.get("match").put("snapshots", snapshots.toString());
        matchData.get("match").put("dragon_soul", dragonSoul == null ? "null" : dragonSoul.toString());

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
        String currentPatch = PatchUtils.getPatch();
        String previousPatch = PatchUtils.getPreviousPatch();
    
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
                            MatchListBuilder builder = summoner.getLeagueGames()
                                .withCount(100)
                                .withStartTime(threshold)
                                .withBeginIndex(start);
                            if (queue == GameQueueType.CHERRY) {
                                builder.withType(MatchlistMatchType.NORMAL);
                            } else {
                                builder.withQueue(queue);
                            }
                            matchIds.addAll(builder.get());
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
                        LOLMatch match = LeagueService.getMatch(me.matchId(), me.summoner().getPlatform());
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
        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            ChronoTask task = () -> {
                    List<TierDivisionType> tiers = new ArrayList<>(List.of(TierDivisionType.values()));
                    Collections.reverse(tiers);
                    tiers.remove(0);
                    for (TierDivisionType tier : tiers) {
                        int page = 1;
                        if (tier == TierDivisionType.CHALLENGER_I || tier == TierDivisionType.GRANDMASTER_I
                                || tier == TierDivisionType.UNRANKED || tier == TierDivisionType.MASTER_I)
                            continue;
                        if (tier.getDivision() != null && tier.getDivision().equals("V"))
                            continue;
                        try {
                            List<LeagueEntry> entries = new ArrayList<>();
                            do {
                                TrackerState.awaitCondition(TrackerState.Priority.LOW);
                                entries = LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueByTierDivision(shard, GameQueueType.RANKED_SOLO_5X5, tier, page);
                                BotLogger.info("[LPTracker] Start analyzing page " + page + " of " + tier.name() + " for region " + shard + " | Entries: " + entries.size());
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
        String currentPatch = PatchUtils.getPatch();
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
        String[] parts = PatchUtils.getPatch().split("\\.", 3);
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
