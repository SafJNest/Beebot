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

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
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
import com.safjnest.lol.utils.MatchUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.ParticipantBuildCodec;
import com.safjnest.lol.utils.RankProgressUtils;
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

    private static final long timelineSnapshotInterval = TimeConstant.MINUTE * 5;

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

                List<String> matchIds = MatchService.getMatchlist(
                    summoner, GameQueueType.TEAM_BUILDER_RANKED_SOLO, 0, 2, 0, null);
                if (matchIds == null || matchIds.isEmpty()) {
                    task.done(account.puuid());
                    return;
                }

                String currentMatchId = matchIds.get(0);
                LeagueShard shard = MatchUtils.matchShard(currentMatchId, summoner.getPlatform());
                if (matchIds.size() > 1) persistPreviousMatch(matchIds.get(1), summoner.getPlatform());
                if (MongoDB.isMatchTracked(currentMatchId)) {
                    task.done(account.puuid());
                    return;
                }
                if (shard != summoner.getPlatform()) summoner = SummonerService.getRiotSummoner(summoner.getPUUID(), shard);

                LOLMatch match = MatchService.fetch(currentMatchId, shard);
                if (match == null || !GameQueueTypeUtils.isRankedSolo(match.getQueue())) {
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

//     ▄████████ ███▄▄▄▄      ▄████████  ▄█       ▄██   ▄    ▄███████▄     ▄████████
//    ███    ███ ███▀▀▀██▄   ███    ███ ███       ███   ██▄ ██▀     ▄██   ███    ███
//    ███    ███ ███   ███   ███    ███ ███       ███▄▄▄███       ▄███▀   ███    █▀
//    ███    ███ ███   ███   ███    ███ ███       ▀▀▀▀▀▀███  ▀█▀▄███▀▄▄  ▄███▄▄▄
//  ▀███████████ ███   ███ ▀███████████ ███       ▄██   ███   ▄███▀   ▀ ▀▀███▀▀▀
//    ███    ███ ███   ███   ███    ███ ███       ███   ███ ▄███▀         ███    █▄
//    ███    ███ ███   ███   ███    ███ ███▌    ▄ ███   ███ ███▄     ▄█   ███    ███
//    ███    █▀   ▀█   █▀    ███    █▀  █████▄▄██  ▀█████▀   ▀████████▀   ██████████
//                                      ▀


//     ▄███████▄ ███    █▄     ▄████████    ▄█    █▄
//    ███    ███ ███    ███   ███    ███   ███    ███
//    ███    ███ ███    ███   ███    █▀    ███    ███
//    ███    ███ ███    ███   ███         ▄███▄▄▄▄███▄▄
//  ▀█████████▀  ███    ███ ▀███████████ ▀▀███▀▀▀▀███▀
//    ███        ███    ███          ███   ███    ███
//    ███        ███    ███    ▄█    ███   ███    ███
//   ▄████▀      ████████▀   ▄████████▀    ███    █▀
//

    private static void persistPreviousMatch(String matchId, LeagueShard fallbackShard) {
        LeagueShard shard = MatchUtils.matchShard(matchId, fallbackShard);
        if (matchId == null || matchId.isBlank() || shard == null || MongoDB.hasMatch(matchId)) return;

        LOLMatch match = MatchService.fetch(matchId, shard);
        if (match != null) MatchService.insert(match);
    }

    private static Match trackMatch(LOLMatch source, String referencePuuid, String trackedSummoner, JobPriority priority) {
        if (source == null || source.getPlatform() == null || referencePuuid == null || referencePuuid.isBlank()) return null;
        if (!GameQueueTypeUtils.isRankedSolo(source.getQueue()) || MatchUtils.isRemake(source)) return null;

        String currentFullGameId = MatchUtils.fullGameId(source);
        if (MongoDB.isMatchTracked(currentFullGameId)) return MongoDB.findMatch(currentFullGameId);
        if (MatchService.insert(source) == null) return null;

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
                source.getGameStartTimestamp(),
                currentFullGameId
            );
            Rank current = refreshRank(sourceParticipant.getPuuid(), source.getPlatform(), source.getQueue(), priority);
            RankProgress progress = RankProgressUtils.withPrevious(source.getQueue(),
                    RankProgressUtils.snapshot(current), previous == null ? null : previous.rankProgress);
            participant.rankProgress = progress;
            ranks.add(progress.rank);
        }

        match.rank = TierDivisionUtils.getAverageRank(ranks);
        if (!MongoDB.upsertMatch(currentFullGameId, match, true)) return null;
        MatchService.invalidate(match.gameId, match.leagueShard);
        if (trackedSummoner != null && !trackedSummoner.isBlank())
            BotLogger.info("[LPTracker] Pushed match data for " + trackedSummoner + " (" + referencePuuid + ")");
        return match;
    }

    private static Rank refreshRank(String puuid, LeagueShard shard, GameQueueType queue, JobPriority priority) {
        Map<GameQueueType, Rank> ranks = RankService.refreshSync(puuid, shard, priority);
        Rank rank = findRank(ranks, queue);
        return rank == null ? Rank.unranked() : rank;
    }

    private static Rank findRank(Map<GameQueueType, Rank> ranks, GameQueueType queue) {
        GameQueueType canonicalQueue = GameQueueTypeUtils.canonicalQueue(queue);
        return ranks == null ? null : ranks.get(canonicalQueue);
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
        participant.championLevel = source.getChampionLevel();
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
                            RankService.saveEntry(shard, entry);
                            Summoner summoner = SummonerService.getRiotSummoner(entry.getPuuid(), shard);
                            List<String> matchIds = new ArrayList<>();
                            for (int start = 0; matchIds.size() == start; start += 100) {
                                matchIds.addAll(MatchService.getMatchlist(
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
                            LOLMatch match = MatchService.fetch(me.matchId(), me.summoner().getPlatform());
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
                            Match persisted = MatchService.insert(match);
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

}
