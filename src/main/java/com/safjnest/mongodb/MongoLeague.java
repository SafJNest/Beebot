package com.safjnest.mongodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.json.JSONObject;

import static com.mongodb.client.model.Projections.*;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.safjnest.lol.model.MatchData;
import com.safjnest.lol.model.ParticipantData;
import com.safjnest.lol.model.SummonerData;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

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


  public static List<MatchData> getMatches() {
    List<MatchData> matches = new ArrayList<>();

    Document d = matchCollection
            .find(new Document("_id", "EUW1_7644912818"))
            .first();

    matches.add(parseMatch(d));

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

    p.win = d.getBoolean("win", false);

    p.kda = d.getString("kda");
    p.champion = d.getInteger("champion", 0);

    p.lane = LaneType.valueOf(d.getString("lane"));
    p.team = TeamType.valueOf(d.getString("team"));
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

    p.subTeam = d.getInteger("subTeam", 0);
    p.subTeamPlacement = d.getInteger("subTeamPlacement", 0);
    p.puuid = d.getString("puuid");

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
