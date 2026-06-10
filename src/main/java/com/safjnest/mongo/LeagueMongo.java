package com.safjnest.mongo;

import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.safjnest.lol.model.SummonerDTO;
import com.safjnest.mongo.codec.SummonerDocumentCodec;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class LeagueMongo {

    private static final String SUMMONERS = "lol_summoners";
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private static MongoClient client;
    private static MongoDatabase database;
    private static MongoCollection<Document> summoners;

    private LeagueMongo() {}

    public static SummonerDTO getSummoner(String puuid, LeagueShard region) {
        if (puuid == null || region == null) return null;
        Document document = summoners().find(
            eq("_id", SummonerDocumentCodec.id(puuid, region))
        ).first();
        return SummonerDocumentCodec.decode(document);
    }

    public static void saveSummoner(SummonerDTO summoner) {
        if (summoner == null || summoner.getRegion() == null) return;
        String id = SummonerDocumentCodec.id(summoner.getPuuid(), summoner.getRegion());
        summoners().replaceOne(eq("_id", id), SummonerDocumentCodec.encode(summoner), UPSERT);
    }

    public static boolean deleteSummoner(String puuid, LeagueShard region) {
        if (puuid == null || region == null) return false;
        return summoners().deleteOne(
            eq("_id", SummonerDocumentCodec.id(puuid, region))
        ).getDeletedCount() > 0;
    }

    public static void ensureIndexes() {
        summoners().createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("gameName"),
                Indexes.ascending("tagLine"),
                Indexes.ascending("region")
            ),
            new IndexOptions().name("riot_id_region")
        );
        summoners().createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("tracking"),
                Indexes.ascending("region")
            ),
            new IndexOptions().name("tracking_region")
        );
    }

    public static synchronized void close() {
        if (client != null) client.close();
        client = null;
        database = null;
        summoners = null;
    }

    private static synchronized MongoCollection<Document> summoners() {
        if (summoners == null) summoners = database().getCollection(SUMMONERS);
        return summoners;
    }

    private static synchronized MongoDatabase database() {
        if (database != null) return database;
        client = MongoClients.create(MongoSettings.getUri());
        database = client.getDatabase(MongoSettings.getDatabase());
        return database;
    }
}
