package com.safjnest.lol.tracker;

import com.safjnest.mongo.MongoDB;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.core.Chronos;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.service.ChampionDataRefreshService;
import com.safjnest.lol.service.LeaderboardService;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.service.ProfileStatisticsService;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.ItemUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.ParticipantBuildCodec;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.sql.QueryRecord;
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

    private static final ExecutorService API_REFRESH_EXECUTOR = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("lol-api-refresh-", 0).factory()
    );
    private static final Set<String> PROFILE_STATISTICS_RUNNING = ConcurrentHashMap.newKeySet();
    private static final ProfileStatisticsService PROFILE_STATISTICS_SERVICE = new ProfileStatisticsService();
    private static final Set<String> CHAMPION_DATA_RUNNING = ConcurrentHashMap.newKeySet();
    private static final ChampionDataRefreshService CHAMPION_DATA_REFRESH_SERVICE = new ChampionDataRefreshService();
    private static final int MATCH_LOOKUP_BATCH_SIZE = 5;
    private static final int MATCH_LOOKUP_MAX_RETRIES = 3;
    private static final int MATCH_LOOKUP_NOT_FOUND_TTL = 60 * 5;
    private static final Queue<MatchLookupRequest> MATCH_LOOKUP_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<String> MATCH_LOOKUP_PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> MATCH_LOOKUP_RETRIES = new ConcurrentHashMap<>();

    private static long period = TimeConstant.MINUTE * 10;
    private static final long timelineSnapshotInterval = TimeConstant.MINUTE * 5;

    private static List<GameQueueType> toTrack = List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.CHERRY);


    static void retrieveSummoners() {
        try {
            List<QueryRecord> result = MongoDB.getRegisteredLolAccounts(SeasonUtils.getCurrentSplitRange()[0]);
            BotLogger.info("[LPTracker] Start tracking summoners (" + result.size() + " accounts)");
            for (QueryRecord account : result) {
                Summoner summoner = null;
                try {
                    summoner = LeagueService.getRiotSummoner(account.get("puuid"), account.getAsLeagueShard("region"));
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
                        analyzeMatchHistory(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LeagueService.getRiotSummoner(summoner.getPUUID(), shard)).complete();
                        continue;
                    }

                    LOLMatch match = LeagueService.getR4JMatch(matchId, shard);
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
        RedisClient.sadd(RedisKey.TRACKER_PENDING_MATCH_LIST.of(), LeagueService.putR4JMatch(match));
    }

    public static void enqueueMatchLookup(LeagueShard shard, String gameId) {
        if (shard == null || gameId == null || gameId.isBlank()) return;

        String key = shard.name() + ":" + gameId;
        if (MATCH_LOOKUP_PENDING.add(key)) MATCH_LOOKUP_QUEUE.offer(new MatchLookupRequest(key, shard, gameId));
    }

    public static synchronized int processMatchLookups() {
        int batchSize = Math.min(MATCH_LOOKUP_BATCH_SIZE, MATCH_LOOKUP_QUEUE.size());
        int processed = 0;
        for (int i = 0; i < batchSize; i++) {
            MatchLookupRequest request = MATCH_LOOKUP_QUEUE.poll();
            if (request == null) break;

            try {
                String riotGameId = request.shard().name() + "_" + request.gameId();
                LOLMatch match = LeagueService.getR4JMatch(riotGameId, request.shard());
                if (match == null) {
                    retryMatchLookup(request);
                } else {
                    queueMatch(match);
                    completeMatchLookup(request);
                }
            } catch (Exception exception) {
                BotLogger.error("Match lookup failed for game=" + request.key()
                    + " message=" + exception.getMessage());
                retryMatchLookup(request);
            }
            processed++;
        }
        return processed;
    }
    
    public static Set<LOLMatch> popQueue() {
        Set<String> ids = RedisClient.smembers(RedisKey.TRACKER_PENDING_MATCH_LIST.of());
        Set<LOLMatch> matches = new HashSet<>();
        for (String id : ids) {
            if (id == null || !id.contains("_")) continue;
            try {
                String[] parts = id.split("_", 2);
                LOLMatch match = LeagueService.getR4JMatch(id, LeagueShard.valueOf(parts[0]));
                if (match != null) matches.add(match);
            } catch (RuntimeException ignored) { }
        }
        return matches;
    }
    
    public static Set<LOLMatch> copyQueue() {
        return popQueue();
    }

    public static void startProfileStatistics(
        com.safjnest.lol.model.summoner.Summoner summoner,
        SeasonUtils.SeasonRange season
    ) {
        if (summoner == null || summoner.summonerId() == 0 || season == null) return;

        ProfileStatisticsRequest request = new ProfileStatisticsRequest(summoner, season);
        String key = request.summoner().summonerId() + ":" + request.season().start();
        if (!PROFILE_STATISTICS_RUNNING.add(key)) return;

        try {
            API_REFRESH_EXECUTOR.submit(() -> refreshProfileStatistics(request, key));
        } catch (RuntimeException exception) {
            PROFILE_STATISTICS_RUNNING.remove(key);
            BotLogger.error("Profile statistics async start failed for summoner="
                + request.summoner().summonerId() + " message=" + exception.getMessage());
        }
    }

    private record ProfileStatisticsRequest(
        com.safjnest.lol.model.summoner.Summoner summoner,
        SeasonUtils.SeasonRange season
    ) {}

    public static void startChampionData(Filter filter) {
        if (filter == null || filter.champion() == 0) return;

        String key = filter.toKey();
        if (!CHAMPION_DATA_RUNNING.add(key)) return;

        try {
            API_REFRESH_EXECUTOR.submit(() -> refreshChampionData(filter, key));
        } catch (RuntimeException exception) {
            CHAMPION_DATA_RUNNING.remove(key);
            BotLogger.error("Champion data async start failed for filter=" + key
                + " message=" + exception.getMessage());
        }
    }

    private static void refreshProfileStatistics(ProfileStatisticsRequest request, String key) {
        try {
            LeagueShard shard = LeagueShard.valueOf(request.summoner().region());
            if (!PROFILE_STATISTICS_SERVICE.refresh(request.summoner().puuid(), shard, request.season(), false)) {
                BotLogger.error("Profile statistics refresh failed for summoner=" + request.summoner().summonerId());
                return;
            }

            LeagueService.invalidateProfilePage(request.summoner().puuid(), shard);
            BotLogger.info("[LPTracker] Updated summoner overview for "
                + request.summoner().riotId() + " (" + shard + ", id="
                + request.summoner().summonerId() + ") | profile statistics persisted, Redis profile page invalidated");
        } catch (Exception exception) {
            BotLogger.error("Profile statistics refresh failed for summoner=" + request.summoner().summonerId()
                + " message=" + exception.getMessage());
        } finally {
            PROFILE_STATISTICS_RUNNING.remove(key);
        }
    }

    private static void refreshChampionData(Filter filter, String key) {
        try {
            if (!CHAMPION_DATA_REFRESH_SERVICE.refresh(filter)) {
                BotLogger.error("Champion data refresh failed for filter=" + key);
            }
        } catch (Exception exception) {
            BotLogger.error("Champion data refresh failed for filter=" + key
                + " message=" + exception.getMessage());
        } finally {
            CHAMPION_DATA_RUNNING.remove(key);
        }
    }

    private static void retryMatchLookup(MatchLookupRequest request) {
        int retries = MATCH_LOOKUP_RETRIES.merge(request.key(), 1, Integer::sum);
        if (retries < MATCH_LOOKUP_MAX_RETRIES) {
            MATCH_LOOKUP_QUEUE.offer(request);
            return;
        }

        RedisClient.set(
            RedisKey.MATCH_NOT_FOUND.of(request.shard().name(), request.gameId()),
            "1",
            MATCH_LOOKUP_NOT_FOUND_TTL
        );
        completeMatchLookup(request);
    }

    private static void completeMatchLookup(MatchLookupRequest request) {
        MATCH_LOOKUP_PENDING.remove(request.key());
        MATCH_LOOKUP_RETRIES.remove(request.key());
    }

    private record MatchLookupRequest(String key, LeagueShard shard, String gameId) {}

    public static Summoner checkSummoner(MatchParticipant participant, Summoner summoner) {
        if (participant == null || participant.getPuuid() == null || summoner == null || summoner.getPUUID() == null)
            return null;
        if (summoner.getPUUID().equals(participant.getPuuid()))
            return summoner;
        
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("platform", summoner.getPlatform());
        data.put("puuid", participant.getPuuid());
        LeagueHandler.clearCache(URLEndpoint.V4_SUMMONER_BY_PUUID, data);

        summoner = LeagueService.getRiotSummoner(participant.getPuuid(), summoner.getPlatform());
        if (summoner != null && participant.getPuuid().equals(summoner.getPUUID()))
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
        if (queue == null || summoner == null || toTrack.indexOf(queue) == -1) return Chronos.NULL;

        LeagueService.upsertSummoner(summoner, null);
        QueryRecord row = MongoDB.getRegisteredLolAccount(summoner.getPUUID(), SeasonUtils.getCurrentSplitRange()[0]);
        //if (row.emptyValues() && queue == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return Chronos.NULL;

        try { Thread.sleep(350); }
        catch (InterruptedException e) {e.printStackTrace();}

        List<String> matchIds = summoner.getLeagueGames().withQueue(queue).get();
        if (matchIds.isEmpty()) return Chronos.NULL;

        String matchId = matchIds.get(0);
        LeagueShard shard = summoner.getPlatform();
        try {
            shard = LeagueShard.valueOf(matchId.split("_")[0]);
        } catch (Exception e) { }

        if (row != null && row.get("game_id") != null && Long.parseLong(matchId.split("_")[1]) == row.getAsLong("game_id")) return Chronos.NULL;
        else if (shard != summoner.getPlatform()) {
            return analyzeMatchHistory(queue, LeagueService.getRiotSummoner(summoner.getPUUID(), shard));
        }

        LOLMatch match = LeagueService.getR4JMatch(matchId, shard);
        if (match == null || match.getQueue() != queue) return Chronos.NULL;
        return analyzeMatchHistory(match, summoner, row);
    }

    public static ChronoTask analyzeMatchHistory(LOLMatch match, Summoner summoner, QueryRecord dataGame) {
        ChronoTask task = () -> {
            if (!SeasonUtils.isCurrentSplit(match.getGameStartTimestamp()) && match.getQueue() == GameQueueType.TEAM_BUILDER_RANKED_SOLO) return;
            if (isRemake(match)) return;

            HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(match, match.getParticipants());
            String fullGameId = fullGameId(match);
            MongoDB.upsertMatchDocument(fullGameId, Match.fromR4J(match, matchData));

            List<TierDivisionType> ranks = new ArrayList<>();
            for (MatchParticipant participant : match.getParticipants()) {
                if (participant.getPuuid().equals(summoner.getPUUID())) {
                    ranks.add(pushSummoner(match, fullGameId, summoner, participant, dataGame, matchData.get(participant.getPuuid())));
                    continue;
                }

                Summoner toPush = LeagueService.getRiotSummoner(participant.getPuuid(), match.getPlatform());
                toPush = checkSummoner(participant, toPush);
                if (toPush == null) {
                    BotLogger.error("CLEAR " + participant.getPuuid());
                    continue;
                }
                try { 
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, toPush, null);
                    Thread.sleep(500); 
                }
                catch (InterruptedException e) {e.printStackTrace();}
                ranks.add(pushSummoner(match, fullGameId, toPush, participant, matchData.get(participant.getPuuid())));
            }
            TierType avgRank = TierDivisionUtils.getAverageRank(ranks);
            MongoDB.updateMatchRank(fullGameId, avgRank);
            MongoDB.upsertMatchEventsJson(fullGameId, createJSONEvents(matchData.get("match")));
            LeagueService.invalidateMatchDetail(match.getPlatform(), String.valueOf(match.getGameId()));
            
            BotLogger.info("[LPTracker] Pushed match data for " + LeagueHandler.getFormattedSummonerName(summoner) + " (" + summoner.getAccountId() + ")");
        };
        return task;
    }

    public static ChronoTask analyzeMatchHistory(LOLMatch match) {
        return () -> {
            String fullGameId = fullGameId(match);
            if (MongoDB.hasMatch(fullGameId)) {
                BotLogger.info("[LPTracker] Match " + match.getGameId() + " already tracked");
                return;
            }

            HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(match, match.getParticipants());
            MongoDB.upsertMatchDocument(fullGameId, Match.fromR4J(match, matchData));

            List<TierDivisionType> ranks = new ArrayList<>();
            for (MatchParticipant participant : match.getParticipants()) {
                Summoner summoner = LeagueService.getRiotSummoner(participant.getPuuid(), match.getPlatform());
                if (summoner == null) continue;
                try { 
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, summoner, null);
                    Thread.sleep(500); 
                }
                catch (InterruptedException e) {e.printStackTrace();}

                TierDivisionType rank = pushSummoner(match, fullGameId, summoner, participant, matchData.get(participant.getPuuid()));
                ranks.add(rank);
            }
            TierType avgRank = TierDivisionUtils.getAverageRank(ranks);
            MongoDB.updateMatchRank(fullGameId, avgRank);
            MongoDB.upsertMatchEventsJson(fullGameId, createJSONEvents(matchData.get("match")));
            LeagueService.invalidateMatchDetail(match.getPlatform(), String.valueOf(match.getGameId()));
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

    public static TierDivisionType pushSummoner(LOLMatch match, String fullGameId, Summoner summoner, MatchParticipant participant, HashMap<String, String> matchData) {
        LeagueService.upsertSummoner(summoner, null);
        QueryRecord row = MongoDB.getRegisteredLolAccount(summoner.getPUUID(), SeasonUtils.getCurrentSplitRange()[0]);
        return pushSummoner(match, fullGameId, summoner, participant, row, matchData);
    }

    private static TierDivisionType pushSummoner(LOLMatch match, String fullGameId, Summoner summoner, MatchParticipant participant, QueryRecord dataGame, HashMap<String, String> matchData) {
        if (dataGame != null && match.getGameId() == dataGame.getAsLong("game_id")) return dataGame.getAsTier("rank");
        if (participant.getPuuid().equals("BOT")) return TierDivisionType.UNRANKED;

        List<LeagueEntry> entries = LeagueService.getLeagueEntries(summoner.getPUUID(), summoner.getPlatform());
        LeagueEntry league = entries.stream().filter(l -> l.getQueueType().commonName().equals("5v5 Ranked Solo")).findFirst().orElse(null);

        TierDivisionType oldDivision = dataGame == null ? TierDivisionType.UNRANKED : dataGame.getAsTier("rank");
        TierDivisionType division = league != null ? league.getTierDivisionType() : TierDivisionType.UNRANKED;

        int lp = league != null ? league.getLeaguePoints() : 0;
        int gain = 0;

        boolean isPromotionToMaster = oldDivision == TierDivisionType.DIAMOND_I && division == TierDivisionType.MASTER_I;
        boolean isMasterPlus = division == TierDivisionType.MASTER_I || division == TierDivisionType.GRANDMASTER_I || division == TierDivisionType.CHALLENGER_I;

        if (dataGame == null || dataGame.get("rank") == null || match.getQueue() != GameQueueType.TEAM_BUILDER_RANKED_SOLO) gain = 0;
        else if ((isPromotionToMaster || !isMasterPlus) && division != dataGame.getAsTier("rank")) {
            gain = 100 - (Math.abs(lp - dataGame.getAsInt("lp")));
            gain = division.ordinal() < dataGame.getAsTier("rank").ordinal() ? gain : -gain;
        } else {
            gain = lp - dataGame.getAsInt("lp");
        }
        LeagueService.upsertSummoner(summoner, null);

        Participant canonicalParticipant = toCanonicalParticipant(participant, matchData);
        canonicalParticipant.rank = division;
        canonicalParticipant.lp = lp;
        canonicalParticipant.gain = gain;
        MongoDB.upsertParticipant(fullGameId, canonicalParticipant);
        return division;
    }

    private static void updateLeaderboardEntries(List<LeagueEntry> entries, LeagueShard shard) {
        Map<String, List<LeagueEntry>> byPuuid = new LinkedHashMap<>();
        if (entries != null) for (LeagueEntry entry : entries) {
            if (entry != null && entry.getPuuid() != null) byPuuid.computeIfAbsent(entry.getPuuid(), ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<LeagueEntry>> entry : byPuuid.entrySet())
            LeagueService.saveRanks(entry.getKey(), shard, entry.getValue());
    }

    private static String fullGameId(LOLMatch match) {
        return match.getPlatform().name() + "_" + match.getGameId();
    }

    public static Match fromR4J(LOLMatch source) {
        return fromR4J(source, analyzeMatchBuild(source, source.getParticipants()));
    }

    public static Match fromR4J(
            LOLMatch source,
            Map<String, HashMap<String, String>> matchData) {
        Match match = toCanonicalMatch(source, matchData);
        if (matchData != null && matchData.get("match") != null) {
            match.eventData = new JSONObject(createJSONEvents(matchData.get("match"))).toMap();
            match.restoreEvents();
        }
        return match;
    }

    private static Match toCanonicalMatch(LOLMatch source, Map<String, HashMap<String, String>> matchData) {
        Match match = new Match();
        match.gameId = String.valueOf(source.getGameId());
        match.leagueShard = source.getPlatform();
        match.queue = source.getQueue();
        match.timeStart = source.getGameStartTimestamp() == null ? 0 : source.getGameStartTimestamp();
        match.timeEnd = source.getGameEndTimestamp() == null ? 0 : source.getGameEndTimestamp();
        match.patch = source.getGameVersion();
        match.participants = new ArrayList<>();
        for (MatchParticipant participant : source.getParticipants()) {
            match.participants.add(toCanonicalParticipant(participant, matchData.get(participant.getPuuid())));
        }
        if (source.getTeams() != null) for (no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam team : source.getTeams()) {
            List<Integer> bans = new ArrayList<>();
            if (team.getBans() != null) for (no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan ban : team.getBans()) bans.add(ban.getChampionId());
            if (team.getTeamId() != null) match.bans.put(team.getTeamId(), bans);
        }
        return match;
    }

    private static Participant toCanonicalParticipant(MatchParticipant source, HashMap<String, String> matchData) {
        Participant participant = new Participant();
        participant.id = source.getParticipantId();
        participant.win = source.didWin();
        participant.kda = source.getKills() + "/" + source.getDeaths() + "/" + source.getAssists();
        participant.champion = source.getChampionId();
        participant.lane = source.getChampionSelectLane() != null ? source.getChampionSelectLane() : source.getLane();
        participant.team = source.getTeam();
        participant.roleQuestId = source.getRoleBoundItem();
        participant.subTeam = source.getPlayerSubteamId();
        participant.subTeamPlacement = source.getSubteamPlacement();
        participant.damage = source.getTotalDamageDealtToChampions();
        participant.damageTaken = source.getTotalDamageTaken();
        participant.damageBuilding = source.getDamageDealtToBuildings();
        participant.healing = source.getTotalHeal();
        participant.cs = source.getTotalMinionsKilled() + source.getNeutralMinionsKilled();
        participant.goldEarned = source.getGoldEarned();
        participant.ward = source.getWardsPlaced();
        participant.wardKilled = source.getWardsKilled();
        participant.visionScore = source.getVisionScore();
        participant.puuid = source.getPuuid();
        participant.riotId = source.getRiotIdName();
        participant.riotTag = source.getRiotIdTagline();
        participant.level = source.getSummonerLevel();
        participant.doubles = source.getDoubleKills();
        participant.triples = source.getTripleKills();
        participant.quadruples = source.getQuadraKills();
        participant.pentas = source.getPentaKills();
        participant.item0 = source.getItem0();
        participant.item1 = source.getItem1();
        participant.item2 = source.getItem2();
        participant.item3 = source.getItem3();
        participant.item4 = source.getItem4();
        participant.item5 = source.getItem5();
        participant.item6 = source.getItem6();
        participant.turretKills = source.getTurretKills();
        participant.summonerSpell1 = source.getSummoner1Id();
        participant.summonerSpell2 = source.getSummoner2Id();
        if (matchData != null && !matchData.isEmpty()) ParticipantBuildCodec.apply(participant, createJSONBuild(matchData));
        return participant;
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
        build.put("build", matchData.getOrDefault("build", "").split(","));
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


    public static HashMap<String, HashMap<String, String>> analyzeMatchBuild(LOLMatch match, List<MatchParticipant> participants) {
        Map<Integer, Item> items = LeagueHandler.getRiotApi().getDDragonAPI().getItems();

        HashMap<String, HashMap<String, String>> matchData = new HashMap<>();
        for (MatchParticipant participant : participants) {
            LaneType lane = participant.getChampionSelectLane() != null ? participant.getChampionSelectLane() : participant.getLane();

            matchData.put(participant.getPuuid(), new HashMap<>());
            matchData.get(participant.getPuuid()).put("win", participant.didWin() ? "1" : "0");
            matchData.get(participant.getPuuid()).put("lane", String.valueOf(lane.ordinal()));
            matchData.get(participant.getPuuid()).put("champion", String.valueOf(participant.getChampionId()));
            matchData.get(participant.getPuuid()).put("stats", participant.getPerks().getStatPerks().getDefense() + "," + participant.getPerks().getStatPerks().getFlex() + "," + participant.getPerks().getStatPerks().getOffense());
            for (int i = 0; i < 2; i++) {
                for (PerkSelection perk : participant.getPerks().getPerkStyles().get(i).getSelections()) {
                    String perkList = matchData.get(participant.getPuuid()).getOrDefault("perks-" + i, "");
                    if (perkList.isEmpty()) perkList = perk.getPerk() + "";
                    else perkList += "," + perk.getPerk();
                    matchData.get(participant.getPuuid()).put("perks-" + i, perkList);
                }
                matchData.get(participant.getPuuid()).put("perks-" + i, participant.getPerks().getPerkStyles().get(i).getStyle() + "," + matchData.get(participant.getPuuid()).get("perks-" + i));
            }

            String itemsIds = participant.getItem0() + "," + participant.getItem1() + "," + participant.getItem2() + "," + participant.getItem3() + "," + participant.getItem4() + "," + participant.getItem5() + "," + participant.getItem6();
            

            matchData.get(participant.getPuuid()).put("summoner_spells", participant.getSummoner1Id() + "," + participant.getSummoner2Id());
            matchData.get(participant.getPuuid()).put("items", itemsIds);

            if (GameQueueTypeUtils.isCherry(match.getQueue())) {
                String augmentList = "";
                if (participant.getPlayerAugment1() != 0) augmentList = participant.getPlayerAugment1() + "";
                if (participant.getPlayerAugment2() != 0) augmentList += "," + participant.getPlayerAugment2();
                if (participant.getPlayerAugment3() != 0) augmentList += "," + participant.getPlayerAugment3();
                if (participant.getPlayerAugment4() != 0) augmentList += "," + participant.getPlayerAugment4();

                List<String> prismatics = List.of(itemsIds.split(",")).stream().filter(ItemUtils::isPrismatic).collect(Collectors.toList());
                if (!prismatics.isEmpty()) {
                    matchData.get(participant.getPuuid()).put("prismatics", String.join(",", prismatics));
                }
                matchData.get(participant.getPuuid()).put("augments", augmentList);
            }

            /**
             * i cant get the evolution of support item from the event
             * so i can just check all the slot and see which item i have and how i built it
             */
            if (lane == LaneType.UTILITY) {
                String supportItem = null;
                if (isSuppItemFromId(participant.getItem0()) != null)
                    supportItem = String.valueOf(participant.getItem0());
                else if (isSuppItemFromId(participant.getItem1()) != null)
                    supportItem = String.valueOf(participant.getItem1());
                else if (isSuppItemFromId(participant.getItem2()) != null)
                    supportItem = String.valueOf(participant.getItem2());
                else if (isSuppItemFromId(participant.getItem3()) != null)
                    supportItem = String.valueOf(participant.getItem3());
                else if (isSuppItemFromId(participant.getItem4()) != null)
                    supportItem = String.valueOf(participant.getItem4());
                else if (isSuppItemFromId(participant.getItem5()) != null)
                    supportItem = String.valueOf(participant.getItem5());
                else if (isSuppItemFromId(participant.getItem6()) != null)
                    supportItem = String.valueOf(participant.getItem6());

                if (supportItem != null) matchData.get(participant.getPuuid()).put("support_item", supportItem);
            }

        }

        LOLTimeline timeline = match.getTimeline();
        Map<String, List<String>> matchItemData = new HashMap<>();

        timeline.getParticipants().forEach(participant -> {
            matchData.put(String.valueOf(participant.getParticipantId()), matchData.get(participant.getPuuid()));
            matchData.remove(participant.getPuuid());
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
                String itemType = i == 1 ? "starter" : "build";

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
                            String killerTeam = event.getKillerTeamId() != null ? event.getKillerTeamId().name() : getParticipantTeam(participants, event.getKillerId());
                            addChampionKill(championKills, indexedChampionKills, event, killerTeam, false);
                            break;
                        }
                        case CHAMPION_SPECIAL_KILL: {
                            if (event.getKillType() != KillType.KILL_FIRST_BLOOD) break;
                            String killerTeam = event.getKillerTeamId() != null ? event.getKillerTeamId().name() : getParticipantTeam(participants, event.getKillerId());
                            addChampionKill(championKills, indexedChampionKills, event, killerTeam, true);
                            break;
                        }
                        case ELITE_MONSTER_KILL: {
                            if (event.getMonsterType() == null) break;

                            String monster = event.getMonsterType().name();
                            String subType = event.getMonsterSubType() != null ? event.getMonsterSubType().name() : "";
                            String killerTeam = event.getKillerTeamId() != null ? event.getKillerTeamId().name() : getParticipantTeam(participants, event.getKillerId());

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
        timeline.getParticipants().forEach(participant -> {
            matchParticipants.put(String.valueOf(participant.getParticipantId()), participant.getPuuid());

            matchData.put(participant.getPuuid(), matchData.get(String.valueOf(participant.getParticipantId())));
            matchData.remove(String.valueOf(participant.getParticipantId()));

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

    public static void retrieveSampleGames(GameQueueType queue) {
        BotLogger.info("[LPTracker] Pushing sample matches");
        String currentPatch = PatchUtils.getPatch();
        String previousPatch = PatchUtils.getPreviousPatch();
    
        long[] splitRange = SeasonUtils.getCurrentSplitRange();
    
        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            ChronoTask shardTask = () -> {
                long threshold = splitRange != null ? MongoDB.findLatestMatchTime(previousPatch, shard) : 0;
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
                        LeagueService.putWeakLeagueEntry(shard, entry);
                        Summoner summoner = LeagueService.getRiotSummoner(entry.getPuuid(), shard);
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
                        LOLMatch match = LeagueService.getR4JMatch(me.matchId(), me.summoner().getPlatform());
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

    public static void retrieveChallengerEntries() {
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
                    for (LeagueEntry entry : entries) LeagueService.upsertSummoner(
                            LeagueService.getRiotSummoner(entry.getPuuid(), shard), null);
                    updateLeaderboardEntries(entries, shard);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }  
        LeaderboardService.rebuildDistribution();
    }

    public static void retrieveHighEloEntries() {
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
                        for (LeagueEntry entry : entries) LeagueService.upsertSummoner(
                                LeagueService.getRiotSummoner(entry.getPuuid(), shard), null);
                        updateLeaderboardEntries(entries, shard);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }  
        }
        LeaderboardService.rebuildDistribution();
    }

    public static void retrieveAllEntries() {
        BotLogger.info("[LPTracker] Pushing all entries");
        List<CompletableFuture<Void>> shardTasks = new ArrayList<>();
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
                                for (LeagueEntry entry : entries) LeagueService.upsertSummoner(
                                        LeagueService.getRiotSummoner(entry.getPuuid(), shard), null);
                                updateLeaderboardEntries(entries, shard);
                                page++;
                                Thread.sleep(500);
                            } while (entries.size() > 0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
            };
            shardTasks.add(task.queueFuture());
        }
        if (!shardTasks.isEmpty()) CompletableFuture.allOf(shardTasks.toArray(new CompletableFuture[0]))
            .thenRun(LeaderboardService::rebuildDistribution);
        
    }

    public static void retrieveSampleGamesPatch() {
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
                        Summoner summoner = LeagueService.getRiotSummoner(entry.getPuuid(), shard);
                        RiotAccount account = LeagueService.getAccountFromSummoner(summoner);
                        BotLogger.info("[LPTracker] Analyzing summoner " + account.getName() + "#" + account.getTag() + " | " + j + "/" + entries.size());

                        List<String> matchIds = summoner.getLeagueGames().withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).get();
                        if (matchIds.isEmpty()) 
                            continue;

                        int k = 0;
                        LOLMatch match;
                        do {
                            String matchId = matchIds.get(k); 
                            match = LeagueService.getR4JMatch(matchId, shard);
                            
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
    
        List<QueryRecord> matchDatas = MongoDB.findMatchBans(patch);
        List<QueryRecord> championDatas = MongoDB.findChampionWins(patch, champion, lane);
    
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

    public static void retrieveMatchHistory(Summoner summoner) {
        try {
            List<String> matchIds = new ArrayList<>();
            List<String> retrievedMatchIds;
    
            do {
                retrievedMatchIds = summoner.getLeagueGames().withCount(100).withBeginIndex(matchIds.size()).get();
                matchIds.addAll(retrievedMatchIds);
                Thread.sleep(350);
            } while (retrievedMatchIds.size() > 0);
    
            int i = 0;
            for (String matchId : matchIds) {
                try {
                    i++;
                    if (LeagueHandler.isMatchDBCached(matchId)) continue;
                    if (!LeagueHandler.isMatchLocallyCached(matchId, summoner.getPlatform())) {
                        Thread.sleep(350);
                    }
                    LOLMatch match = LeagueService.getR4JMatch(matchId, summoner.getPlatform());
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

    public static void retrieveMatchHistory(Summoner summoner, GameQueueType queue) {
        try {
            List<String> matchIds = new ArrayList<>();
            List<String> retrievedMatchIds = summoner.getLeagueGames().withCount(100).withQueue(queue).get();
            do {
                matchIds.addAll(retrievedMatchIds);

                try { Thread.sleep(350); }
                catch (InterruptedException e) {e.printStackTrace();}

                int i = 0;
                for (String matchId : retrievedMatchIds) {
                    try {
                        LOLMatch match = LeagueService.getR4JMatch(matchId, summoner.getPlatform());
                        if (match == null) continue;
                        System.out.println("[" + i + "/" + retrievedMatchIds.size() + "] " + match.getGameId() + " - " + match.getPlatform() + " - " + match.getQueue());
                        Tracker.queueMatch(match);
                        i++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                retrievedMatchIds = summoner.getLeagueGames().withCount(100).withQueue(queue).withBeginIndex(matchIds.size()).get();
            } while (retrievedMatchIds.size() > 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
