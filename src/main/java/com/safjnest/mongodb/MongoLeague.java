package com.safjnest.mongodb;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import static com.mongodb.client.model.Projections.*;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.safjnest.util.lol.model.SummonerData;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class MongoLeague {

  private static MongoCollection<Document> matchCollection = MongoManager.LOLdb.getCollection("summoner");
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
  
}
