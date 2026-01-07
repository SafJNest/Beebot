package com.safjnest.mongodb;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import static org.bson.codecs.configuration.CodecRegistries.*;


import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.safjnest.App;
import com.safjnest.util.SettingsLoader;

public class MongoManager {

  private static MongoClient mongo;
  public static MongoDatabase LOLdb;

  static {
    init();
  }

  private static void init() {
    CodecRegistry pojoCodecRegistry = fromRegistries(
      MongoClientSettings.getDefaultCodecRegistry(),
      fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    ConnectionString connectionString = new ConnectionString(SettingsLoader.getSettings().getJsonSettings().getMongodb());

    MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .codecRegistry(pojoCodecRegistry)
            .build();

    mongo = MongoClients.create(settings);

    loadLOLDb();
  }

  private static void loadLOLDb() { 
    LOLdb = mongo.getDatabase(
      App.isTesting() 
        ? "league_of_legends_test"
        : "league_of_legends"
    );
    setupIndexes();
  }

  private static void setupIndexes() {
    MongoCollection<Document> match = LOLdb.getCollection("match");

    match.createIndex(
        Indexes.ascending("participants.puuid"),
        new IndexOptions().name("participants.puuid_1")
    );

    match.createIndex(
        Indexes.ascending("participants.champion"),
        new IndexOptions().name("participants.champion_1")
    );

    match.createIndex(
        Indexes.ascending("participants.lane"),
        new IndexOptions().name("participants.lane_1")
    );

    match.createIndex(
        Indexes.ascending("region"),
        new IndexOptions().name("region_1")
    );

    match.createIndex(
        Indexes.ascending("rank"),
        new IndexOptions().name("rank_1")
    );

    MongoCollection<Document> summoner = LOLdb.getCollection("summoner");

    summoner.createIndex(
        Indexes.ascending("region"),
        new IndexOptions().name("region_1")
    );

    summoner.createIndex(
        Indexes.ascending("user_id"),
        new IndexOptions().name("user_id_1")
    );

    summoner.createIndex(
        Indexes.ascending("tracking"),
        new IndexOptions().name("tracking_1")
    );
  }

  
}
