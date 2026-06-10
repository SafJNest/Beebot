package com.safjnest.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public final class MongoConnection {

    private static MongoClient client;
    private static MongoDatabase database;

    private MongoConnection() {}

    public static synchronized MongoDatabase getDatabase() {
        if (database != null) return database;

        client = MongoClients.create(MongoSettings.getUri());
        database = client.getDatabase(MongoSettings.getDatabase());
        return database;
    }

    public static synchronized void close() {
        if (client != null) client.close();
        client = null;
        database = null;
    }
}
