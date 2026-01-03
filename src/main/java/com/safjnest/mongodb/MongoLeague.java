package com.safjnest.mongodb;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.MatchData;
import com.safjnest.lol.model.ParticipantData;
import com.safjnest.lol.model.SummonerData;

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


    Document bansDoc = d.get("bansa", Document.class);
    m.bans = new HashMap<>();

    if (bansDoc != null) {
        for (Map.Entry<String, Object> e : bansDoc.entrySet()) {


            TeamType team = TeamType.valueOf(e.getKey());


            @SuppressWarnings("unchecked")
            List<Integer> bannedChampions = (List<Integer>) e.getValue();

            //m.bans.put(team, bannedChampions);
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

    p.lane = LaneType.valueOf(d.getString("lane"));
    p.team = TeamType.valueOf(d.getString("team"));
    p.subTeam = d.getInteger("subTeam", 0);
    p.subTeamPlacement = d.getInteger("subTeamPlacement", 0);
    
    p.rank = TierDivisionType.valueOf(d.getString("rank"));
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
}
