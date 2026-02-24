package com.safjnest.mongodb;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONObject;

import static com.mongodb.client.model.Projections.*;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.model.MatchData;
import com.safjnest.lol.model.ParticipantData;
import com.safjnest.lol.model.SummonerData;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class MongoLeague {

  private static MongoCollection<Document> matchCollection = MongoManager.LOLdb.getCollection("match");
  private static MongoCollection<SummonerData> summonerCollection = MongoManager.LOLdb.getCollection("summoner", SummonerData.class);

  /** Tracking state for a summoner (last game id, rank, lp) used by MongoTracker. */
  public static class SummonerTrackingInfo {
    public final SummonerData summoner;
    public final long gameId;
    public final String rank;
    public final int lp;
    public final long timeStart;

    public SummonerTrackingInfo(SummonerData summoner, long gameId, String rank, int lp, long timeStart) {
      this.summoner = summoner;
      this.gameId = gameId;
      this.rank = rank;
      this.lp = lp;
      this.timeStart = timeStart;
    }
  }

  public static List<SummonerData> getSummonersByUserId(String userId) {
    List<SummonerData> results = summonerCollection
      .find(new org.bson.Document("user_id", userId))
      .sort(Sorts.ascending("id"))
      .into(new ArrayList<>());

    return results;
  }

  public static String getUserIdByPuuid(String puuid, LeagueShard shard) {
    SummonerData summoner = summonerCollection
      .find(new org.bson.Document("puuid", puuid)
        .append("region", shard.name()))
      .projection(fields(include("user_id"), excludeId()))
      .first();

    return summoner != null ? summoner.getUserId() : null;
  }

  public static List<SummonerData> getSummonerWithTracking() {
    List<SummonerData> results = summonerCollection
      .find(new org.bson.Document("tracking", true))
      .into(new ArrayList<>());
    return results;
  }

  public static SummonerData saveSummoner(Summoner summoner, String userId) {
    RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);

    Document filter = new Document("_id", summoner.getPUUID())
        .append("region", summoner.getPlatform().name())
        .append("puuid", summoner.getPUUID());

    Document update = new Document("$set", new Document()
        .append("icon", summoner.getProfileIconId())
        .append("level", summoner.getSummonerLevel())
        .append("riot_id", account != null
            ? account.getName() + "#" + account.getTag()
            : null
        )
    );

    if (userId != null) {
        update.get("$set", Document.class)
            .append("user_id", userId);
    }

    FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
        .upsert(true)
        .returnDocument(ReturnDocument.AFTER);

    return summonerCollection.findOneAndUpdate(filter, update, options);
  }

  public static boolean saveSummoners(SpectatorGameInfo info) {

    List<WriteModel<SummonerData>> bulkOps = new ArrayList<>();

    for (SpectatorParticipant summoner : info.getParticipants()) {
        if (summoner.getPuuid() == null) continue;

        Bson filter = Filters.eq("_id", summoner.getPuuid());

        Bson update = Updates.combine(
            Updates.set("puuid", summoner.getPuuid()),
            Updates.set("riot_id", summoner.getRiotId()),
            Updates.set("region", info.getPlatform().name()),
            Updates.set("icon", summoner.getProfileIconId())
        );

        bulkOps.add(
            new UpdateOneModel<>(
                filter,
                update,
                new UpdateOptions().upsert(true)
            )
        );
    }

    if (bulkOps.isEmpty()) return true;

    BulkWriteResult result = summonerCollection.bulkWrite(bulkOps);
    return result.getModifiedCount() + result.getUpserts().size() == bulkOps.size();
  
  }

  public static boolean saveSummoners(LOLMatch match) {
    List<WriteModel<SummonerData>> bulkOps = new ArrayList<>();

    for (MatchParticipant summoner : match.getParticipants()) {

        Bson filter = Filters.eq("_id", summoner.getPuuid());

        Bson update = Updates.combine(
            Updates.set("puuid", summoner.getPuuid()),
            Updates.set("riot_id",
                summoner.getRiotIdName() + "#" + summoner.getRiotIdTagline()
            ),
            Updates.set("region", match.getPlatform().name()),
            Updates.set("icon", summoner.getProfileIcon()),
            Updates.set("level", summoner.getSummonerLevel())
        );

        bulkOps.add(
            new UpdateOneModel<>(
                filter,
                update,
                new UpdateOptions().upsert(true)
            )
        );
    }

    if (bulkOps.isEmpty()) return true;

    BulkWriteResult result = summonerCollection.bulkWrite(bulkOps);
    return result.getModifiedCount() + result.getUpserts().size() == bulkOps.size();
  }

  public static boolean updateSummonerMasteries(Summoner summoner, List<ChampionMastery> masteries) {
    List<Document> masteryDocs = masteries.stream()
        .map(m -> new Document()
            .append("champion_id", m.getChampionId())
            .append("champion_level", m.getChampionLevel())
            .append("champion_points", m.getChampionPoints())
            .append("last_play_time", new Date(m.getLastPlayTime()))
        )
        .toList();

    summonerCollection.updateOne(
        Filters.eq("_id", summoner.getPUUID()),
        Updates.set("masteries", masteryDocs)
    );

    return true;
  }

  public static boolean updateSummonerEntries(Summoner summoner, List<LeagueEntry> entries) {
    List<Document> rankDocs = entries.stream()
        .map(e -> new Document()
            .append("queue", e.getQueueType().name())
            .append("rank", e.getTierDivisionType().name())
            .append("lp", e.getLeaguePoints())
            .append("wins", e.getWins())
            .append("losses", e.getLosses())
        )
        .toList();

    summonerCollection.updateOne(
        Filters.eq("_id", summoner.getPUUID()),
        Updates.set("rank", rankDocs)
    );

    return true;
  }

  public static boolean trackSummoner(String puuid, boolean tracking) {
    summonerCollection.updateOne(
        Filters.eq("_id", puuid),
        Updates.set("tracking", tracking)
    );

    return true;
  }

  /** Untracks summoner and clears user link (Mongo equivalent of deleteLOLaccount). */
  public static boolean untrackAndUnlinkUser(String userId, String puuid) {
    Bson filter = Filters.and(Filters.eq("user_id", userId), Filters.eq("puuid", puuid));
    summonerCollection.updateOne(filter, Updates.combine(Updates.set("tracking", false), Updates.unset("user_id")));
    return true;
  }


  public static void saveMatch(MatchData match) {

    String id = match.region + "_" + match.gameId;
    Map<String, List<Integer>> bansDoc = new HashMap<>();

    for (Map.Entry<TeamType, List<Integer>> entry : match.bans.entrySet()) {
        bansDoc.put(entry.getKey().name(), entry.getValue());
    }
    Document eventsDoc = Document.parse(match.events.toString());
    
    Document document = new Document("_id", id)
      .append("region", match.region != null ? match.region.name() : null)
      .append("game_id",  match.gameId)
      .append("queue", match.queue)
      .append("rank", match.rank)
      .append("time_start", new Timestamp(match.timeStart))
      .append("time_end", new Timestamp(match.timeEnd))
      .append("patch", match.patch)
      .append("bans", bansDoc)
      .append("events", eventsDoc);

    List<Document> participantsDocs = match.participants.stream()
      .map(p -> {
          Document pDoc = new Document()
              .append("puuid", p.puuid)
              .append("win", p.win)
              .append("kills", p.kills)
              .append("deaths", p.deaths)
              .append("assists", p.assists)
              .append("doubles", p.doubles)
              .append("triples", p.triples)
              .append("quadruples", p.quadruples)
              .append("pentas", p.pentas)
              .append("gain", p.gain)
              .append("champion", p.champion)
              .append("lane", p.lane.name())
              .append("team", p.team.name())
              .append("subTeam", p.subTeam)
              .append("subTeamPlacement", p.subTeamPlacement)
              .append("damage", p.damage)
              .append("damageBuilding", p.damageBuilding)
              .append("healing", p.healing)
              .append("cs", p.cs)
              .append("goldEarned", p.goldEarned)
              .append("ward", p.ward)
              .append("wardKilled", p.wardKilled)
              .append("visionScore", p.visionScore)
              .append("pings", p.pings)
              .append("summoner_spells", List.of(p.summonerSpell1, p.summonerSpell2))
              .append("skill_order", p.skillOrder);

          if (p.rank != null)
              pDoc.append("rank", p.rank.name());
          if (p.getBuildAsJson() != null)
              pDoc.append("build", Document.parse(p.getBuildAsJson().toString()));

          return pDoc;
      }).toList();
    document.append("participants", participantsDocs);

    matchCollection.replaceOne(
      new Document("_id", id),
      document,
      new ReplaceOptions().upsert(true)
    );
  }

  public static List<MatchData> getMatches() {
    List<MatchData> matches = new ArrayList<>();

    FindIterable<Document> docs = matchCollection.find(
        Filters.eq("participants.puuid", "qwf0lHM8o9ZrlWuyVwmNnz5RZwuE_z9SdWCGwOJ5Ypi5-zNapWjTRgKl08HH0XjNS0XZ0yzfRQJApA")
    );

    for (Document d : docs) {
        matches.add(parseMatch(d));
    }

    return matches;
  }



  private static MatchData parseMatch(Document d) {
    MatchData m = new MatchData();

    m.gameId = d.getString("game_id");


    m.region = LeagueShard.valueOf(d.getString("region"));
    m.queue = GameQueueType.valueOf(d.getString("queue"));


    m.timeStart = d.getDate("time_start").getTime();
    m.timeEnd = d.getDate("time_end").getTime();

    m.patch = d.getString("patch");
    if (d.getString("rank") != null) {
      try {
        m.rank = no.stelar7.api.r4j.basic.constants.types.lol.TierType.valueOf(d.getString("rank"));
      } catch (Exception ignored) {}
    }

    Document bansDoc = d.get("bans", Document.class);
    m.bans = new HashMap<>();

    if (bansDoc != null) {
        for (Map.Entry<String, Object> e : bansDoc.entrySet()) {
            TeamType team = TeamType.valueOf(e.getKey());
            @SuppressWarnings("unchecked")
            List<Integer> bannedChampions = (List<Integer>) e.getValue();
            m.bans.put(team, bannedChampions);
        }
    }


    Document eventsDoc = d.get("events", Document.class);
    if (eventsDoc != null) {
      m.events = new JSONObject(eventsDoc);
    }


    List<Document> parts = d.getList("participants", Document.class);
    if (parts != null) {
      m.participants = parts.stream()
          .map(MongoLeague::parseParticipant)
          .toList();
    }

    return m;
  }

  private static ParticipantData parseParticipant(Document d) {
    ParticipantData p = new ParticipantData();

    p.puuid = d.getString("puuid");

    p.win = d.getBoolean("win", false);

    p.kda = d.getString("kda");
    p.kills = d.getInteger("kills", 0);
    p.deaths = d.getInteger("deaths", 0);
    p.assists = d.getInteger("assists", 0);
    p.doubles = d.getInteger("doubles", 0);
    p.triples = d.getInteger("triples", 0);
    p.quadruples = d.getInteger("quadruples", 0);
    p.pentas = d.getInteger("pentas", 0);
    
    
    p.champion = d.getInteger("champion", 0);

    String laneStr = d.getString("lane");
    String teamStr = d.getString("team");
    if (laneStr != null) {
      try { p.lane = LaneType.valueOf(laneStr); } catch (Exception ignored) {}
    }
    if (teamStr != null) {
      try { p.team = TeamType.valueOf(teamStr); } catch (Exception ignored) {}
    }
    p.subTeam = d.getInteger("subTeam", 0);
    p.subTeamPlacement = d.getInteger("subTeamPlacement", 0);
    String rankStr = d.getString("rank");
    if (rankStr != null) p.rank = TierDivisionType.valueOf(rankStr);
    p.gain = d.getInteger("gain", 0);
    
    p.damage = d.getInteger("damage", 0);
    p.damageBuilding = d.getInteger("damageBuilding", 0);
    p.healing = d.getInteger("healing", 0);

    p.cs = d.getInteger("cs", 0);
    p.goldEarned = d.getInteger("goldEarned", 0);

    p.ward = d.getInteger("ward", 0);
    p.wardKilled = d.getInteger("wardKilled", 0);
    p.visionScore = d.getInteger("visionScore", 0);

    Document pingsDoc = d.get("pings", Document.class);
    p.pings = new HashMap<>();

    if (pingsDoc != null) {
        for (Map.Entry<String, Object> e : pingsDoc.entrySet()) {
            p.pings.put(
                e.getKey(),
                ((Number) e.getValue()).intValue()
            );
        }
    }


    List<Integer> spells = d.getList("summoner_spells", Integer.class);
    if (spells != null) {
      p.summonerSpell1 = spells.get(0);
      p.summonerSpell2 = spells.get(1);
    }

    p.skillOrder = d.getList("skill_order", Integer.class, new ArrayList<>());



    p.setBuild(d.get("build", Document.class));

    return p;
  }

  /**
   * Returns the last solo ranked game state for a puuid (game_id, rank, lp) since split start, or null if none.
   */
  public static SummonerTrackingInfo getLastGameState(String puuid, long splitStart) {
    if (puuid == null) return null;
    Bson filter = Filters.and(
        Filters.eq("participants.puuid", puuid),
        Filters.gte("time_start", new Date(splitStart)),
        Filters.eq("queue", GameQueueType.TEAM_BUILDER_RANKED_SOLO.name())
    );
    Document latestMatch = matchCollection.find(filter)
        .sort(Sorts.descending("time_start"))
        .first();
    if (latestMatch == null) return null;
    long gameId = Long.parseLong(latestMatch.getString("game_id"));
    long timeStart = latestMatch.getDate("time_start") != null ? latestMatch.getDate("time_start").getTime() : 0;
    String rank = null;
    int lp = 0;
    List<Document> parts = latestMatch.getList("participants", Document.class);
    if (parts != null) {
      for (Document p : parts) {
        if (puuid.equals(p.getString("puuid"))) {
          rank = p.getString("rank");
          lp = p.getInteger("lp", 0);
          break;
        }
      }
    }
    return new SummonerTrackingInfo(null, gameId, rank, lp, timeStart);
  }

  /**
   * Returns summoners with tracking enabled and their last solo ranked game state (game_id, rank, lp) since splitStart.
   */
  public static List<SummonerTrackingInfo> getSummonerWithTracking(long splitStart) {
    List<SummonerData> summoners = getSummonerWithTracking();
    List<SummonerTrackingInfo> result = new ArrayList<>();
    Date splitDate = new Date(splitStart);
    for (SummonerData s : summoners) {
      String puuid = s.get_id() != null ? s.get_id() : s.getPuuid();
      if (puuid == null) continue;
      Bson matchFilter = Filters.and(
          Filters.eq("participants.puuid", puuid),
          Filters.gte("time_start", splitDate),
          Filters.eq("queue", GameQueueType.TEAM_BUILDER_RANKED_SOLO.name())
      );
      Document latestMatch = matchCollection.find(matchFilter)
          .sort(Sorts.descending("time_start"))
          .first();
      long gameId = 0;
      String rank = null;
      int lp = 0;
      long timeStart = 0;
      if (latestMatch != null) {
        gameId = Long.parseLong(latestMatch.getString("game_id"));
        timeStart = latestMatch.getDate("time_start") != null ? latestMatch.getDate("time_start").getTime() : 0;
        List<Document> parts = latestMatch.getList("participants", Document.class);
        if (parts != null) {
          for (Document p : parts) {
            if (puuid.equals(p.getString("puuid"))) {
              rank = p.getString("rank");
              lp = p.getInteger("lp", 0);
              break;
            }
          }
        }
      }
      result.add(new SummonerTrackingInfo(s, gameId, rank, lp, timeStart));
    }
    return result;
  }

  /**
   * Match history for a summoner (puuid + region) with optional filters.
   */
  public static List<MatchData> getMatchHistory(String puuid, LeagueShard region, LeagueMessageParameter parameter) {
    Bson filter = Filters.eq("participants.puuid", puuid);
    if (parameter.getTimeStart() > 0) {
      filter = Filters.and(filter, Filters.gte("time_start", new Date(parameter.getTimeStart())));
    }
    if (parameter.getTimeEnd() > 0) {
      filter = Filters.and(filter, Filters.lte("time_end", new Date(parameter.getTimeEnd())));
    }
    if (parameter.getQueueType() != null) {
      filter = Filters.and(filter, Filters.eq("queue", parameter.getQueueType().name()));
    }
    List<Bson> participantConds = new ArrayList<>();
    participantConds.add(Filters.eq("puuid", puuid));
    if (parameter.getShowingChampion() > 0) {
      participantConds.add(Filters.eq("champion", parameter.getShowingChampion()));
    }
    if (parameter.getLaneType() != null && parameter.getQueueType() != GameQueueType.CHERRY) {
      participantConds.add(Filters.eq("lane", parameter.getLaneType().name()));
    }
    if (participantConds.size() > 1) {
      filter = Filters.and(filter, Filters.elemMatch("participants", Filters.and(participantConds)));
    }
    int pageItem = parameter.getMessageType().getPageItem();
    int limit = (parameter.getMessageType() == LeagueMessageType.OVERVIEW_OPGG && pageItem > 0)
        ? pageItem
        : 100;
    int offset = parameter.getOffset();
    List<Document> docs = matchCollection.find(filter)
        .sort(Sorts.descending("time_start"))
        .skip(offset)
        .limit(limit)
        .into(new ArrayList<>());
    List<MatchData> matches = new ArrayList<>();
    for (Document d : docs) {
      matches.add(parseMatch(d));
    }
    return matches;
  }

  public static int countMatchHistory(String puuid, LeagueShard region, LeagueMessageParameter parameter) {
    Bson filter = Filters.eq("participants.puuid", puuid);
    if (parameter.getTimeStart() > 0) {
      filter = Filters.and(filter, Filters.gte("time_start", new Date(parameter.getTimeStart())));
    }
    if (parameter.getTimeEnd() > 0) {
      filter = Filters.and(filter, Filters.lte("time_end", new Date(parameter.getTimeEnd())));
    }
    if (parameter.getQueueType() != null) {
      filter = Filters.and(filter, Filters.eq("queue", parameter.getQueueType().name()));
    }
    List<Bson> participantConds = new ArrayList<>();
    participantConds.add(Filters.eq("puuid", puuid));
    if (parameter.getShowingChampion() > 0) {
      participantConds.add(Filters.eq("champion", parameter.getShowingChampion()));
    }
    if (parameter.getLaneType() != null && parameter.getQueueType() != GameQueueType.CHERRY) {
      participantConds.add(Filters.eq("lane", parameter.getLaneType().name()));
    }
    if (participantConds.size() > 1) {
      filter = Filters.and(filter, Filters.elemMatch("participants", Filters.and(participantConds)));
    }
    return (int) matchCollection.countDocuments(filter);
  }

  /**
   * Summoner game history as QueryResult for compatibility (game_id, queue, win, time_start, time_end, patch, rank, lp, gain).
   */
  public static QueryResult getSummonerData(String puuid, LeagueShard region) {
    Bson filter = Filters.eq("participants.puuid", puuid);
    List<Document> docs = matchCollection.find(filter)
        .sort(Sorts.descending("time_start"))
        .into(new ArrayList<>());
    QueryResult result = new QueryResult();
    for (Document d : docs) {
      String gameId = d.getString("game_id");
      String queue = d.getString("queue");
      Date ts = d.getDate("time_start");
      Date te = d.getDate("time_end");
      String patch = d.getString("patch");
      List<Document> parts = d.getList("participants", Document.class);
      if (parts == null) continue;
      for (Document p : parts) {
        if (!puuid.equals(p.getString("puuid"))) continue;
        QueryRecord rec = new QueryRecord(null);
        rec.put("game_id", gameId);
        rec.put("queue", queue);
        rec.put("win", p.getBoolean("win", false) ? "1" : "0");
        rec.put("time_start", ts != null ? new Timestamp(ts.getTime()).toString() : "");
        rec.put("time_end", te != null ? new Timestamp(te.getTime()).toString() : "");
        rec.put("patch", patch != null ? patch : "");
        rec.put("rank", p.getString("rank"));
        rec.put("lp", String.valueOf(p.getInteger("lp", 0)));
        rec.put("gain", String.valueOf(p.getInteger("gain", 0)));
        result.add(rec);
        break;
      }
    }
    return result;
  }

  public static QueryResult getAllGamesForAccount(String puuid, LeagueShard region, long timeStart, long timeEnd) {
    Bson filter = Filters.eq("participants.puuid", puuid);
    if (timeStart > 0) filter = Filters.and(filter, Filters.gte("time_start", new Date(timeStart)));
    if (timeEnd > 0) filter = Filters.and(filter, Filters.lte("time_end", new Date(timeEnd)));
    List<Document> docs = matchCollection.find(filter)
        .sort(Sorts.descending("time_start"))
        .into(new ArrayList<>());
    QueryResult result = new QueryResult();
    for (Document d : docs) {
      String gameId = d.getString("game_id");
      String queue = d.getString("queue");
      List<Document> parts = d.getList("participants", Document.class);
      if (parts == null) continue;
      for (Document p : parts) {
        if (!puuid.equals(p.getString("puuid"))) continue;
        QueryRecord rec = new QueryRecord(null);
        rec.put("game_id", gameId);
        rec.put("queue", queue);
        rec.put("win", p.getBoolean("win", false) ? "1" : "0");
        result.add(rec);
        break;
      }
    }
    return result;
  }

  /**
   * Advanced LOL stats by champion (games, wins, losses, avg_kills, avg_deaths, avg_assists, total_lp_gain, lanes_played).
   */
  public static QueryResult getAdvancedLOLData(String puuid, LeagueShard region, long timeStart, long timeEnd, GameQueueType queue) {
    Bson filter = Filters.eq("participants.puuid", puuid);
    if (timeStart > 0) filter = Filters.and(filter, Filters.gte("time_start", new Date(timeStart)));
    if (timeEnd > 0) filter = Filters.and(filter, Filters.lte("time_end", new Date(timeEnd)));
    if (queue != null) filter = Filters.and(filter, Filters.eq("queue", queue.name()));
    List<Document> docs = matchCollection.find(filter).into(new ArrayList<>());
    Map<Integer, int[]> byChamp = new LinkedHashMap<>();
    Map<Integer, double[]> sums = new LinkedHashMap<>();
    Map<Integer, Map<String, int[]>> laneStatsByChamp = new LinkedHashMap<>();
    for (Document d : docs) {
      List<Document> parts = d.getList("participants", Document.class);
      if (parts == null) continue;
      for (Document p : parts) {
        if (!puuid.equals(p.getString("puuid"))) continue;
        int champion = p.getInteger("champion", 0);
        String lane = p.getString("lane");
        if (lane == null) lane = "NONE";
        boolean win = p.getBoolean("win", false);
        int kills = p.getInteger("kills", 0);
        int deaths = p.getInteger("deaths", 0);
        int assists = p.getInteger("assists", 0);
        int gain = p.getInteger("gain", 0);
        byChamp.putIfAbsent(champion, new int[]{0, 0});
        int[] c = byChamp.get(champion);
        c[0]++;
        c[1] += win ? 1 : 0;
        sums.putIfAbsent(champion, new double[]{0, 0, 0, 0});
        double[] s = sums.get(champion);
        s[0] += kills;
        s[1] += deaths;
        s[2] += assists;
        s[3] += gain;
        laneStatsByChamp.putIfAbsent(champion, new LinkedHashMap<>());
        Map<String, int[]> laneStats = laneStatsByChamp.get(champion);
        laneStats.putIfAbsent(lane, new int[]{0, 0});
        int[] l = laneStats.get(lane);
        if (win) l[0]++; else l[1]++;
        break;
      }
    }
    QueryResult result = new QueryResult();
    for (Map.Entry<Integer, int[]> e : byChamp.entrySet()) {
      int champion = e.getKey();
      int[] c = e.getValue();
      int games = c[0];
      int wins = c[1];
      double[] s = sums.get(champion);
      double avgK = games == 0 ? 0 : s[0] / games;
      double avgD = games == 0 ? 0 : s[1] / games;
      double avgA = games == 0 ? 0 : s[2] / games;
      int totalLp = (int) (s != null ? s[3] : 0);
      Map<String, int[]> laneStats = laneStatsByChamp.get(champion);
      StringBuilder lanesPlayed = new StringBuilder();
      if (laneStats != null) {
        for (Map.Entry<String, int[]> le : laneStats.entrySet()) {
          if (lanesPlayed.length() > 0) lanesPlayed.append(", ");
          lanesPlayed.append(le.getKey()).append("-").append(le.getValue()[0]).append("-").append(le.getValue()[1]);
        }
      }
      QueryRecord rec = new QueryRecord(null);
      rec.put("champion", String.valueOf(champion));
      rec.put("games", String.valueOf(games));
      rec.put("wins", String.valueOf(wins));
      rec.put("losses", String.valueOf(games - wins));
      rec.put("avg_kills", String.format("%.2f", avgK));
      rec.put("avg_deaths", String.format("%.2f", avgD));
      rec.put("avg_assists", String.format("%.2f", avgA));
      rec.put("total_lp_gain", String.valueOf(totalLp));
      rec.put("lanes_played", lanesPlayed.toString());
      result.add(rec);
    }
    return result;
  }

  public static boolean hasSummonerData(String puuid, LeagueShard region) {
    return matchCollection.countDocuments(Filters.eq("participants.puuid", puuid)) > 0;
  }

  /** Returns true if this match is already stored. */
  public static boolean matchExists(LeagueShard region, long gameId) {
    String id = region.name() + "_" + gameId;
    return matchCollection.countDocuments(Filters.eq("_id", id)) > 0;
  }

  /**
   * Champion stats (bans, picks, wins, losses, winrate, banrate, pickrate) from match collection.
   */
  public static HashMap<String, String> analyzeChampionData(int championId, LaneType lane) {
    List<Document> matchDocs = matchCollection.find().into(new ArrayList<>());
    int totalGames = matchDocs.size();
    int totalBans = 0;
    int totalPicks = 0;
    int totalWins = 0;
    for (Document d : matchDocs) {
      Document bansDoc = d.get("bans", Document.class);
      if (bansDoc != null) {
        for (Object list : bansDoc.values()) {
          if (list instanceof List) {
            for (Object id : (List<?>) list) {
              if (id instanceof Number && ((Number) id).intValue() == championId) totalBans++;
            }
          }
        }
      }
      List<Document> parts = d.getList("participants", Document.class);
      if (parts != null) {
        for (Document p : parts) {
          if (p.getInteger("champion", 0) != championId) continue;
          String pLane = p.getString("lane");
          if (lane != null && (pLane == null || !LaneType.valueOf(pLane).equals(lane))) continue;
          totalPicks++;
          if (p.getBoolean("win", false)) totalWins++;
        }
      }
    }
    int totalLosses = totalPicks - totalWins;
    double winrate = totalPicks == 0 ? 0 : (double) totalWins / totalPicks * 100;
    double banrate = totalGames == 0 ? 0 : (double) totalBans / totalGames * 100;
    double pickrate = totalGames == 0 ? 0 : (double) totalPicks / totalGames * 100;
    HashMap<String, String> result = new HashMap<>();
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
