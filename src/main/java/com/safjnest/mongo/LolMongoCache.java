package com.safjnest.mongo;

public final class LolMongoCache {

    private static SummonerMongoRepository summoners;
    private static MatchMongoRepository matches;

    private LolMongoCache() {}

    public static synchronized SummonerMongoRepository summoners() {
        if (summoners == null) summoners = new SummonerMongoRepository();
        return summoners;
    }

    public static synchronized MatchMongoRepository matches() {
        if (matches == null) matches = new MatchMongoRepository();
        return matches;
    }

    public static void ensureIndexes() {
        summoners().ensureIndexes();
        matches().ensureIndexes();
    }

    public static synchronized void close() {
        summoners = null;
        matches = null;
        MongoConnection.close();
    }
}
