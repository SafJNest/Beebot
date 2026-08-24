package com.safjnest.lol.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.SyncScheduler;
import com.safjnest.lol.service.MatchService;
import com.safjnest.lol.service.RankService;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.ItemUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.ParticipantBuildCodec;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.TimeConstant;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.KillType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
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

public class Tracker {

    private static final int MATCH_LOOKUP_MAX_RETRIES = 3;
    private static final List<GameQueueType> HIGH_ELO_QUEUES = List.of(
        GameQueueType.RANKED_SOLO_5X5,
        GameQueueType.RANKED_FLEX_SR
    );
    private static final long timelineSnapshotInterval = TimeConstant.MINUTE * 5;

    private static List<GameQueueType> toTrack = List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.CHERRY);


    static void retrieveSummoners() {
        List<com.safjnest.lol.model.summoner.Summoner> accounts = MongoDB.findTrackedSummonerModels();
        Map<LeagueShard, List<com.safjnest.lol.model.summoner.Summoner>> accountsByShard = new LinkedHashMap<>();
        for (com.safjnest.lol.model.summoner.Summoner account : accounts) {
            if (account == null || account.puuid() == null) continue;
            LeagueShard shard = account.region();
            if (shard == null || shard == LeagueShard.UNKNOWN) {
                BotLogger.error("Tracked summoner has no active shard: " + account.puuid());
                continue;
            }
            accountsByShard.computeIfAbsent(shard, ignored -> new ArrayList<>()).add(account);
        }
        QueueHandler.immediate(SyncScheduler.class, null, "tracking", "tracking summoners", root -> {
            root.phase("TRACKING");
            for (Map.Entry<LeagueShard, List<com.safjnest.lol.model.summoner.Summoner>> entry : accountsByShard.entrySet()) {
                LeagueShard shard = entry.getKey();
                List<com.safjnest.lol.model.summoner.Summoner> shardAccounts = entry.getValue();
                QueueHandler.immediate(SyncScheduler.class, shard, "tracking:" + shard.name(),
                    "tracking summoners shard=" + shard.name(), shardJob -> {
                        shardJob.phase("TRACKING");
                        for (com.safjnest.lol.model.summoner.Summoner account : shardAccounts) {
                            QueueHandler.immediate(SyncScheduler.class, shard,
                                "tracking-summoner:" + shard.name() + ':' + account.puuid(),
                                "tracking summoner puuid=" + account.puuid(), summonerJob -> {
                                    retrieveSummoner(account, summonerJob);
                                    return null;
                                });
                        }
                        return null;
                    });
            }
            return null;
        });
        BotLogger.info("[LPTracker] Start tracking summoners (" + accounts.size() + " accounts)");
    }

    private static void retrieveSummoner(com.safjnest.lol.model.summoner.Summoner account, Job<?> task) {
        try {
            Summoner summoner = null;
            try {
                LeagueShard accountShard = account.region();
                summoner = SummonerService.getRiotSummoner(account.puuid(), accountShard);
                if (summoner == null) throw new Exception("account null ??????");

                LeagueHandler.clearCache(URLEndpoint.V5_MATCHLIST, summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO);

                try { Thread.sleep(350); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }

                List<String> matchIds = MatchService.getIds(
                    summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO, 0, 2, 0, null);
                if (matchIds == null || matchIds.isEmpty()) {
                    task.done(account.puuid());
                    return;
                }

                String currentMatchId = matchIds.get(0);
                LeagueShard shard = matchShard(currentMatchId, summoner.getPlatform());
                if (matchIds.size() > 1) persistPreviousMatch(matchIds.get(1), summoner.getPlatform());
                if (MongoDB.isMatchTracked(currentMatchId, account.puuid())) {
                    task.done(account.puuid());
                    return;
                }
                if (shard != summoner.getPlatform()) summoner = SummonerService.getRiotSummoner(summoner.getPUUID(), shard);

                LOLMatch match = MatchService.getRiotMatch(currentMatchId, shard);
                if (match == null || !isRankedSolo(match.getQueue())) {
                    task.missing(account.puuid());
                    return;
                }
                if (trackMatch(match, account.puuid(), account.riotId(), JobPriority.IMMEDIATE) == null)
                    task.failed(account.puuid());
                else task.done(account.puuid());
            } catch (Exception exception) {
                exception.printStackTrace();
                BotLogger.error(summoner == null ? account.puuid() : summoner.toString());
                task.failed(account.puuid());
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            task.failed(account.puuid());
        }
    }

    /**
     * In the future, this function will not save the games but will simply analyze them and push the data into tables with already processed data to reduce db size.
     * <p>
     * For now, it will just save the data into the db
     * <p>
     * im lazy UwU
     */
    public static CompletableFuture<Match> enqueueMatch(LOLMatch match, JobPriority priority) {
        if (match == null || match.getPlatform() == null) return CompletableFuture.completedFuture(null);
        String gameId = fullGameId(match);
        return submit(priority, match.getPlatform(), "match:" + match.getPlatform().name() + ":" + gameId,
            "match analysis id=" + gameId, task -> {
                task.phase("PERSISTING");
                task.trackItem(gameId);
                Match persisted = persistFetchedMatch(match);
                if (persisted == null) task.failed(gameId);
                else task.done(gameId);
                return persisted;
            });
    }

    public static Match persistFetchedMatch(LOLMatch match) {
        if (match == null || match.getPlatform() == null) return null;
        MatchService.cacheRiotMatch(match);
        return persistRawMatch(match);
    }

    public static void enqueueParticipantRefresh(String puuid, LeagueShard shard) {
        if (puuid == null || puuid.isBlank() || shard == null) return;
        QueueHandler.background(SyncScheduler.class, shard, "participant-refresh:" + shard.name() + ":" + puuid,
            "participant rank refresh puuid=" + puuid, job -> {
                QueueHandler.retain(job);
                RankService.refreshBackgroundAsync(puuid, shard).whenComplete((ignored, failure) -> QueueHandler.resume(job, () -> {
                    if (failure != null) throw new IllegalStateException("Participant refresh failed for " + puuid, failure);
                }));
                return null;
            });
    }

    public static void enqueueMatchLookup(LeagueShard shard, String gameId) {
        if (shard == null || gameId == null || gameId.isBlank()) return;

        QueueHandler.background(SyncScheduler.class, shard, "match-lookup:" + shard.name() + ":" + gameId,
            "match lookup id=" + gameId, task -> {
                task.phase("LOOKUP");
                task.trackItem(gameId);
                Match match = lookupMatch(shard, gameId);
                if (match == null) task.missing(gameId);
                else task.done(gameId);
                return match;
            });
    }

    public static void enqueueRecentMatches(Summoner summoner, int limit) {
        if (summoner == null || summoner.getPUUID() == null || summoner.getPlatform() == null || limit < 1) return;

        String key = "recent-matches:" + summoner.getPlatform().name() + ":" + summoner.getPUUID();
        String name = "recent matches puuid=" + summoner.getPUUID();
        QueueHandler.background(SyncScheduler.class, summoner.getPlatform(), key, name, job -> {
            QueueHandler.retain(job);
            MatchService.getIdsAsync(summoner, null, 0, limit, 0, null).whenComplete((matchIds, failure) -> QueueHandler.resume(job, () -> {
                if (failure != null) throw new IllegalStateException("Recent matches lookup failed", failure);
                for (String matchId : matchIds) {
                    if (MongoDB.hasMatch(matchId)) continue;
                    enqueueMatchLookup(matchShard(matchId, summoner.getPlatform()), matchId);
                }
            }));
            return null;
        });
    }

    private static Match lookupMatch(LeagueShard shard, String gameId) {
        for (int retry = 0; retry < MATCH_LOOKUP_MAX_RETRIES; retry++) {
            try {
                LOLMatch match = MatchService.getRiotMatch(gameId, shard);
                if (match != null) return persistRawMatch(match);
            } catch (Exception exception) {
                BotLogger.error("Match lookup failed for game=" + gameId + " message=" + exception.getMessage());
            }
        }
        return null;
    }

    private static <T> CompletableFuture<T> submit(JobPriority priority, LeagueShard shard,
        String key, String name, java.util.function.Function<Job<T>, T> work) {
        return switch (priority) {
            case IMMEDIATE -> QueueHandler.immediate(SyncScheduler.class, shard, key, name, work);
            case NORMAL -> QueueHandler.normal(SyncScheduler.class, shard, key, name, work);
            case BACKGROUND -> QueueHandler.background(SyncScheduler.class, shard, key, name, work);
        };
    }

    public static Summoner checkSummoner(MatchParticipant participant, Summoner summoner) {
        if (participant == null || participant.getPuuid() == null || summoner == null || summoner.getPUUID() == null)
            return null;
        if (summoner.getPUUID().equals(participant.getPuuid()))
            return summoner;
        
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("platform", summoner.getPlatform());
        data.put("puuid", participant.getPuuid());
        LeagueHandler.clearCache(URLEndpoint.V4_SUMMONER_BY_PUUID, data);

        summoner = SummonerService.getRiotSummoner(participant.getPuuid(), summoner.getPlatform());
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


    public static CompletableFuture<Match> analyzeMatchHistory(GameQueueType queue, Summoner summoner) {
        if (queue == null || summoner == null || !toTrack.contains(queue)) return CompletableFuture.completedFuture(null);

        SummonerService.upsert(summoner, null);

        try { Thread.sleep(350); }
        catch (InterruptedException e) {e.printStackTrace();}

        List<String> matchIds = MatchService.getIds(summoner, queue, 0, 2, 0, null);
        if (matchIds == null || matchIds.isEmpty()) return CompletableFuture.completedFuture(null);

        String matchId = matchIds.get(0);
        LeagueShard shard = matchShard(matchId, summoner.getPlatform());
        if (MongoDB.isMatchTracked(matchId, summoner.getPUUID())) return CompletableFuture.completedFuture(null);
        if (shard != summoner.getPlatform()) summoner = SummonerService.getRiotSummoner(summoner.getPUUID(), shard);

        LOLMatch match = MatchService.getRiotMatch(matchId, shard);
        if (match == null || match.getQueue() != queue) return CompletableFuture.completedFuture(null);
        Summoner trackedSummoner = summoner;
        return QueueHandler.immediate(SyncScheduler.class, shard, "match-track:" + shard.name() + ":" + matchId,
            "match rank tracking id=" + matchId, ignored -> trackMatch(match, trackedSummoner.getPUUID(), null, JobPriority.IMMEDIATE));
    }

    public static CompletableFuture<Match> analyzeMatchHistory(LOLMatch match, Summoner summoner, QueryRecord dataGame) {
        if (match == null || summoner == null) return CompletableFuture.completedFuture(null);
        return QueueHandler.immediate(SyncScheduler.class, match.getPlatform(), "match-track:" + match.getPlatform().name() + ":" + fullGameId(match),
            "match rank tracking id=" + fullGameId(match), ignored -> trackMatch(match, summoner.getPUUID(), null, JobPriority.IMMEDIATE));
    }

    public static CompletableFuture<Match> analyzeMatchHistory(LOLMatch match) {
        return enqueueMatch(match, JobPriority.BACKGROUND);
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

    private static Match persistRawMatch(LOLMatch source) {
        String currentFullGameId = fullGameId(source);
        if (!SeasonUtils.isCurrentSplit(source.getGameStartTimestamp()) && source.getQueue() == GameQueueType.TEAM_BUILDER_RANKED_SOLO) {
            completeMatch(source);
            return null;
        }
        if (isRemake(source)) {
            completeMatch(source);
            return null;
        }

        Match match = MongoDB.findMatch(currentFullGameId);
        if (match == null) {
            match = Match.fromR4J(source);
            if (match == null || !MongoDB.upsertMatch(currentFullGameId, match, false)) return null;
        }

        if (source.getParticipants() == null) return null;
        for (MatchParticipant participant : source.getParticipants()) {
            if (!MongoDB.upsertSummoner(participant, source.getPlatform())) return null;
        }

        completeMatch(source);
        return match;
    }

    private static void persistPreviousMatch(String matchId, LeagueShard fallbackShard) {
        LeagueShard shard = matchShard(matchId, fallbackShard);
        if (matchId == null || matchId.isBlank() || shard == null || MongoDB.hasMatch(matchId)) return;

        LOLMatch match = MatchService.getRiotMatch(matchId, shard);
        if (match != null) persistRawMatch(match);
    }

    private static Match trackMatch(LOLMatch source, String referencePuuid, String trackedSummoner, JobPriority priority) {
        if (source == null || source.getPlatform() == null || referencePuuid == null || referencePuuid.isBlank()) return null;
        if (!isRankedSolo(source.getQueue()) || isRemake(source)) return null;

        String currentFullGameId = fullGameId(source);
        if (MongoDB.isMatchTracked(currentFullGameId, referencePuuid)) return MongoDB.findMatch(currentFullGameId);
        if (persistRawMatch(source) == null) return null;

        Match match = loadMatch(source);
        if (match == null || match.participants == null) return null;

        List<TierDivisionType> ranks = new ArrayList<>();
        for (MatchParticipant sourceParticipant : source.getParticipants()) {
            Participant participant = findParticipant(match, sourceParticipant.getPuuid());
            if (participant == null) continue;

            Participant previous = MongoDB.findPreviousParticipant(
                sourceParticipant.getPuuid(),
                source.getPlatform(),
                source.getQueue(),
                source.getGameStartTimestamp()
            );
            Rank stored = findRank(RankService.find(sourceParticipant.getPuuid(), source.getPlatform()), source.getQueue());
            Participant baseline = rankBaseline(previous, stored);

            Rank current = refreshRank(sourceParticipant.getPuuid(), source.getPlatform(), source.getQueue(), priority);
            participant.rank = current.tier();
            participant.lp = current.lp();
            participant.gain = calculateGain(source.getQueue(), participant.rank, participant.lp, baseline);
            ranks.add(participant.rank);
        }

        match.rank = TierDivisionUtils.getAverageRank(ranks);
        if (!MongoDB.upsertMatch(currentFullGameId, match, true)) return null;
        MatchService.invalidate(currentFullGameId, source.getPlatform());
        if (trackedSummoner != null && !trackedSummoner.isBlank())
            BotLogger.info("[LPTracker] Pushed match data for " + trackedSummoner + " (" + referencePuuid + ")");
        return match;
    }

    static Participant rankBaseline(Participant previous, Rank stored) {
        if (previous != null && previous.rank != null) return previous;
        if (stored == null) return null;

        Participant participant = new Participant();
        participant.rank = stored.tier();
        participant.lp = stored.lp();
        return participant;
    }

    private static Rank refreshRank(String puuid, LeagueShard shard, GameQueueType queue, JobPriority priority) {
        List<Rank> ranks = RankService.refreshSync(puuid, shard, priority);
        Rank rank = findRank(ranks, queue);
        return rank == null ? Rank.unranked() : rank;
    }

    private static Rank findRank(List<Rank> ranks, GameQueueType queue) {
        GameQueueType canonicalQueue = GameQueueTypeUtils.canonicalQueue(queue);
        if (ranks == null) return null;
        for (Rank rank : ranks) if (rank != null && rank.queue() == canonicalQueue) return rank;
        return null;
    }

    private static boolean isRankedSolo(GameQueueType queue) {
        return GameQueueTypeUtils.canonicalQueue(queue) == GameQueueType.RANKED_SOLO_5X5;
    }

    private static void completeMatch(LOLMatch match) {
        if (match == null) return;

        MatchService.deleteRiotMatch(fullGameId(match), match.getPlatform());
    }

    static int calculateGain(GameQueueType queue, TierDivisionType division, int lp, Participant previousParticipant) {
        if (previousParticipant == null || previousParticipant.rank == null || !isRankedSolo(queue)) return 0;

        TierDivisionType previousDivision = previousParticipant.rank;
        boolean isPromotionToMaster = previousDivision == TierDivisionType.DIAMOND_I && division == TierDivisionType.MASTER_I;
        boolean isMasterPlus = division == TierDivisionType.MASTER_I || division == TierDivisionType.GRANDMASTER_I || division == TierDivisionType.CHALLENGER_I;
        if ((isPromotionToMaster || !isMasterPlus) && division != previousDivision) {
            int gain = 100 - Math.abs(lp - previousParticipant.lp);
            return division.ordinal() < previousDivision.ordinal() ? gain : -gain;
        }
        return lp - previousParticipant.lp;
    }

    private static void updateRank(LeagueEntry entry, LeagueShard shard) {
        if (entry == null || entry.getPuuid() == null || shard == null) return;

        if (SummonerService.find(entry.getPuuid(), shard) == null) {
            Summoner summoner = SummonerService.getRiotSummoner(entry.getPuuid(), shard);
            if (summoner == null) return;
            if (SummonerService.find(entry.getPuuid(), shard) == null && !SummonerService.upsert(summoner, null)) return;
        }
        RankService.put(shard, entry);
    }

    private static String fullGameId(LOLMatch match) {
        return match.getPlatform().name() + "_" + match.getGameId();
    }

    public static Match loadMatch(LOLMatch source) {
        if (source == null) return null;

        HashMap<String, HashMap<String, String>> matchData = analyzeMatchBuild(source, source.getParticipants());
        return fromR4J(source, matchData);
    }

    private static Participant findParticipant(Match match, String puuid) {
        if (match == null || match.participants == null || puuid == null) return null;
        for (Participant participant : match.participants) {
            if (participant != null && puuid.equals(participant.puuid)) return participant;
        }
        return null;
    }
    
    private static LeagueShard matchShard(String matchId, LeagueShard fallback) {
        if (matchId == null || matchId.isBlank()) return fallback;
        try {
            return LeagueShard.valueOf(matchId.split("_", 2)[0]);
        } catch (Exception ignored) {
            return fallback;
        }
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
        match.gameId = source.getPlatform().name() + "_" + source.getGameId();
        match.leagueShard = source.getPlatform();
        match.queue = source.getQueue();
        match.timeStart = source.getGameStartTimestamp() == null ? 0 : source.getGameStartTimestamp();
        match.timeEnd = source.getGameEndTimestamp() == null ? 0 : source.getGameEndTimestamp();
        match.patch = source.getGameVersion();
        match.participants = new ArrayList<>();
        for (MatchParticipant participant : source.getParticipants()) {
            HashMap<String, String> participantData = matchData == null ? null : matchData.get(participant.getPuuid());
            match.participants.add(toCanonicalParticipant(participant, participantData));
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
        participant.healing = source.getTotalHealsOnTeammates() + source.getTotalDamageShieldedOnTeammates();
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
        participant.q = source.getSpell1Casts();
        participant.w = source.getSpell2Casts();
        participant.e = source.getSpell3Casts();
        participant.r = source.getSpell4Casts();
        participant.d = source.getSummoner1Casts();
        participant.f = source.getSummoner2Casts();
        participant.summonerSpell1 = source.getSummoner1Id();
        participant.summonerSpell2 = source.getSummoner2Id();
        participant.pings.put("push", source.getPushPings());
        participant.pings.put("bait", source.getBaitPings());
        participant.pings.put("danger", source.getDangerPings());
        participant.pings.put("hold", source.getHoldPings());
        participant.pings.put("all_in", source.getAllInPings());
        participant.pings.put("basic", source.getBasicPings());
        participant.pings.put("command", source.getCommandPings());
        participant.pings.put("get_back", source.getGetBackPings());
        participant.pings.put("on_my_way", source.getOnMyWayPings());
        participant.pings.put("assist_me", source.getAssistMePings());
        participant.pings.put("need_vision", source.getNeedVisionPings());
        participant.pings.put("enemy_vision", source.getEnemyVisionPings());
        participant.pings.put("enemy_missing", source.getEnemyMissingPings());
        participant.pings.put("vision_cleared", source.getVisionClearedPings());
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
        List<LeagueShard> shards = LeagueShardUtils.getActives();
        QueueHandler.background(SyncScheduler.class, null, "sample-games:" + queue.name(), "sample games", root -> {
            for (LeagueShard shard : shards) {
                QueueHandler.background(SyncScheduler.class, shard, "sample-games:" + shard.name() + ":" + queue.name(),
                    "sample games queue=" + queue.name(), task -> {
                try {
                    task.phase("DISCOVERING");
                    long threshold = splitRange != null ? MongoDB.findLatestMatchTime(previousPatch, shard) : 0;
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("platform", shard);
                    data.put("queue", GameQueueType.RANKED_SOLO_5X5);
                    LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_CHALLENGER, data);
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
        
                    List<LeagueEntry> entries = RankService.getByTier(
                        shard, GameQueueType.RANKED_SOLO_5X5, TierDivisionType.CHALLENGER_I, 0);
        
                    record MatchEntry(LeagueEntry entry, Summoner summoner, String matchId) {}
                    List<MatchEntry> allMatches = new ArrayList<>();
                    Set<String> seenMatchIds = new HashSet<>();
        
                    for (LeagueEntry entry : entries) {
                        try {
                            RankService.put(shard, entry);
                            Summoner summoner = SummonerService.getRiotSummoner(entry.getPuuid(), shard);
                            List<String> matchIds = new ArrayList<>();
                            for (int start = 0; matchIds.size() == start; start += 100) {
                                matchIds.addAll(MatchService.getIds(
                                    summoner,
                                    queue,
                                    start,
                                    100,
                                    threshold,
                                    queue == GameQueueType.CHERRY ? MatchlistMatchType.NORMAL : null
                                ));
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
                    for (MatchEntry entry : allMatches) task.trackItem(entry.matchId());
                    task.phase("PERSISTING");
                    int i = 0;
                    for (MatchEntry me : allMatches) {
                        try {
                            LOLMatch match = MatchService.getRiotMatch(me.matchId(), me.summoner().getPlatform());
                            if (match == null) {
                                task.missing(me.matchId());
                                continue;
                            }
                            if (!match.getGameVersion().startsWith(currentPatch)) {
                                task.done(me.matchId());
                                continue;
                            }
                            i++;
                            BotLogger.info("[LPTracker] [" + i + "/" + allMatches.size() + "] Pushing " + me.entry().getTier() + " match " + shard + " - " + LeagueHandler.getFormattedSummonerName(me.summoner()) + " -> " + me.matchId());
                            Match persisted = persistFetchedMatch(match);
                            if (persisted == null) task.failed(me.matchId());
                            else task.done(me.matchId());
                            Thread.sleep(350);
                        } catch (Exception e) {
                            e.printStackTrace();
                            task.failed(me.matchId());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
                    });
            }
            return null;
        });
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
    
                    List<LeagueEntry> entries = RankService.getByTier(shard, queue, TierDivisionType.CHALLENGER_I, 0);
                    BotLogger.info("[LPTracker] Start analyzing " + entries.size() + " challengers for region " + shard);
                    for (LeagueEntry entry : entries) updateRank(entry, shard);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }  
    }

    static void retrieveRankEntries(boolean highElo, boolean allEntries) {
        List<LeagueShard> shards = LeagueShardUtils.getActives();
        List<TierDivisionType> highEloTiers = highElo ? highEloTiers() : List.of();
        List<TierDivisionType> allEntriesTiers = allEntries ? allEntriesTiers() : List.of();
        String type = "RANK_ENTRIES";
        BotLogger.info("[LPTracker] Pushing rank entries highElo=" + highElo + " allEntries=" + allEntries);
        QueueHandler.background(SyncScheduler.class, null, "rank-entries", type, root -> {
            for (LeagueShard shard : shards) {
                for (TierDivisionType tier : highEloTiers) for (GameQueueType queue : HIGH_ELO_QUEUES)
                    enqueueHighRankEntries(shard, tier, queue);
                for (TierDivisionType tier : allEntriesTiers)
                    enqueueAllRankEntries(shard, tier);
            }
            return null;
        });
    }

    private static void enqueueHighRankEntries(LeagueShard shard, TierDivisionType tier, GameQueueType queue) {
        String key = "rank-high:" + shard.name() + ":" + tier.name() + ":" + queue.name();
        QueueHandler.background(SyncScheduler.class, shard, key, "rank entries tier=" + tier.name() + " queue=" + queue.name(), task -> {
                task.phase("FETCHING");
                try {
                    URLEndpoint endpoint = tier == TierDivisionType.CHALLENGER_I ? URLEndpoint.V4_LEAGUE_CHALLENGER
                        : tier == TierDivisionType.GRANDMASTER_I ? URLEndpoint.V4_LEAGUE_GRANDMASTER : URLEndpoint.V4_LEAGUE_MASTER;
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("platform", shard);
                    data.put("queue", queue);
                    LeagueHandler.clearCache(endpoint, data);
                    Thread.sleep(500);
                    List<LeagueEntry> entries = RankService.getByTier(shard, queue, tier, 0);
                    task.phase("PERSISTING");
                    for (LeagueEntry entry : entries) {
                        task.trackItem(entry.getPuuid());
                        try {
                            updateRank(entry, shard);
                            task.done(entry.getPuuid());
                        } catch (Exception exception) {
                            task.failed(entry.getPuuid());
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                    throw new IllegalStateException("Rank entries failed for " + key, exception);
                }
                return null;
            });
    }

    private static void enqueueAllRankEntries(LeagueShard shard, TierDivisionType tier) {
        GameQueueType queue = GameQueueType.RANKED_SOLO_5X5;
        String key = "rank-all:" + shard.name() + ":" + tier.name() + ":" + queue.name();
        QueueHandler.background(SyncScheduler.class, shard, key, "rank entries tier=" + tier.name() + " queue=" + queue.name(), task -> {
                task.phase("FETCHING");
                try {
                    List<LeagueEntry> entries = new ArrayList<>();
                    for (int page = 1; ; page++) {
                        entries = RankService.getByTier(shard, queue, tier, page);
                        BotLogger.info("[LPTracker] Start analyzing page " + page + " of " + tier.name() + " for region " + shard + " | Entries: " + entries.size());
                        task.phase("PERSISTING");
                        for (LeagueEntry entry : entries) {
                            task.trackItem(entry.getPuuid());
                            try {
                                updateRank(entry, shard);
                                task.done(entry.getPuuid());
                            } catch (Exception exception) {
                                task.failed(entry.getPuuid());
                            }
                        }
                        if (entries.isEmpty()) break;
                        task.phase("FETCHING");
                        Thread.sleep(500);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                    throw new IllegalStateException("Rank entries failed for " + key, exception);
                }
                return null;
            });
    }

    private static List<TierDivisionType> allEntriesTiers() {
        List<TierDivisionType> tiers = new ArrayList<>(List.of(TierDivisionType.values()));
        Collections.reverse(tiers);
        tiers.remove(0);
        tiers.removeIf(tier -> TierDivisionUtils.isHighElo(tier) || tier == TierDivisionType.UNRANKED);
        return tiers;
    }

    private static List<TierDivisionType> highEloTiers() {
        List<TierDivisionType> tiers = new ArrayList<>();
        for (TierDivisionType tier : TierDivisionType.values()) {
            if (TierDivisionUtils.isHighElo(tier)) tiers.add(tier);
        }
        return tiers;
    }

    public static void retrieveSampleGamesPatch() {
        String currentPatch = PatchUtils.getPatch();
        BotLogger.info("[LPTracker] Pushing sample matches");
        List<LeagueShard> shards = List.of(LeagueShard.EUW1);
        for (LeagueShard shard : shards) {
            try {
                List<LeagueEntry> entries = RankService.getByTier(
                    shard, GameQueueType.RANKED_SOLO_5X5, TierDivisionType.CHALLENGER_I, 0);

                BotLogger.info("[LPTracker] Start analyzing " + entries.size() + " matches for region " + shard);

                for (int j = 0; j < entries.size() ; j++) {
                    try {
                        LeagueEntry entry = entries.get(j);
                        Summoner summoner = SummonerService.getRiotSummoner(entry.getPuuid(), shard);
                        RiotAccount account = SummonerService.getRiotAccountFromSummoner(summoner);
                        BotLogger.info("[LPTracker] Analyzing summoner " + account.getName() + "#" + account.getTag() + " | " + j + "/" + entries.size());

                        List<String> matchIds = MatchService.getIds(
                            summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO, 0, 0, 0, null);
                        if (matchIds.isEmpty()) 
                            continue;

                        int k = 0;
                        LOLMatch match;
                        do {
                            String matchId = matchIds.get(k); 
                            match = MatchService.getRiotMatch(matchId, shard);
                            
                            BotLogger.info("[LPTracker] Pushing match data for region " + shard + " | " + k + "/5 -  " + j + "/" + entries.size());
                            enqueueMatch(match, JobPriority.BACKGROUND);
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
                retrievedMatchIds = MatchService.getIds(summoner, null, matchIds.size(), 100, 0, null);
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
                    LOLMatch match = MatchService.getRiotMatch(matchId, summoner.getPlatform());
                    if (match == null) continue;
                    System.out.println("[" + i + "/" + matchIds.size() + "] " + match.getGameId() + " - " + match.getPlatform() + " - " + match.getQueue());
                    Tracker.enqueueMatch(match, JobPriority.BACKGROUND);
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
            List<String> retrievedMatchIds = MatchService.getIds(summoner, queue, 0, 100, 0, null);
            do {
                matchIds.addAll(retrievedMatchIds);

                try { Thread.sleep(350); }
                catch (InterruptedException e) {e.printStackTrace();}

                int i = 0;
                for (String matchId : retrievedMatchIds) {
                    try {
                        LOLMatch match = MatchService.getRiotMatch(matchId, summoner.getPlatform());
                        if (match == null) continue;
                        System.out.println("[" + i + "/" + retrievedMatchIds.size() + "] " + match.getGameId() + " - " + match.getPlatform() + " - " + match.getQueue());
                        Tracker.enqueueMatch(match, JobPriority.BACKGROUND);
                        i++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                retrievedMatchIds = MatchService.getIds(summoner, queue, matchIds.size(), 100, 0, null);
            } while (retrievedMatchIds.size() > 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
