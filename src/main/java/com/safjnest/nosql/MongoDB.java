package com.safjnest.nosql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.App;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionIndexable;
import com.safjnest.lol.model.ProfileIndexable;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionTierList;
import com.safjnest.lol.model.ChampionTierSource;
import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.MatchMemoryUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.RankProgressUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.utils.JsonCodec;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryRecordParser;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;

public final class MongoDB {

    private static final int MAX_SEARCH_RESULTS = 25;
    private static final int MAX_BATCH_IDS = 2_000;
    private static final int EXISTS_QUERY_BATCH_SIZE = 2_000;
    private static final int RANK_PROGRESS_SCHEMA_PAGE_SIZE = 10_000;
    private static final int RANK_PROGRESS_HISTORY_BULK_SIZE = 1_000;
    private static final int COMPETITIVE_REBUILD_BATCH_SIZE = 250;
    private static final int PROFILE_RECORD_MATCH_BATCH_SIZE = 250;
    private static final int AI_TRAINING_CURSOR_BATCH_SIZE = 10_000;
    private static final String EVENTS_STORAGE_ENGINE_CONFIG = "block_compressor=zstd";
    private static final String LEADERBOARD_AGGREGATES_COLLECTION = "leaderboard_aggregates";
    private static final String COMPETITIVE_COLLECTION = "competitive";
    private static final String GLOBAL_LEADERBOARD_REGION = "GLOBAL";
    private static final String PAGE_COUNT_AGGREGATE = "page-count";
    private static final String RANK_DISTRIBUTION_AGGREGATE = "rank-distribution";
    private static final String TOP_REGIONS_AGGREGATE = "top-regions";
    private static final String CHAMPION_INDEXABLES_COLLECTION = "champions_indexable";
    private static final String PROFILE_INDEXABLES_COLLECTION = "profiles_indexable";
    private static final String PROFILE_RECORDS_COLLECTION = "profile_records";
    private static final List<String> INDEXABLE_PROFILE_RANKS = List.of(
            TierDivisionType.MASTER_I.name(),
            TierDivisionType.GRANDMASTER_I.name(),
            TierDivisionType.CHALLENGER_I.name());
    private static final List<GameQueueType> LEADERBOARD_QUEUES = List.of(
            GameQueueType.RANKED_SOLO_5X5,
            GameQueueType.RANKED_FLEX_SR);
    private static final List<String> AI_TRAINING_QUEUES = List.of(
            GameQueueType.RANKED_SOLO_5X5.name(),
            GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(),
            GameQueueType.JADE_RANKED_SOLO_5X5.name(),
            GameQueueType.RANKED_FLEX_SR.name(),
            GameQueueType.TEAM_BUILDER_DRAFT_RANKED_5X5.name(),
            GameQueueType.NORMAL_5X5_DRAFT.name(),
            GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5.name());
    private static final List<String> COLLECTION_NAMES = List.of(
            "summoner", "match", "match_events", "profile_statistics", "champion",
            "champion_builds", "champion_stats", "profile_activity", "profile_matchups", PROFILE_RECORDS_COLLECTION, LEADERBOARD_AGGREGATES_COLLECTION,
            COMPETITIVE_COLLECTION,
            CHAMPION_INDEXABLES_COLLECTION, PROFILE_INDEXABLES_COLLECTION, "migration_runs");
    private static void ensureCollections(MongoDatabase database) {
        List<String> existing = database.listCollectionNames().into(new ArrayList<>());
        for (String name : COLLECTION_NAMES) if (!existing.contains(name)) {
            try {
                database.createCollection(name, collectionOptions(name));
            } catch (MongoCommandException exception) {
                if (exception.getCode() != 48 && !"NamespaceExists".equals(exception.getErrorCodeName())) throw exception;
            }
        }
    }

    private static CreateCollectionOptions collectionOptions(String collection) {
        if (!"match_events".equals(collection)) return new CreateCollectionOptions();
        return new CreateCollectionOptions().storageEngineOptions(new Document("wiredTiger", new Document("configString", EVENTS_STORAGE_ENGINE_CONFIG)));
    }

    public static final String PRODUCTION_DATABASE = "beebot";
    public static final String TEST_DATABASE = "beebot_test";
    private static final String MONGO_URI_ERROR = "Mongo URI is missing from settings.json";
    private static MongoClient client;
    private static MongoDatabase database;
    private static boolean collectionsReady;

    private MongoDB() {
    }

    public static synchronized MongoDatabase getDatabase() {
        if (database == null) {
            database = getClient().getDatabase(App.isTesting() ? TEST_DATABASE : PRODUCTION_DATABASE);
        }
        if (!collectionsReady) {
            ensureCollections(database);
            collectionsReady = true;
        }
        return database;
    }

    public static MongoDatabase initialize() {
        return getDatabase();
    }

    public static String getDatabaseName() {
        return getDatabaseName(App.isTesting());
    }

    public static String getDatabaseName(boolean testing) {
        return testing ? TEST_DATABASE : PRODUCTION_DATABASE;
    }

    public static MongoDatabase database() {
        return getDatabase();
    }

    public static synchronized MongoClient getClient() {
        if (client == null) {
            String uri = SettingsLoader.getSettings().getJsonSettings().getMongo();
            if (uri == null || uri.isBlank()) throw new IllegalStateException(MONGO_URI_ERROR);
            ConnectionString connection = new ConnectionString(uri);
            if (connection.getDatabase() != null && !connection.getDatabase().isBlank()) {
                throw new IllegalStateException("Mongo URI must not select an application database");
            }
            client = MongoClients.create(MongoClientSettings.builder()
                    .applyConnectionString(connection)
                    .addCommandListener(MongoCommandMonitor.listener())
                    .build());
        }
        return client;
    }

    public static synchronized void close() {
        if (client != null) client.close();
        client = null;
        database = null;
        collectionsReady = false;
    }

    public static long commandCount() {
        MongoCommandMonitor.ClientOpcounters counters = MongoCommandMonitor.clientOpcounters();
        return counters.insert() + counters.query() + counters.update()
                + counters.delete() + counters.command() + counters.getmore();
    }

    public static MongoServerStatusSnapshot serverStatusSnapshot() {
        try {
            Document response = getClient().getDatabase("admin").runCommand(new Document("serverStatus", 1)
                    .append("opcounters", 1)
                    .append("connections", 1)
                    .append("mem", 1));
            if (response == null) return null;
            Document opcounters = response.get("opcounters", Document.class);
            Document connections = response.get("connections", Document.class);
            Document mem = response.get("mem", Document.class);
            if (opcounters == null) return null;
            MongoCommandMonitor.ClientOpcounters counters = new MongoCommandMonitor.ClientOpcounters(
                    number(opcounters, "insert"),
                    number(opcounters, "query"),
                    number(opcounters, "update"),
                    number(opcounters, "delete"),
                    number(opcounters, "command"),
                    number(opcounters, "getmore"));
            Long currentConnections = connections == null ? null : number(connections, "current");
            Long residentMb = mem == null ? null : number(mem, "resident");
            Long virtualMb = mem == null ? null : number(mem, "virtual");
            return new MongoServerStatusSnapshot(counters, currentConnections, residentMb, virtualMb);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static Document collectionStats(String collection) {
        if (collection == null || collection.isBlank()) throw new IllegalArgumentException("Mongo collection is required");
        return database().runCommand(new Document("collStats", collection).append("scale", 1));
    }

    public static Document spaceAudit(int sampleSize) {
        int boundedSample = Math.max(1, Math.min(10_000, sampleSize));
        MongoCollection<Document> collection = summoners();
        List<Document> samples = collection.aggregate(List.of(
                new Document("$sample", new Document("size", boundedSample)),
                new Document("$project", new Document("bsonBytes", new Document("$bsonSize", "$$ROOT"))
                        .append("hasUserId", new Document("$cond", List.of(new Document("$ne", Arrays.asList("$userId", null)), 1, 0)))
                        .append("isTracking", new Document("$cond", List.of(new Document("$eq", List.of("$tracking", true)), 1, 0)))
                        .append("masteriesBytes", new Document("$bsonSize", new Document("masteries", "$masteries")))
                        .append("region", 1))))
                .into(new ArrayList<>());
        List<Integer> sizes = new ArrayList<>();
        List<Integer> masteriesSizes = new ArrayList<>();
        Map<String, Integer> regions = new LinkedHashMap<>();
        long totalBytes = 0;
        int withUserId = 0;
        int tracking = 0;
        for (Document sample : samples) {
            int bytes = sample.getInteger("bsonBytes", 0);
            sizes.add(bytes);
            masteriesSizes.add(sample.getInteger("masteriesBytes", 0));
            totalBytes += bytes;
            withUserId += sample.getInteger("hasUserId", 0);
            tracking += sample.getInteger("isTracking", 0);
            String region = sample.getString("region");
            if (region != null) regions.merge(region, 1, Integer::sum);
        }
        Collections.sort(sizes);
        Collections.sort(masteriesSizes);
        int p95Index = sizes.isEmpty() ? 0 : Math.min(sizes.size() - 1, (int) Math.ceil(sizes.size() * 0.95D) - 1);
        int masteriesP95Index = masteriesSizes.isEmpty() ? 0 : Math.min(masteriesSizes.size() - 1, (int) Math.ceil(masteriesSizes.size() * 0.95D) - 1);
        long totalMasteriesBytes = 0;
        for (int size : masteriesSizes) totalMasteriesBytes += size;
        Document sample = new Document("documents", sizes.size())
                .append("totalBsonBytes", totalBytes)
                .append("averageBsonBytes", sizes.isEmpty() ? 0D : (double) totalBytes / sizes.size())
                .append("p95BsonBytes", sizes.isEmpty() ? 0 : sizes.get(p95Index))
                .append("maxBsonBytes", sizes.isEmpty() ? 0 : sizes.get(sizes.size() - 1))
                .append("averageMasteriesBsonBytes", masteriesSizes.isEmpty() ? 0D : (double) totalMasteriesBytes / masteriesSizes.size())
                .append("p95MasteriesBsonBytes", masteriesSizes.isEmpty() ? 0 : masteriesSizes.get(masteriesP95Index))
                .append("withUserId", withUserId)
                .append("tracking", tracking)
                .append("regions", regions);
        Document stats = collectionStats("summoner");
        return new Document("summoner", stats).append("sample", sample).append("indexSizes", stats.get("indexSizes", new Document()));
    }

    public static Document matchEventsSpaceAudit(int sampleSize) {
        int boundedSample = Math.max(1, Math.min(10_000, sampleSize));
        List<Document> samples = matchEvents().find().limit(boundedSample).into(new ArrayList<>());
        List<Integer> payloadBytes = new ArrayList<>();
        for (Document document : samples) {
            String data = document.getString("data");
            payloadBytes.add(data == null ? 0 : data.getBytes(StandardCharsets.UTF_8).length);
        }
        Collections.sort(payloadBytes);
        int p95Index = payloadBytes.isEmpty() ? 0 : Math.min(payloadBytes.size() - 1, (int) Math.ceil(payloadBytes.size() * 0.95D) - 1);
        long totalPayloadBytes = 0;
        for (int size : payloadBytes) totalPayloadBytes += size;
        return new Document("stats", collectionStats("match_events"))
                .append("sampleDocuments", payloadBytes.size())
                .append("averagePayloadBytes", payloadBytes.isEmpty() ? 0D : (double) totalPayloadBytes / payloadBytes.size())
                .append("p95PayloadBytes", payloadBytes.isEmpty() ? 0 : payloadBytes.get(p95Index));
    }

    public static QueryRecord findRecord(String collection, Object id) {
        Document document = database().getCollection(collection).find(Filters.eq("_id", id)).first();
        return document == null ? null : QueryRecordParser.fromDocument(document);
    }

    public static Set<String> findExistingIds(String collection, List<String> ids) {
        Set<String> existing = new HashSet<>();
        if (collection == null || collection.isBlank() || ids == null || ids.isEmpty()) return existing;
        MongoCollection<Document> target = database().getCollection(collection);
        for (int start = 0; start < ids.size(); start += EXISTS_QUERY_BATCH_SIZE) {
            int end = Math.min(ids.size(), start + EXISTS_QUERY_BATCH_SIZE);
            for (Document document : target.find(Filters.in("_id", ids.subList(start, end))).projection(Projections.include("_id"))) {
                Object id = document.get("_id");
                if (id != null) existing.add(String.valueOf(id));
            }
        }
        return existing;
    }

    public static void upsertDocument(String collection, Document document) {
        if (collection == null || document == null || document.get("_id") == null) throw new IllegalArgumentException("Mongo collection, document and _id are required");
        replace(database().getCollection(collection), document);
    }

    public static void bulkUpsertDocuments(String collection, Iterable<Document> documents, int batchSize) {
        if (collection == null || collection.isBlank() || documents == null) return;
        if (batchSize < 1) throw new IllegalArgumentException("Mongo bulk batch size must be positive");
        MongoCollection<Document> target = database().getCollection(collection);
        List<WriteModel<Document>> operations = new ArrayList<>(batchSize);
        for (Document document : documents) {
            if (document == null || document.get("_id") == null) throw new IllegalArgumentException("Mongo bulk document and _id are required");
            operations.add(new ReplaceOneModel<>(Filters.eq("_id", document.get("_id")), document,
                    new ReplaceOptions().upsert(true)));
            if (operations.size() == batchSize) {
                bulkWrite(target, operations);
                operations.clear();
            }
        }
        if (!operations.isEmpty()) bulkWrite(target, operations);
    }

    public static Document toDocument(Object value) {
        return write(value);
    }

    public static List<QueryRecord> getRegisteredLolAccounts(long timeStart) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document summoner : summoners().find(Filters.eq("tracking", true))) {
            QueryRecord row = latestRegisteredRow(summoner, timeStart);
            if (row != null) result.add(row);
        }
        return result;
    }

    public static QueryRecord getRegisteredLolAccount(String puuid, long timeStart) {
        Document summoner = summoners().find(Filters.eq("_id", puuid)).first();
        return summoner == null ? new QueryRecord() : latestRegisteredRow(summoner, timeStart);
    }

    public static List<Match> getMatches(String puuid, Filter filter, int limit, int offset) {
        traceRead("match.getMatches", "puuid=" + puuid);
        if (puuid == null || puuid.isBlank() || filter == null) return List.of();
        List<Match> result = new ArrayList<>();
        int boundedOffset = Math.max(0, offset);
        int boundedLimit = Math.max(0, limit);
        List<Match> candidates = new ArrayList<>();
        boolean relationalFilter = filter.opponent() != 0 || filter.duo() != 0;
        FindIterable<Document> matches = matches().find(buildMatchFilter(puuid, null, filter, 0, 0))
                .sort(Sorts.descending("timeStart"))
                .skip(relationalFilter ? 0 : boundedOffset);
        matches = matches.limit(relationalFilter ? 0 : boundedLimit > 0 ? Math.min(100, boundedLimit) : 100);
        int skipped = 0;
        for (Document document : matches) {
            Match match = readMatch(matchRecord(document));
            if (!ProfileStatistics.matchesFilter(match, puuid, filter)) continue;
            if (relationalFilter && skipped++ < boundedOffset) continue;
            candidates.add(match);
            if (boundedLimit > 0 && candidates.size() >= Math.min(100, boundedLimit)) break;
        }
        attachEvents(candidates);
        result.addAll(candidates);
        return result;
    }

    public static int countMatches(String puuid, Filter filter) {
        traceRead("match.countMatches", "puuid=" + puuid);
        if (puuid == null || puuid.isBlank() || filter == null) return 0;
        if (filter.opponent() == 0 && filter.duo() == 0)
            return (int) Math.min(Integer.MAX_VALUE, matches().countDocuments(buildMatchFilter(puuid, null, filter, 0, 0)));
        int count = 0;
        for (Document document : matches().find(buildMatchFilter(puuid, null, filter, 0, 0)).projection(profileStatisticsMatchProjection())) {
            Match match = readMatch(matchRecord(document));
            if (ProfileStatistics.matchesFilter(match, puuid, filter)) count++;
        }
        return count;
    }

    public static long findLatestMatchTime(String patch, LeagueShard shard) {
        Bson filter = Filters.and(Filters.eq("patchMajor", patchMajor(patch)), Filters.eq("region", shard.name()));
        Document document = matches().find(filter).sort(Sorts.descending("timeStart")).first();
        return document == null ? 0L : ((Number) document.getOrDefault("timeStart", 0L)).longValue() / 1000L;
    }

    public static List<QueryRecord> findMatchBans(String patch) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.eq("patchMajor", patchMajor(patch)))
                .projection(Projections.include("bans"))) {
            result.add(QueryRecordParser.fromMap(Map.of("bans", bansJson(document.get("bans")))));
        }
        return result;
    }

    public static List<QueryRecord> findChampionWins(String patch, int champion, no.stelar7.api.r4j.basic.constants.types.lol.LaneType lane) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.eq("patchMajor", patchMajor(patch)))
                .projection(Projections.include("participants.champion", "participants.lane", "participants.win"))) {
            for (Document participant : documents(document.get("participants"))) {
                if (participant.getInteger("champion", 0) != champion) continue;
                String laneName = participant.getString("lane");
                if (lane != null && (laneName == null || !lane.name().equals(laneName))) continue;
                result.add(QueryRecordParser.fromMap(Map.of("win", participant.getBoolean("win", false))));
            }
        }
        return result;
    }

    public static Map<Integer, Map<LaneType, Integer>> findChampionRoleGames() {
        String current = patchMajor(PatchUtils.getPatch());
        String previous = patchMajor(PatchUtils.getPreviousPatch());

        List<Bson> pipeline = List.of(
                new Document("$match", Filters.in("patchMajor", List.of(current, previous))),
                new Document("$unwind", "$participants"),
                new Document("$match", Filters.in("participants.lane", playableRoleNames())),
                new Document("$group", new Document("_id", new Document("champion", "$participants.champion")
                        .append("role", "$participants.lane")).append("games", new Document("$sum", 1))),
                new Document("$sort", new Document("_id.champion", 1))
        );
        Map<Integer, Map<LaneType, Integer>> result = new LinkedHashMap<>();
        for (Document document : matches().aggregate(pipeline)) {
            Document id = document.get("_id", Document.class);
            if (id == null) continue;
            int champion = (int) number(id.get("champion"));
            LaneType role = enumValue(LaneType.class, id.getString("role"));
            if (champion == 0 || role == null || !LaneTypeUtils.playables().contains(role)) continue;
            result.computeIfAbsent(champion, ignored -> new LinkedHashMap<>())
                    .put(role, (int) number(document.get("games")));
        }
        return result;
    }

    public static List<ChampionIndexable> findChampionIndexables(String patch) {
        String majorPatch = patchMajor(patch);
        if (majorPatch == null || majorPatch.isBlank()) return List.of();

        List<ChampionIndexable> result = new ArrayList<>();
        for (Document document : championIndexables().find(Filters.eq("patchMajor", majorPatch))) {
            int champion = (int) number(document.get("championId"));
            LaneType role = enumValue(LaneType.class, document.getString("role"));
            if (champion == 0 || role == null || !LaneTypeUtils.playables().contains(role)) continue;
            result.add(new ChampionIndexable(
                    champion,
                    role,
                    (int) number(document.get("games")),
                    document.getBoolean("indexable", false),
                    number(document.get("lastUpdate"))));
        }
        result.sort((left, right) -> {
            int championOrder = Integer.compare(left.champion(), right.champion());
            if (championOrder != 0) return championOrder;
            int gamesOrder = Integer.compare(right.games(), left.games());
            return gamesOrder != 0 ? gamesOrder
                    : Integer.compare(LaneTypeUtils.playableOrder(left.role()), LaneTypeUtils.playableOrder(right.role()));
        });
        return result;
    }

    // TODO Mongo build aggregation: use buildPath for the timeline build and include rune arrays.
    public static List<QueryRecord> getChampionBuildsRaw(Filter filter) {
        List<QueryRecord> result = new ArrayList<>();
        forEachChampionBuildRaw(filter, result::add);
        return result;
    }

    public static void forEachChampionBuildRaw(Filter filter, Consumer<QueryRecord> consumer) {
        if (filter == null || consumer == null) return;
        forEachChampionBuildRawBatch(filter, 1, batch -> {
            for (QueryRecord record : batch) consumer.accept(record);
        });
    }

    public static void forEachChampionBuildRawBatch(Filter filter, int batchSize,
                                                     Consumer<List<QueryRecord>> consumer) {
        if (filter == null || consumer == null || batchSize <= 0) return;
        FindIterable<Document> query = matches().find(championMatchFilter(filter, null)).projection(Projections.include(
                "_id", "participants.champion", "participants.lane", "participants.win",
                "participants.starterItems", "participants.boots", "participants.supportItem",
                "participants.item0", "participants.item1", "participants.item2", "participants.item3",
                "participants.item4", "participants.item5", "participants.skillOrder", "participants.augments",
                "participants.summonerSpell1", "participants.summonerSpell2", "participants.primaryRunes",
                "participants.secondaryRunes", "participants.statsRunes")).batchSize(batchSize);
        List<QueryRecord> batch = new ArrayList<>(batchSize);
        try {
            try (MongoCursor<Document> cursor = query.iterator()) {
                while (cursor.hasNext()) {
                    Document match = cursor.next();
                    try {
                        for (Document participant : documents(match.get("participants"))) {
                            if (!matchesChampionFilter(participant, filter)) continue;
                            batch.add(championBuildRecord(match, participant));
                            if (batch.size() == batchSize) {
                                consumer.accept(batch);
                                batch.clear();
                            }
                        }
                    } finally {
                        MatchMemoryUtils.release(match);
                    }
                }
            }
            if (!batch.isEmpty()) {
                consumer.accept(batch);
                batch.clear();
            }
        } finally {
            MatchMemoryUtils.release(batch);
        }
    }

    public static List<QueryRecord> championBuildRecords(Document match, Filter filter) {
        if (match == null || filter == null) return List.of();
        List<QueryRecord> result = new ArrayList<>();
        for (Document participant : documents(match.get("participants")))
            if (matchesChampionBuildFilter(match, participant, filter)) result.add(championBuildRecord(match, participant));
        return result;
    }

    private static QueryRecord championBuildRecord(Document match, Document participant) {
        JSONObject build = new JSONObject();
        JSONObject buildData = new JSONObject();
        buildData.put("starter", new JSONArray(integerList(readIntegers(participant, "starterItems"))));
        buildData.put("boots", participant.getInteger("boots", 0));
        buildData.put("support_item", participant.getInteger("supportItem", 0));
        buildData.put("build", new JSONArray(List.of(
                participant.getInteger("item0", 0), participant.getInteger("item1", 0), participant.getInteger("item2", 0),
                participant.getInteger("item3", 0), participant.getInteger("item4", 0), participant.getInteger("item5", 0))));
        build.put("build", buildData);
        build.put("skill_order", new JSONArray(readIntegers(participant, "skillOrder")));
        build.put("augments", new JSONArray(readIntegers(participant, "augments")));
        build.put("summoner_spells", new JSONArray(List.of(
                participant.getInteger("summonerSpell1", 0), participant.getInteger("summonerSpell2", 0))));
        build.put("runes", new JSONObject()
            .put("primary", new JSONArray(readIntegers(participant, "primaryRunes")))
            .put("secondary", new JSONArray(readIntegers(participant, "secondaryRunes")))
            .put("stats", new JSONArray(readIntegers(participant, "statsRunes"))));
        return QueryRecordParser.fromMap(Map.of(
                "game_id", match.getString("_id"),
                "win", participant.getBoolean("win", false),
                "build", build.toString()));
    }

    public static long countChampionMatchesByFilter(Filter filter) {
        return countChampionMatches(filter);
    }

    public static List<QueryRecord> findChampionRecordsByIds(List<String> fullGameIds) {
        if (fullGameIds == null || fullGameIds.isEmpty()) return List.of();
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.in("_id", fullGameIds))) result.add(matchRecord(document));
        return result;
    }

    public static String getSummonerNameById(String puuid, LeagueShard shard) {
        Summoner summoner = findSummoner(puuid, shard);
        return summoner == null ? null : summoner.riotId();
    }

    private static QueryRecord latestRegisteredRow(Document summoner, long timeStart) {
        String puuid = summoner.getString("puuid");
        if (puuid == null) puuid = summoner.getString("_id");
        Document latest = matches().find(matchFilter(puuid, parseShard(summoner.getString("region")), timeStart, 0, GameQueueType.TEAM_BUILDER_RANKED_SOLO))
                .projection(Projections.include("_id", "timeStart", "participants.puuid", "participants.rankProgress"))
                .sort(Sorts.descending("timeStart")).first();
        Document row = new Document("puuid", puuid).append("region", summoner.getString("region"));
        if (latest == null) return QueryRecordParser.fromDocument(row);
        row.append("game_id", latest.getString("_id"))
                .append("time_start", latest.get("timeStart", 0L));
        for (Document participant : documents(latest.get("participants"))) if (puuid.equals(participant.getString("puuid"))) {
            Document progress = participant.get("rankProgress", Document.class);
            row.append("rankProgress", progress);
            break;
        }
        return QueryRecordParser.fromDocument(row);
    }

    private static boolean matchesChampionFilter(Document participant, Filter filter) {
        if (filter == null) return true;
        if (filter.champion() != 0 && participant.getInteger("champion", 0) != filter.champion()) return false;
        return filter.lane() == null || !GameQueueTypeUtils.hasLane(filter.queue())
                || filter.lane().name().equals(participant.getString("lane"));
    }

    private static boolean matchesChampionBuildFilter(Document match, Document participant, Filter filter) {
        if (!matchesChampionFilter(participant, filter)) return false;
        if (filter.region() != null && !filter.region().name().equals(match.getString("region"))) return false;
        if (filter.rank() == null) return true;
        TierType rank;
        try {
            rank = TierType.valueOf(match.getString("rank"));
        } catch (RuntimeException ignored) {
            return false;
        }
        return filter.rankBehavior() == Filter.RankBehavior.EXACT
            ? rank == filter.rank()
            : rank.ordinal() <= filter.rank().ordinal();
    }

    private static List<Integer> readIntegers(Document document, String field) {
        Object value = document.get(field);
        if (!(value instanceof List<?> values)) return List.of();
        List<Integer> result = new ArrayList<>();
        for (Object item : values) if (item instanceof Number number) result.add(number.intValue());
        return result;
    }

    private static MongoCollection<Document> summoners() {
        return database().getCollection("summoner");
    }

    private static MongoCollection<Document> leaderboardAggregates() {
        return database().getCollection(LEADERBOARD_AGGREGATES_COLLECTION);
    }

    private static MongoCollection<Document> competitive() {
        return database().getCollection(COMPETITIVE_COLLECTION);
    }

    private static MongoCollection<Document> entityCollection(String collectionName) {
        return switch (collectionName) {
            case "summoner" -> summoners();
            case "match" -> matches();
            default -> throw new IllegalArgumentException("Unsupported Mongo entity collection=" + collectionName);
        };
    }

    public record SummonerSearchResult(Summoner summoner, Rank soloRank) {}

    public record ProfileProjection(Summoner summoner, Map<GameQueueType, Rank> ranks, List<Mastery> masteries) {}

    public static String findPuuid(String riotId, LeagueShard shard) {
        traceRead("summoner.findPuuid", "region=" + shard);
        List<Summoner> result = findSummonersByRiotId(normalizedRiotId(riotId), shard, 1);
        if (!result.isEmpty()) return result.get(0).puuid();

        if (riotId == null || riotId.isBlank() || shard == null) return null;
        Pattern exactRiotId = Pattern.compile("^" + Pattern.quote(riotId.trim()) + "$", Pattern.CASE_INSENSITIVE);
        Document document = summoners().find(Filters.and(
                Filters.eq("region", shard.name()),
                Filters.regex("riotId", exactRiotId)))
            .projection(Projections.include("_id"))
            .first();
        return document == null ? null : document.getString("_id");
    }

    public static Summoner findSummoner(String puuid, LeagueShard shard) {
        traceRead("summoner.find", "puuid=" + puuid + " region=" + shard);
        if (puuid == null || puuid.isBlank()) return null;
        Bson filter = shard == null ? Filters.eq("_id", puuid) : Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()));
        Document document = summoners().find(filter).first();
        return document == null ? null : summoner(document);
    }

    public static Map<String, Summoner> findSummonersByPuuids(List<String> puuids) {
        Map<String, Summoner> result = new HashMap<>();
        if (puuids == null || puuids.isEmpty()) return result;
        Set<String> distinct = new java.util.LinkedHashSet<>();
        for (String puuid : puuids) if (puuid != null && !puuid.isBlank()) distinct.add(puuid);
        if (distinct.isEmpty()) return result;
        List<String> all = new ArrayList<>(distinct);
        traceRead("summoner.findByPuuids", "size=" + all.size());
        for (int start = 0; start < all.size(); start += MAX_BATCH_IDS) {
            int end = Math.min(all.size(), start + MAX_BATCH_IDS);
            List<String> ids = all.subList(start, end);
            for (Document document : summoners().find(Filters.in("_id", ids))
                    .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon"))
                    .limit(ids.size())) {
                Summoner summoner = summoner(document);
                result.put(summoner.puuid(), summoner);
            }
        }
        return result;
    }

    public static List<Summoner> findSummonersByRiotId(String normalizedQuery, LeagueShard shard, int limit) {
        List<Summoner> result = new ArrayList<>();
        for (SummonerSearchResult row : findSummonerSearch(normalizedQuery, shard, limit)) result.add(row.summoner());
        return result;
    }

    public static List<SummonerSearchResult> findSummonerSearch(String normalizedQuery, LeagueShard shard, int limit) {
        traceRead("summoner.search", "region=" + shard + " limit=" + limit);
        int boundedLimit = Math.max(0, Math.min(MAX_SEARCH_RESULTS, limit));
        if (boundedLimit == 0) return List.of();
        Pattern prefix = Pattern.compile("^" + Pattern.quote(normalizedQuery == null ? "" : normalizedQuery));
        List<SummonerSearchResult> result = new ArrayList<>();
        for (Document document : summoners()
                .find(Filters.and(Filters.eq("region", shard.name()), Filters.regex("riotSearch", prefix)))
                .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon", "ranks"))
                .sort(Sorts.ascending("riotId"))
                .limit(boundedLimit)) {
            result.add(new SummonerSearchResult(summoner(document), soloRank(document)));
        }
        return result;
    }

    public static List<QueryRecord> findAccountsByUserId(String userId) {
        traceRead("summoner.findAccountsByUserId", "userId=" + userId);
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : summoners().find(Filters.eq("userId", userId)).sort(Sorts.ascending("_id"))) {
            result.add(record(document));
        }
        return result;
    }

        public static String findUserIdByPuuid(String puuid, LeagueShard shard) {
        Document document = summoners().find(Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name())))
            .projection(Projections.include("userId")).first();
        return document == null ? null : record(document).getAsString("userId");
    }

        public static String findSummonerName(String puuid, LeagueShard shard) {
        Document document = summoners().find(Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name())))
            .projection(Projections.include("riotId")).first();
        return document == null ? null : record(document).getAsString("riotId");
    }

    public static Rank findRank(String puuid, LeagueShard shard, GameQueueType queue) {
        traceRead("summoner.findRank", "puuid=" + puuid + " queue=" + queue);
        Document document = summoners().find(summonerFilter(puuid, shard))
                .projection(Projections.include("ranks")).first();
        if (document == null) return null;
        return queue == null ? null : ranks(document).get(GameQueueTypeUtils.canonicalQueue(queue));
    }

    public static Map<GameQueueType, Rank> findRanks(String puuid, LeagueShard shard) {
        traceRead("summoner.findRanks", "puuid=" + puuid);
        Document document = summoners().find(summonerFilter(puuid, shard))
                .projection(Projections.include("ranks")).first();
        if (document == null || !document.containsKey("ranks")) return null;
        return ranks(document);
    }

    public static Map<String, Rank> findSoloRanksByPuuid(List<String> puuids, LeagueShard shard) {
        Map<String, Rank> result = new HashMap<>();
        if (puuids == null || puuids.isEmpty()) return result;
        List<String> ids = boundedIds(puuids);
        for (Document document : summoners()
                .find(Filters.and(Filters.in("_id", ids), Filters.eq("region", shard.name())))
                .projection(Projections.include("_id", "puuid", "ranks"))
                .limit(ids.size())) {
            Rank rank = soloRank(document);
            if (rank != null) result.put(puuid(document), rank);
        }
        return result;
    }

    public static ProfileProjection findProfileProjection(String puuid, LeagueShard shard) {
        if (puuid == null || puuid.isBlank()) return null;
        Bson filter = shard == null
            ? Filters.eq("_id", puuid)
            : Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()));
        Document document = summoners().find(filter)
                .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon", "ranks", "masteries"))
                .first();
        if (document == null) return null;
        return new ProfileProjection(summoner(document), ranks(document), masteries(document));
    }

    public static Map<String, List<Mastery>> findMasteriesByPuuid(List<String> puuids, LeagueShard shard) {
        Map<String, List<Mastery>> result = new HashMap<>();
        if (puuids == null || puuids.isEmpty()) return result;
        List<String> ids = boundedIds(puuids);
        Bson filter = shard == null
            ? Filters.in("_id", ids)
            : Filters.and(Filters.in("_id", ids), Filters.eq("region", shard.name()));
        for (Document document : summoners().find(filter)
                .projection(Projections.include("_id", "puuid", "masteries"))
                .limit(ids.size())) {
            result.put(puuid(document), masteries(document));
        }
        return result;
    }

    public static List<Mastery> findMasteries(String puuid, LeagueShard shard) {
        traceRead("summoner.findMasteries", "puuid=" + puuid);
        Document document = summoners().find(summonerFilter(puuid, shard))
                .projection(Projections.include("masteries")).first();
        if (document == null || !document.containsKey("masteries")) return null;
        return masteries(document);
    }

    public static com.safjnest.lol.model.match.Match findMatch(String fullGameId) {
        traceRead("match.find", "id=" + fullGameId);
        Document document = matches().find(Filters.eq("_id", fullGameId)).first();
        if (document == null) return null;
        Match match = read(matchRecord(document), Match.class);
        attachEvents(List.of(match));
        return match;
    }

    public static boolean isMatchTracked(String fullGameId) {
        if (fullGameId == null || fullGameId.isBlank()) return false;
        Document document = matches().find(Filters.eq("_id", fullGameId))
                .projection(Projections.include("tracked")).first();
        return document != null && Boolean.TRUE.equals(document.getBoolean("tracked"));
    }

    static boolean isMatchTrackedDocument(Document document) {
        return document != null && Boolean.TRUE.equals(document.getBoolean("tracked"));
    }

    public static Participant findPreviousParticipant(
            String puuid,
            LeagueShard shard,
            GameQueueType queue,
            long beforeTimeStart,
            String beforeMatchId) {
        if (puuid == null || puuid.isBlank() || shard == null || queue == null || beforeTimeStart <= 0) return null;

        GameQueueType canonicalQueue = GameQueueTypeUtils.canonicalQueue(queue);
        Bson queueFilter = canonicalQueue == GameQueueType.RANKED_SOLO_5X5
                ? Filters.in("queue", GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), GameQueueType.RANKED_SOLO_5X5.name())
                : Filters.eq("queue", queue.name());
        Bson beforeFilter = beforeMatchId == null || beforeMatchId.isBlank()
                ? Filters.lt("timeStart", beforeTimeStart)
                : Filters.or(Filters.lt("timeStart", beforeTimeStart),
                        Filters.and(Filters.eq("timeStart", beforeTimeStart), Filters.lt("_id", beforeMatchId)));
        Bson filter = Filters.and(
                Filters.elemMatch("participants", Filters.eq("puuid", puuid)),
                Filters.eq("region", shard.name()),
                queueFilter,
                beforeFilter);
        Document document = matches().find(filter)
                .projection(Projections.include("participants"))
                .sort(Sorts.descending("timeStart", "_id"))
                .first();
        if (document == null) return null;

        for (Document participant : documents(document.get("participants"))) {
            if (puuid.equals(participant.getString("puuid"))) return readParticipant(matchRecord(participant));
        }
        return null;
    }

    public static List<com.safjnest.lol.model.match.MatchResult> findMatchResults(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue,
            int offset,
            int limit,
            boolean ascending) {
        traceRead("match.findResults", "puuid=" + puuid + " queue=" + queue + " offset=" + offset + " limit=" + limit);
        List<com.safjnest.lol.model.match.MatchResult> result = new ArrayList<>();
        int boundedOffset = Math.max(0, offset);
        int boundedLimit = Math.max(0, Math.min(101, limit));
        if (boundedLimit == 0) return result;
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue))
                .projection(matchResultProjection())
                .sort(ascending ? Sorts.ascending("timeStart", "_id") : Sorts.descending("timeStart", "_id"))
                .skip(boundedOffset)
                .limit(boundedLimit)) {
            com.safjnest.lol.model.match.Match match = read(matchRecord(document), Match.class);
            com.safjnest.lol.model.match.MatchResult matchResult = toMatchResult(match, puuid);
            if (matchResult != null) result.add(matchResult);
        }
        return result;
    }

    public static List<com.safjnest.lol.model.match.RankHistoryMatch> findRankHistoryMatches(
            String puuid,
            LeagueShard shard,
            long seasonStart,
            long seasonEnd) {
        if (puuid == null || puuid.isBlank() || shard == null || seasonStart <= 0 || seasonEnd < seasonStart) return List.of();
        List<com.safjnest.lol.model.match.RankHistoryMatch> result = new ArrayList<>();
        Bson filter = Filters.and(matchFilter(puuid, shard, seasonStart, seasonEnd, null), Filters.in("queue",
            GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), GameQueueType.RANKED_SOLO_5X5.name(), GameQueueType.RANKED_FLEX_SR.name()));
        for (Document document : matches().find(filter)
                .projection(rankHistoryProjection()).sort(Sorts.ascending("timeStart", "_id"))) {
            com.safjnest.lol.model.match.Match match = read(matchRecord(document), Match.class);
            com.safjnest.lol.model.match.RankHistoryMatch value = com.safjnest.lol.model.match.RankHistoryMatch.from(match, puuid);
            if (value != null) result.add(value);
        }
        return result;
    }

    public static List<Match> findProfileStatisticsMatches(
            String puuid,
            LeagueShard shard,
            Filter filter,
            long afterTime,
            long untilTime) {
        if (puuid == null || puuid.isBlank() || filter == null) return List.of();
        List<Match> result = new ArrayList<>();
        forEachProfileStatisticsMatch(puuid, shard, filter, afterTime, untilTime, result::add);
        return result;
    }

    public static void forEachProfileStatisticsMatch(
            String puuid,
            LeagueShard shard,
            Filter filter,
            long afterTime,
            long untilTime,
            Consumer<Match> consumer) {
        traceRead("match.findProfileStatistics", "puuid=" + puuid + " filter=" + (filter == null ? "null" : filter.toSummonerKey()));
        if (puuid == null || puuid.isBlank() || filter == null || consumer == null) return;
        try (MongoCursor<Document> cursor = matches().find(buildMatchFilter(puuid, shard, filter, afterTime, untilTime))
                .projection(profileStatisticsMatchProjection())
                .sort(Sorts.ascending("timeStart", "_id"))
                .iterator()) {
            while (cursor.hasNext()) {
                Match match = read(matchRecord(cursor.next()), Match.class);
                if (ProfileStatistics.matchesFilter(match, puuid, filter)) consumer.accept(match);
            }
        }
    }

    public static void forEachProfileRecordMatch(
        String puuid,
        LeagueShard shard,
        Filter filter,
        Consumer<Match> consumer
    ) {
        if (puuid == null || puuid.isBlank() || filter == null || consumer == null) return;
        List<Match> batch = new ArrayList<>(PROFILE_RECORD_MATCH_BATCH_SIZE);
        try (MongoCursor<Document> cursor = matches().find(buildMatchFilter(puuid, shard, filter, 0, 0))
                .projection(profileStatisticsMatchProjection())
                .sort(Sorts.ascending("timeStart", "_id"))
                .iterator()) {
            while (cursor.hasNext()) {
                Match match = read(matchRecord(cursor.next()), Match.class);
                if (!ProfileStatistics.matchesFilter(match, puuid, filter)) continue;
                batch.add(match);
                if (batch.size() == PROFILE_RECORD_MATCH_BATCH_SIZE) flushProfileRecordMatches(batch, consumer);
            }
        }
        flushProfileRecordMatches(batch, consumer);
    }

    public static List<MatchResult> findProfileRecentMatches(
            String puuid,
            LeagueShard shard,
            Filter filter,
            int limit) {
        if (puuid == null || puuid.isBlank() || filter == null || limit <= 0) return List.of();
        List<MatchResult> result = new ArrayList<>();
        boolean relationalFilter = filter.opponent() != 0 || filter.duo() != 0;
        FindIterable<Document> matches = matches().find(buildMatchFilter(puuid, shard, filter, 0, 0))
                .projection(matchResultProjection())
                .sort(Sorts.descending("timeStart"));
        if (!relationalFilter) matches = matches.limit(Math.max(limit, Math.min(100, limit * 4)));
        for (Document document : matches) {
            Match match = read(matchRecord(document), Match.class);
            if (!ProfileStatistics.matchesFilter(match, puuid, filter)) continue;
            MatchResult value = toMatchResult(match, puuid);
            if (value != null) result.add(value);
            if (result.size() == limit) break;
        }
        return result;
    }

        public static List<com.safjnest.lol.model.match.Match> findAnalysisMatches(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        traceRead("match.findAnalysis", "puuid=" + puuid + " queue=" + queue);
        List<com.safjnest.lol.model.match.Match> result = new ArrayList<>();
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue)).sort(Sorts.descending("timeStart"))) {
            result.add(read(matchRecord(document), Match.class));
        }
        attachEvents(result);
        return result;
    }

    public static void forEachAiTrainingSample(String patch, Consumer<Map<String, Object>> consumer) {
        if (consumer == null) return;
        String patchMajor = patch == null ? patchMajor(PatchUtils.getPatch()) : patch;
        Bson filter = Filters.and(
            Filters.eq("patchMajor", patchMajor),
            Filters.in("queue", AI_TRAINING_QUEUES)
        );
        try (MongoCursor<Document> cursor = matches().find(filter)
                .projection(Projections.include("_id", "patch", "queue", "timeStart", "participants.champion", "participants.lane", "participants.team", "participants.win"))
                .batchSize(AI_TRAINING_CURSOR_BATCH_SIZE)
                .iterator()) {
            while (cursor.hasNext()) {
                Document match = cursor.next();
                List<Map<String, Object>> blue = aiTrainingParticipants(match, "BLUE");
                List<Map<String, Object>> red = aiTrainingParticipants(match, "RED");
                if (blue == null || red == null) continue;
                consumer.accept(aiTrainingSample(match, "BLUE", blue));
                consumer.accept(aiTrainingSample(match, "RED", red));
            }
        }
    }

        public static long countMatches(String puuid, LeagueShard shard, long timeStart, long timeEnd, GameQueueType queue) {
        return matches().countDocuments(matchFilter(puuid, shard, timeStart, timeEnd, queue));
    }

        public static boolean hasMatch(String fullGameId) {
        return matches().countDocuments(Filters.eq("_id", fullGameId)) > 0;
    }

        public static List<String> findSeasonSummonerPuuids(LeagueShard shard, long seasonStart, long seasonEnd) {
        List<String> result = new ArrayList<>();
        for (Document document : matches().aggregate(List.of(
                new Document("$match", matchFilter(null, shard, seasonStart, seasonEnd, null)),
                new Document("$unwind", "$participants"),
                new Document("$match", Filters.and(
                        Filters.ne("participants.puuid", null),
                        Filters.ne("participants.puuid", ""))),
                new Document("$group", new Document("_id", "$participants.puuid")),
                new Document("$sort", new Document("_id", 1))
        )).allowDiskUse(true).batchSize(MAX_BATCH_IDS)) {
            String puuid = document.getString("_id");
            if (puuid != null) result.add(puuid);
        }
        return result;
    }

        public static QueryRecord findSummaryProjection(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        Document document = profileStatistics().find(Filters.and(
                Filters.eq("puuid", puuid),
                Filters.gte("timeEnd", timeStart),
                Filters.lte("timeStart", timeEnd)))
            .sort(Sorts.descending("timeEnd"))
            .first();
        return document == null ? null : profileRecord(document);
    }

    public static List<QueryRecord> findSummonerData(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        traceRead("match.findSummonerData", "puuid=" + puuid + " queue=" + queue);
        List<QueryRecord> result = new ArrayList<>();
        GameQueueType canonicalQueue = GameQueueTypeUtils.canonicalQueue(queue);
        Bson filter = matchFilter(puuid, shard, timeStart, timeEnd, null);
        if (canonicalQueue == GameQueueType.RANKED_SOLO_5X5) {
            filter = Filters.and(filter, Filters.in("queue",
                GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), GameQueueType.RANKED_SOLO_5X5.name()));
        } else if (queue != null) {
            filter = Filters.and(filter, Filters.eq("queue", queue.name()));
        }
        for (Document document : matches().find(filter)
                .projection(Projections.include("_id", "timeStart", "timeEnd", "patch",
                        "tracked", "participants.puuid", "participants.rankProgress", "participants.win"))
                .sort(Sorts.ascending("timeStart", "_id"))) {
            String gameId = gameId(document);
            for (Document participant : documents(document.get("participants"))) {
                if (!puuid.equals(participant.getString("puuid"))) continue;
                Document row = new Document("game_id", gameId)
                        .append("tracked", document.get("tracked", false))
                        .append("rankProgress", participant.get("rankProgress"))
                        .append("win", participant.get("win", false))
                        .append("time_start", document.get("timeStart", 0L))
                        .append("time_end", document.get("timeEnd", 0L))
                        .append("patch", document.get("patch"));
                result.add(QueryRecordParser.fromDocument(row));
                break;
            }
        }
        return result;
    }

    public static List<QueryRecord> findTrackedSummoners(long timeStart) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : summoners().find(Filters.eq("tracking", true))) {
            result.add(record(document));
        }
        return result;
    }

    public static List<Summoner> findTrackedSummonerModels() {
        List<Summoner> result = new ArrayList<>();
        for (Document document : summoners().find(Filters.eq("tracking", true))) {
            result.add(summoner(document));
        }
        return result;
    }

    public static MongoCursor<Document> trackedSummonerCursor() {
        return summoners().find(Filters.eq("tracking", true))
                .projection(Projections.include("_id"))
                .sort(Sorts.ascending("_id"))
                .batchSize(1_000)
                .iterator();
    }

    public static List<ProfileIndexable> refreshProfileIndexables() {
        List<Document> candidates = new ArrayList<>();
        for (Document document : summoners().find(profileIndexableFilter())
                .projection(Projections.include("_id", "puuid", "riotId", "region"))
                .sort(Sorts.ascending("region", "riotId"))) {
            String puuid = puuid(document);
            String riotId = document.getString("riotId");
            String region = document.getString("region");
            if (puuid == null || puuid.isBlank() || riotId == null || riotId.isBlank()
                    || region == null || region.isBlank()) continue;
            candidates.add(new Document("_id", puuid).append("riotId", riotId).append("region", region));
        }

        long now = System.currentTimeMillis();
        Set<String> ids = new HashSet<>();
        List<WriteModel<Document>> operations = new ArrayList<>(candidates.size());
        for (Document candidate : candidates) {
            String puuid = candidate.getString("_id");
            ids.add(puuid);
            operations.add(new UpdateOneModel<>(Filters.eq("_id", puuid), Updates.combine(
                    Updates.set("puuid", puuid),
                    Updates.set("riotId", candidate.getString("riotId")),
                    Updates.set("region", candidate.getString("region")),
                    Updates.setOnInsert("lastUpdate", now)),
                    new UpdateOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(profileIndexables(), operations);
        if (!profileIndexables().deleteMany(ids.isEmpty() ? new Document() : Filters.nin("_id", ids)).wasAcknowledged()) {
            throw new IllegalStateException("Mongo profile indexable cleanup was not acknowledged");
        }

        return findProfileIndexables();
    }

    public static List<ProfileIndexable> findProfileIndexables() {
        List<ProfileIndexable> result = new ArrayList<>();
        for (Document document : profileIndexables().find()
                .projection(Projections.include("riotId", "region"))
                .sort(Sorts.ascending("region", "riotId"))) {
            String riotId = document.getString("riotId");
            String region = document.getString("region");
            if (riotId != null && !riotId.isBlank() && region != null && !region.isBlank()) {
                result.add(new ProfileIndexable(riotId, region));
            }
        }
        return result;
    }

        public static QueryRecord findTrackedSummoner(String puuid, long timeStart) {
        Document document = summoners().find(Filters.and(
                Filters.eq("_id", puuid), Filters.eq("tracking", true))).first();
        return document == null ? null : record(document);
    }

    public static ProfileStatistics findProfileStatistics(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return null;
        Document document = profileStatistics().find(Filters.and(
                Filters.eq("puuid", puuid), Filters.eq("filterKey", filter.toSummonerKey()))).first();
        return document == null ? null : readProfileStatistics(document);
    }

    public static ProfileActivity findProfileActivity(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return null;
        Document document = profileActivity().find(Filters.and(
                Filters.eq("puuid", puuid), Filters.eq("filterKey", filter.toSummonerKey()))).first();
        return document == null ? null : readProfileActivity(document);
    }

    public static ProfileMatchups findProfileMatchups(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return null;
        Document document = profileMatchups().find(Filters.and(
                Filters.eq("puuid", puuid), Filters.eq("filterKey", filter.toSummonerKey()))).first();
        return document == null ? null : readProfileMatchups(document);
    }

    public static List<ProfileRecord> findProfileRecords(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return List.of();
        List<ProfileRecord> result = new ArrayList<>();
        for (Document document : profileRecords().find(Filters.and(
                Filters.eq("puuid", puuid), Filters.eq("filterKey", filter.toSummonerKey())))
                .sort(Sorts.ascending("metric"))) {
            ProfileRecord record = readProfileRecord(document);
            if (record != null) result.add(record);
        }
        return result;
    }

    public static List<ProfileRecord> findGlobalProfileRecords(
        Filter filter,
        RecordMetric metric,
        LeagueShard region,
        int limit,
        int offset
    ) {
        if (filter == null || metric == null || limit <= 0) return List.of();
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("filterKey", filter.toSummonerKey()));
        filters.add(Filters.eq("metric", metric.name()));
        if (region != null) filters.add(Filters.eq("region", region.name()));
        List<ProfileRecord> result = new ArrayList<>();
        for (Document document : profileRecords().find(Filters.and(filters))
                .sort(Sorts.orderBy(Sorts.descending("score"), Sorts.ascending("occurredAt"), Sorts.ascending("puuid")))
                .skip(Math.max(0, offset)).limit(Math.min(100, limit))) {
            ProfileRecord record = readProfileRecord(document);
            if (record != null) result.add(record);
        }
        return result;
    }

    public static long countGlobalProfileRecords(Filter filter, RecordMetric metric, LeagueShard region) {
        if (filter == null || metric == null) return 0;
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("filterKey", filter.toSummonerKey()));
        filters.add(Filters.eq("metric", metric.name()));
        if (region != null) filters.add(Filters.eq("region", region.name()));
        return profileRecords().countDocuments(Filters.and(filters));
    }

    public static List<Filter> findProfileRefreshFilters(String puuid) {
        if (puuid == null || puuid.isBlank()) return List.of();

        Map<String, Filter> filters = new LinkedHashMap<>();
        readProfileRefreshFilters(profileStatistics(), puuid, filters);
        readProfileRefreshFilters(profileActivity(), puuid, filters);
        readProfileRefreshFilters(profileMatchups(), puuid, filters);
        return new ArrayList<>(filters.values());
    }

    public static void pruneProfileNonCanonical(
        String puuid,
        Filter statisticsFilter,
        Filter activityFilter,
        Filter matchupsFilter
    ) {
        if (puuid == null || puuid.isBlank() || statisticsFilter == null
                || activityFilter == null || matchupsFilter == null) return;
        profileStatistics().deleteMany(Filters.and(Filters.eq("puuid", puuid),
            Filters.ne("filterKey", statisticsFilter.toSummonerKey())));
        profileActivity().deleteMany(Filters.and(Filters.eq("puuid", puuid),
            Filters.ne("filterKey", activityFilter.toSummonerKey())));
        profileMatchups().deleteMany(Filters.and(Filters.eq("puuid", puuid),
            Filters.ne("filterKey", matchupsFilter.toSummonerKey())));
    }

    public static ProfileStatistics findProfileStatistics(String puuid, long seasonStart) {
        return findProfileStatistics(puuid, Filter.summoner(seasonStart, 0));
    }

    public static Map<String, ProfileStatistics> findProfileStatistics(List<String> puuids, Filter filter) {
        if (puuids == null || puuids.isEmpty()) return Map.of();
        if (filter == null) return Map.of();
        Map<String, ProfileStatistics> result = new HashMap<>();
        for (int start = 0; start < puuids.size(); start += MAX_BATCH_IDS) {
            int end = Math.min(puuids.size(), start + MAX_BATCH_IDS);
            List<String> ids = boundedIds(puuids.subList(start, end));
            for (Document document : profileStatistics().find(Filters.and(
                    Filters.in("puuid", ids), Filters.eq("filterKey", filter.toSummonerKey())))
                    .limit(ids.size())) {
                ProfileStatistics statistics = readProfileStatistics(document);
                if (statistics != null) result.put(document.getString("puuid"), statistics);
            }
        }
        return result;
    }

    public static Map<String, ProfileStatistics> findProfileStatistics(List<String> puuids, long seasonStart) {
        return findProfileStatistics(puuids, Filter.summoner(seasonStart, 0));
    }

        public static List<Build> findChampionBuilds(Filter filter) {
        if (filter == null) return List.of();
        List<Build> result = new ArrayList<>();
        for (Document document : builds().find(Filters.eq("filterKey", filter.toKey()))) {
            Build build = readBuild(document);
            if (build != null) result.add(build);
        }
        return result;
    }

        public static List<QueryRecord> findChampionBuildSource(Filter filter) {
        return findChampionMatchProjections(findChampionMatchIds(filter, null, 10_000));
    }

        public static List<Filter> findStoredChampionBuildFilters() {
        return readFilters(builds(), false);
    }

    public static List<Filter> findChampionBuildRefreshFilters(String patch) {
        return findChampionSourceFilters(patch, true);
    }

    // Old champion_statistics flow removed — use ChampionStatsDocument with scope

    public static long findChampionBuildLastUpdate(Filter filter) {
        if (filter == null) return 0;
        Document document = builds().find(Filters.eq("filterKey", filter.toKey()))
            .projection(Projections.include("lastUpdate")).first();
        return document == null ? 0 : number(document, "lastUpdate");
    }

    public static List<Filter> findChampionStatsRefreshFilters(String patch) {
        return findChampionSourceFilters(patch, false);
    }

        public static long countChampionMatches(Filter filter) {
        return matches().countDocuments(championMatchFilter(filter, null));
    }

        public static List<String> findChampionMatchIds(Filter filter, String afterFullGameId, int limit) {
        int boundedLimit = Math.max(0, Math.min(10_000, limit));
        if (boundedLimit == 0) return List.of();
        List<Bson> filters = new ArrayList<>();
        filters.add(championMatchFilter(filter, null));
        if (afterFullGameId != null && !afterFullGameId.isBlank()) {
            filters.add(Filters.gt("_id", afterFullGameId));
        }
        List<String> result = new ArrayList<>();
        for (Document document : matches().find(Filters.and(filters))
                .projection(Projections.include("_id"))
                .sort(Sorts.ascending("_id"))
                .limit(boundedLimit)) {
            result.add(document.getString("_id"));
        }
        return result;
    }

        public static List<QueryRecord> findChampionMatchProjections(List<String> fullGameIds) {
        return matchProjections(fullGameIds, false);
    }

        public static List<QueryRecord> findChampionParticipantProjections(List<String> fullGameIds) {
        return matchProjections(fullGameIds, true);
    }

    public static List<QueryRecord> findChampionTrendProjections(List<String> fullGameIds) {
        return matchProjections(fullGameIds, false);
    }

    public record ChampionRawDocuments(List<Document> documents, long matchReadNanos, long eventReadNanos) {}

    public record ChampionRawMatch(Document document, long matchReadNanos, long eventReadNanos) {}

    public static void forEachChampionRawMatch(Filter filter, Consumer<ChampionRawMatch> consumer) {
        if (filter == null || consumer == null) return;
        FindIterable<Document> query = matches().find(championMatchFilter(filter, null))
                .projection(championRawProjection())
                .batchSize(100);
        try (MongoCursor<Document> cursor = query.iterator()) {
            while (cursor.hasNext()) {
                long started = System.nanoTime();
                Document document = cursor.next();
                try {
                    consumer.accept(new ChampionRawMatch(document, System.nanoTime() - started, 0));
                } finally {
                    MatchMemoryUtils.release(document);
                }
            }
        }
    }

    public static void forEachChampionRawMatchWithBuild(Filter filter, Consumer<ChampionRawMatch> consumer) {
        if (filter == null || consumer == null) return;
        FindIterable<Document> query = matches().find(championMatchFilter(filter, null))
            .projection(championRawWithBuildProjection()).batchSize(100);
        try (MongoCursor<Document> cursor = query.iterator()) {
            while (cursor.hasNext()) {
                long started = System.nanoTime();
                Document document = cursor.next();
                try {
                    consumer.accept(new ChampionRawMatch(document, System.nanoTime() - started, 0));
                } finally {
                    MatchMemoryUtils.release(document);
                }
            }
        }
    }

    public static void forEachChampionRawMatchEventBatch(Filter filter, int batchSize,
                                                           Consumer<ChampionRawMatch> consumer) {
        if (filter == null || batchSize <= 0 || consumer == null) return;
        List<String> batch = new ArrayList<>(batchSize);
        FindIterable<Document> query = matches().find(championMatchFilter(filter, null))
                .projection(Projections.include("_id")).batchSize(batchSize);
        try {
            try (MongoCursor<Document> cursor = query.iterator()) {
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    try {
                        String matchId = document.getString("_id");
                        if (matchId != null) batch.add(matchId);
                        if (batch.size() == batchSize) processChampionRawMatchEventBatch(batch, batchSize, consumer);
                    } finally {
                        MatchMemoryUtils.release(document);
                    }
                }
                if (!batch.isEmpty()) processChampionRawMatchEventBatch(batch, batchSize, consumer);
            }
        } finally {
            MatchMemoryUtils.release(batch);
        }
    }

    private static void processChampionRawMatchEventBatch(List<String> batch, int batchSize,
                                                            Consumer<ChampionRawMatch> consumer) {
        if (batch.isEmpty()) return;
        Map<String, Document> matchesById = new HashMap<>();
        try {
            try (MongoCursor<Document> cursor = matches().find(Filters.in("_id", batch))
                    .projection(championRawProjection()).batchSize(batchSize).iterator()) {
                while (cursor.hasNext()) {
                    Document match = cursor.next();
                    String matchId = match.getString("_id");
                    if (matchId != null) matchesById.put(matchId, match);
                    else MatchMemoryUtils.release(match);
                }
            }
            try (MongoCursor<Document> cursor = matchEvents().find(Filters.in("_id", batch)).batchSize(batchSize).iterator()) {
                while (cursor.hasNext()) {
                    long eventStarted = System.nanoTime();
                    Document event = cursor.next();
                    Document match = matchesById.remove(event.getString("_id"));
                    try {
                        if (match == null) continue;
                        match.put("events", decodeMatchEventsJson(event));
                        consumer.accept(new ChampionRawMatch(match, 0, System.nanoTime() - eventStarted));
                    } finally {
                        MatchMemoryUtils.release(event);
                        if (match != null) MatchMemoryUtils.release(match);
                    }
                }
            }
        } finally {
            MatchMemoryUtils.release(matchesById);
            MatchMemoryUtils.release(batch);
        }
    }

    public static List<Document> findChampionRawDocuments(List<String> fullGameIds) {
        return findChampionRawDocumentsTimed(fullGameIds).documents();
    }

    public static ChampionRawDocuments findChampionRawDocumentsTimed(List<String> fullGameIds) {
        if (fullGameIds == null || fullGameIds.isEmpty()) return new ChampionRawDocuments(List.of(), 0, 0);
        long matchReadStarted = System.nanoTime();
        FindIterable<Document> query = matches().find(Filters.in("_id", boundedIds(fullGameIds)))
                .projection(Projections.include(
                        "_id", "bans", "timeStart", "timeEnd",
                        "participants.champion", "participants.lane", "participants.win", "participants.team",
                        "participants.kda", "participants.cs", "participants.goldEarned", "participants.puuid"))
                .limit(Math.min(MAX_BATCH_IDS, fullGameIds.size()));
        List<Document> result = new ArrayList<>();
        try (MongoCursor<Document> cursor = query.iterator()) {
            while (cursor.hasNext()) result.add(cursor.next());
        }
        long matchReadNanos = System.nanoTime() - matchReadStarted;
        long eventReadNanos = 0;
        if (!result.isEmpty()) {
            List<String> ids = new ArrayList<>(result.size());
            Map<String, Document> byId = new HashMap<>();
            for (Document document : result) {
                String id = document.getString("_id");
                if (id != null) {
                    ids.add(id);
                    byId.put(id, document);
                }
            }
            long eventReadStarted = System.nanoTime();
            try (MongoCursor<Document> cursor = matchEvents().find(Filters.in("_id", ids)).iterator()) {
                while (cursor.hasNext()) {
                    Document event = cursor.next();
                    Document match = byId.get(event.getString("_id"));
                    if (match != null) match.put("events", decodeMatchEventsJson(event));
                }
            }
            eventReadNanos = System.nanoTime() - eventReadStarted;
        }
        return new ChampionRawDocuments(result, matchReadNanos, eventReadNanos);
    }

    public static List<Summoner> findLeaderboardPage(
            TierType rank,
            GameQueueType queue,
            String region,
            long offset,
            int limit) {
        return findLeaderboardPage(rank, queue, region, null, offset, limit);
    }

    public static List<Summoner> findLeaderboardPage(
            TierType rank,
            GameQueueType queue,
            String region,
            LaneType role,
            long offset,
            int limit) {
        return findLeaderboardPage(rank, queue, region, role, null, offset, limit);
    }

    public static List<Summoner> findLeaderboardPage(
            TierType rank,
            GameQueueType queue,
            String region,
            LaneType role,
            Integer otpChampionId,
            long offset,
            int limit) {
        int boundedLimit = Math.max(0, Math.min(50, limit));
        int boundedOffset = (int) Math.min(Integer.MAX_VALUE, Math.max(0, offset));
        if (boundedLimit == 0) return List.of();

        List<String> puuids = new ArrayList<>(boundedLimit);
        for (Document document : competitive().find(competitiveFilter(rank, queue, region, role, otpChampionId))
                .projection(Projections.include("puuid"))
                .sort(Sorts.descending("mmr"))
                .skip(boundedOffset)
                .limit(boundedLimit)) {
            String puuid = document.getString("puuid");
            if (puuid != null) puuids.add(puuid);
        }
        if (puuids.isEmpty()) return List.of();

        Map<String, Summoner> byPuuid = new HashMap<>();
        for (Document document : summoners().find(Filters.in("_id", puuids))
                .projection(Projections.include("_id", "riotId", "region", "level", "icon", "ranks", "masteries"))) {
            Summoner summoner = summoner(document);
            byPuuid.put(summoner.puuid(), summoner);
        }
        List<Summoner> page = new ArrayList<>(puuids.size());
        for (String puuid : puuids) {
            Summoner summoner = byPuuid.get(puuid);
            if (summoner != null) page.add(summoner);
        }
        return page;
    }

    public static Long findLeaderboardAggregateCount(TierType rank, GameQueueType queue, String region) {
        if (rank != null) {
            List<LeaderboardDistribution.Entry> distribution = readLeaderboardAggregate(
                    rankDistributionAggregateKey(queue, region), RANK_DISTRIBUTION_AGGREGATE);
            if (distribution != null) for (LeaderboardDistribution.Entry entry : distribution)
                if (rank.name().equals(entry.key())) return entry.players();
        }
        String aggregateKey = leaderboardCountAggregateKey(queue, region, rank);
        Document aggregate = leaderboardAggregates().find(Filters.and(
                Filters.eq("_id", aggregateKey),
                Filters.eq("type", PAGE_COUNT_AGGREGATE),
                Filters.eq("source", COMPETITIVE_COLLECTION))).first();
        if (aggregate == null || !aggregate.containsKey("count")) return null;
        return number(aggregate, "count");
    }

    public static long findLeaderboardCount(TierType rank, GameQueueType queue, String region) {
        return findLeaderboardCount(rank, queue, region, null);
    }

    public static long findLeaderboardCount(TierType rank, GameQueueType queue, String region, LaneType role) {
        return findLeaderboardCount(rank, queue, region, role, null);
    }

    public static long findLeaderboardCount(
        TierType rank, GameQueueType queue, String region, LaneType role, Integer otpChampionId
    ) {
        if (role != null || otpChampionId != null)
            return competitive().countDocuments(competitiveFilter(rank, queue, region, role, otpChampionId));
        Long stored = findLeaderboardAggregateCount(rank, queue, region);
        if (stored != null) return stored;

        long total = competitive().countDocuments(competitiveFilter(rank, queue, region, null));
        storeLeaderboardCount(queue, region, rank, total);
        return total;
    }

    public static List<LeaderboardDistribution.Entry> findRankDistribution(GameQueueType queue, String region) {
        String aggregateKey = rankDistributionAggregateKey(queue, region);
        List<LeaderboardDistribution.Entry> stored = readLeaderboardAggregate(
                aggregateKey, RANK_DISTRIBUTION_AGGREGATE);
        if (stored != null) return stored;

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document entry : competitive().find(competitiveFilter(null, queue, region, null))
                .projection(Projections.include("mmr")))
            counts.merge(TierDivisionUtils.getTierFromMmr(number(entry, "mmr")).name(), 1L, Long::sum);
        List<LeaderboardDistribution.Entry> result = new ArrayList<>();
        for (TierType tier : TierType.values()) {
            if (tier != TierType.UNRANKED) result.add(new LeaderboardDistribution.Entry(
                    tier.name(), counts.getOrDefault(tier.name(), 0L)));
        }
        storeLeaderboardAggregate(aggregateKey, RANK_DISTRIBUTION_AGGREGATE, queue, region, null, result);
        return result;
    }

    public static List<LeaderboardDistribution.Entry> findTopRegions(GameQueueType queue, TierType rank) {
        String aggregateKey = topRegionsAggregateKey(queue, rank);
        List<LeaderboardDistribution.Entry> stored = readLeaderboardAggregate(
                aggregateKey, TOP_REGIONS_AGGREGATE);
        if (stored != null) return stored;

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document entry : competitive().find(competitiveFilter(rank, queue, GLOBAL_LEADERBOARD_REGION, null))
                .projection(Projections.include("region"))) {
            String region = entry.getString("region");
            if (region != null) counts.merge(region, 1L, Long::sum);
        }
        List<LeaderboardDistribution.Entry> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            result.add(new LeaderboardDistribution.Entry(entry.getKey(), entry.getValue()));
        }
        result.sort((first, second) -> {
            int players = Long.compare(second.players(), first.players());
            return players != 0 ? players : first.key().compareTo(second.key());
        });
        storeLeaderboardAggregate(aggregateKey, TOP_REGIONS_AGGREGATE, queue, "GLOBAL", rank, result);
        return result;
    }

    static String rankDistributionAggregateKey(GameQueueType queue, String region) {
        return RANK_DISTRIBUTION_AGGREGATE + ":" + queueName(queue) + ":" + regionName(region);
    }

    static String topRegionsAggregateKey(GameQueueType queue, TierType rank) {
        return TOP_REGIONS_AGGREGATE + ":" + queueName(queue) + ":" + (rank == null ? "ALL" : rank.name());
    }

    static String leaderboardCountAggregateKey(GameQueueType queue, String region, TierType rank) {
        return PAGE_COUNT_AGGREGATE + ":" + queueName(queue) + ":" + regionName(region)
                + ":" + (rank == null ? "ALL" : rank.name());
    }

    private static List<LeaderboardDistribution.Entry> readLeaderboardAggregate(
            String aggregateKey, String type) {
        Document aggregate = leaderboardAggregates().find(Filters.and(
                Filters.eq("_id", aggregateKey),
                Filters.eq("type", type),
                Filters.eq("source", COMPETITIVE_COLLECTION))).first();
        if (aggregate == null) return null;

        Object value = aggregate.get("entries");
        if (!(value instanceof List<?> values)) return null;
        List<LeaderboardDistribution.Entry> result = new ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof Document entry)) return null;
            String key = entry.getString("key");
            if (key == null) return null;
            result.add(new LeaderboardDistribution.Entry(key, number(entry, "players")));
        }
        return result;
    }

    public static void rebuildLeaderboardAggregates() {
        List<Document> scopes = leaderboardAggregates().find()
                .projection(Projections.include("type", "queue", "region", "rank"))
                .into(new ArrayList<>());
        deleteLeaderboardAggregates(new Document());
        for (Document scope : scopes) {
            GameQueueType queue = queue(scope.getString("queue"));
            String type = scope.getString("type");
            if (RANK_DISTRIBUTION_AGGREGATE.equals(type)) {
                findRankDistribution(queue, scope.getString("region"));
            } else if (TOP_REGIONS_AGGREGATE.equals(type) && scope.getString("rank") != null) {
                findTopRegions(queue, TierType.valueOf(scope.getString("rank")));
            } else if (PAGE_COUNT_AGGREGATE.equals(type)) {
                findLeaderboardCount(tier(scope.getString("rank")), queue, scope.getString("region"));
            }
        }
    }

    public static LeaderboardAggregateRebuild rebuildAllLeaderboardAggregates() {
        deleteLeaderboardAggregates(new Document());
        int rankDistributions = 0;
        int counts = 0;
        int topRegions = 0;

        for (GameQueueType queue : LEADERBOARD_QUEUES) {
            for (String region : leaderboardRegions()) {
                findRankDistribution(queue, region);
                rankDistributions++;
                findLeaderboardCount(null, queue, region);
                counts++;
            }
            for (TierType rank : TierType.values()) {
                findTopRegions(queue, rank);
                topRegions++;
            }
        }
        return new LeaderboardAggregateRebuild(rankDistributions, counts, topRegions);
    }

    public record LeaderboardAggregateRebuild(int rankDistributions, int counts, int topRegions) {

        public int total() {
            return rankDistributions + counts + topRegions;
        }
    }

    private static void storeLeaderboardAggregate(
            String aggregateKey,
            String type,
            GameQueueType queue,
            String region,
            TierType rank,
            List<LeaderboardDistribution.Entry> entries) {
        List<Document> values = new ArrayList<>(entries.size());
        for (LeaderboardDistribution.Entry entry : entries) {
            values.add(new Document("key", entry.key()).append("players", entry.players()));
        }
        Document aggregate = new Document("_id", aggregateKey)
                .append("type", type)
                .append("source", COMPETITIVE_COLLECTION)
                .append("queue", queueName(queue))
                .append("entries", values);
        if (region != null) aggregate.append("region", region);
        if (rank != null) aggregate.append("rank", rank.name());

        UpdateResult update = leaderboardAggregates().replaceOne(
                Filters.eq("_id", aggregateKey), aggregate, new ReplaceOptions().upsert(true));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo leaderboard aggregate write was not acknowledged");
    }

    private static void storeLeaderboardCount(GameQueueType queue, String region, TierType rank, long count) {
        String aggregateKey = leaderboardCountAggregateKey(queue, region, rank);
        Document aggregate = new Document("_id", aggregateKey)
                .append("type", PAGE_COUNT_AGGREGATE)
                .append("source", COMPETITIVE_COLLECTION)
                .append("queue", queueName(queue))
                .append("region", regionName(region))
                .append("rank", rank == null ? "ALL" : rank.name())
                .append("count", count);
        UpdateResult update = leaderboardAggregates().replaceOne(
                Filters.eq("_id", aggregateKey), aggregate, new ReplaceOptions().upsert(true));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo leaderboard count write was not acknowledged");
    }

    private static void deleteLeaderboardAggregates(Bson filter) {
        if (!leaderboardAggregates().deleteMany(filter).wasAcknowledged()) {
            throw new IllegalStateException("Mongo leaderboard aggregate rebuild was not acknowledged");
        }
    }

    private static String queueName(GameQueueType queue) {
        return queue == null ? "ALL" : queue.name();
    }

    private static GameQueueType queue(String value) {
        if (value == null || "ALL".equals(value)) return null;
        try {
            return GameQueueTypeUtils.canonicalQueue(GameQueueType.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static TierType tier(String value) {
        if (value == null || "ALL".equals(value)) return null;
        try {
            return TierType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String regionName(String region) {
        return region == null || region.isBlank() ? GLOBAL_LEADERBOARD_REGION : region;
    }

    private static List<String> leaderboardRegions() {
        List<String> regions = new ArrayList<>(LeagueShardUtils.getActives().size() + 1);
        regions.add(GLOBAL_LEADERBOARD_REGION);
        for (LeagueShard shard : LeagueShardUtils.getActives()) regions.add(shard.name());
        return regions;
    }

    private static String rankPath(GameQueueType queue) {
        if (queue == null) throw new IllegalArgumentException("A canonical rank queue is required");
        return "ranks." + GameQueueTypeUtils.canonicalQueue(queue).name();
    }

    public static boolean applyEntityUpdate(
            String collectionName,
            String id,
            List<Map<String, Object>> operations,
            Map<String, Object> filters,
            boolean upsert) {
        if (collectionName == null || collectionName.isBlank()) throw new IllegalArgumentException("Mongo entity collection is required");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Mongo entity id is required");
        if (operations == null || operations.isEmpty()) return true;

        List<Bson> pipeline = new ArrayList<>(operations.size());
        for (Map<String, Object> operation : operations) pipeline.add(entityUpdateStage(operation));

        List<Bson> rootFilters = new ArrayList<>();
        rootFilters.add(Filters.eq("_id", id));
        if (filters != null) {
            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                rootFilters.add(Filters.eq(filter.getKey(), mongoValue(filter.getValue())));
            }
        }

        UpdateResult result = entityCollection(collectionName).updateOne(
                rootFilters.size() == 1 ? rootFilters.get(0) : Filters.and(rootFilters),
                pipeline,
                new UpdateOptions().upsert(upsert));
        if (!result.wasAcknowledged()) return false;
        return upsert || result.getMatchedCount() > 0;
    }

    public static boolean upsertSummoner(Summoner summoner, String userId) {
        if (summoner == null || summoner.puuid() == null) return false;
        if (userId != null) {
            Document current = summoners().find(Filters.eq("_id", summoner.puuid()))
                    .projection(Projections.include("userId")).first();
            String owner = current == null ? null : current.getString("userId");
            if (owner != null && !owner.equals(userId)) return false;
        }
        traceRead("summoner.upsert", "puuid=" + summoner.puuid() + " userId=" + userId);
        return summoners().updateOne(Filters.eq("_id", summoner.puuid()), summonerUpdate(summoner, userId),
                new UpdateOptions().upsert(true)).wasAcknowledged();
    }

    public static void touchSummonerLastSeen(String puuid) {
        if (puuid == null || puuid.isBlank()) return;
        summoners().updateOne(Filters.eq("_id", puuid), Updates.set("lastSeenAt", System.currentTimeMillis()));
    }

    public static long findSummonerLastSeen(String puuid) {
        if (puuid == null || puuid.isBlank()) return 0;
        Document document = summoners().find(Filters.eq("_id", puuid))
            .projection(Projections.include("lastSeenAt"))
            .first();
        if (document == null) return 0;
        Object value = document.get("lastSeenAt");
        return value instanceof Number number ? number.longValue() : 0;
    }

    public static boolean upsertSummoner(String puuid, LeagueShard shard, String riotId, int level, int icon, String userId) {
        if (puuid == null || puuid.isBlank() || shard == null) return false;
        return upsertSummoner(new Summoner(puuid, riotId, shard, level, icon), userId);
    }

    public static boolean upsertSummoner(MatchParticipant participant, LeagueShard shard) {
        if (participant == null || participant.getPuuid() == null || participant.getPuuid().isBlank() || shard == null) return false;
        String riotId = participant.getRiotIdName();
        String riotTag = participant.getRiotIdTagline();
        if (riotId != null && !riotId.isBlank() && riotTag != null && !riotTag.isBlank()) riotId += "#" + riotTag;
        return upsertSummoner(participant.getPuuid(), shard, riotId, participant.getSummonerLevel(), participant.getProfileIcon(), null);
    }

    public static boolean upsertSummoner(SpectatorParticipant participant, LeagueShard shard) {
        if (participant == null || participant.getPuuid() == null || participant.getPuuid().isBlank() || shard == null) return false;
        return upsertSpectatorSummoners(List.of(new Summoner(
            participant.getPuuid(),
            participant.getRiotId(),
            shard,
            0,
            Math.toIntExact(participant.getProfileIconId())
        )));
    }

    public static boolean upsertSummoners(List<Summoner> summoners) {
        if (summoners == null) return false;
        List<WriteModel<Document>> operations = new ArrayList<>(summoners.size());
        for (Summoner summoner : summoners) {
            if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank()) continue;
            operations.add(new UpdateOneModel<>(Filters.eq("_id", summoner.puuid()), summonerUpdate(summoner, null),
                    new UpdateOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(summoners(), operations);
        return true;
    }

    public static boolean upsertSpectatorSummoners(List<Summoner> summoners) {
        if (summoners == null) return false;
        List<WriteModel<Document>> operations = new ArrayList<>(summoners.size());
        for (Summoner summoner : summoners) {
            if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank()) continue;

            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set("region", summoner.region() == null ? null : summoner.region().name()));
            updates.add(Updates.set("icon", summoner.icon()));
            updates.add(Updates.setOnInsert("level", summoner.level()));
            if (summoner.riotId() != null && !summoner.riotId().isBlank()) {
                updates.add(Updates.set("riotId", summoner.riotId()));
                updates.add(Updates.set("riotSearch", normalizedRiotId(summoner.riotId())));
            }
            operations.add(new UpdateOneModel<>(Filters.eq("_id", summoner.puuid()), Updates.combine(updates),
                new UpdateOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(summoners(), operations);
        return true;
    }

    public static boolean detachSummonerUser(String puuid, String userId) {
        return summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)),
                Updates.combine(Updates.unset("userId"), Updates.set("tracking", false))).getMatchedCount() > 0;
    }

    public static boolean setSummonerTracking(String puuid, String userId, boolean tracked) {
        return summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)),
                Updates.set("tracking", tracked)).getMatchedCount() > 0;
    }

    public static boolean upsertRanks(String puuid, LeagueShard shard, Map<GameQueueType, Rank> ranks) {
        Document values = writeRanks(ranks);
        UpdateResult update = summoners().updateOne(summonerFilter(puuid, shard), Updates.set("ranks", values));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo ranks update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

    public static int bulkUpsertRanks(Map<String, Map<GameQueueType, Rank>> ranksByPuuid) {
        if (ranksByPuuid == null || ranksByPuuid.isEmpty()) return 0;
        List<WriteModel<Document>> operations = new ArrayList<>(ranksByPuuid.size());
        for (Map.Entry<String, Map<GameQueueType, Rank>> entry : ranksByPuuid.entrySet()) {
            String puuid = entry.getKey();
            if (puuid == null || puuid.isBlank() || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            for (Map.Entry<GameQueueType, Rank> rank : entry.getValue().entrySet()) {
                if (rank.getKey() == null || rank.getValue() == null) continue;
                operations.add(new UpdateOneModel<>(Filters.eq("_id", puuid), Updates.set(rankPath(rank.getKey()), write(rank.getValue()))));
            }
        }
        if (!operations.isEmpty()) bulkWrite(summoners(), operations);
        return operations.size();
    }

    public static boolean upsertRank(String puuid, LeagueShard shard, GameQueueType queue, Rank rank) {
        if (rank == null || queue == null) return false;

        Document value = write(rank);
        UpdateResult update = summoners().updateOne(summonerFilter(puuid, shard), Updates.set(rankPath(queue), value));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo rank update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

    public static boolean upsertCompetitive(CompetitiveEntry entry) {
        if (entry == null || entry.puuid() == null || entry.puuid().isBlank() || entry.region() == null
                || entry.queue() == null) return false;
        Document value = new Document("_id", entry.id())
                .append("puuid", entry.puuid())
                .append("region", entry.region().name())
                .append("queue", GameQueueTypeUtils.canonicalQueue(entry.queue()).name())
                .append("mmr", entry.mmr())
                .append("lastUpdate", entry.lastUpdate());
        if (entry.primary() != null) value.append("primary", entry.primary().name());
        if (entry.otpChampionId() != null) value.append("otpChampionId", entry.otpChampionId());
        UpdateResult update = competitive().replaceOne(Filters.eq("_id", entry.id()), value, new ReplaceOptions().upsert(true));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo competitive update was not acknowledged");
        return true;
    }

    public static boolean deleteCompetitive(String puuid, GameQueueType queue) {
        if (puuid == null || puuid.isBlank() || queue == null) return false;
        return competitive().deleteOne(Filters.eq("_id", puuid + ':' + GameQueueTypeUtils.canonicalQueue(queue).name()))
                .getDeletedCount() > 0;
    }

    public static long clearCompetitive() {
        var result = competitive().deleteMany(new Document());
        if (!result.wasAcknowledged()) throw new IllegalStateException("Mongo competitive clear was not acknowledged");
        return result.getDeletedCount();
    }

    public static void forEachCompetitiveSummonerBatch(Consumer<List<Summoner>> consumer) {
        if (consumer == null) return;
        List<Summoner> batch = new ArrayList<>(COMPETITIVE_REBUILD_BATCH_SIZE);
        try (MongoCursor<Document> cursor = summoners().find()
                .projection(Projections.include("_id", "riotId", "region", "level", "icon", "ranks"))
                .batchSize(COMPETITIVE_REBUILD_BATCH_SIZE)
                .iterator()) {
            while (cursor.hasNext()) {
                batch.add(summoner(cursor.next()));
                if (batch.size() < COMPETITIVE_REBUILD_BATCH_SIZE) continue;
                consumer.accept(batch);
                batch = new ArrayList<>(COMPETITIVE_REBUILD_BATCH_SIZE);
            }
        }
        if (!batch.isEmpty()) consumer.accept(batch);
    }

    public record CompetitiveRebuild(long candidates, long entries, long removed) {}

    public static OtpRefresh refreshCanonicalProfileOtp() {
        Filter filter = Filter.canonical();
        long scanned = 0;
        long saved = 0;
        try (MongoCursor<Document> cursor = profileStatistics().find(Filters.eq("filterKey", filter.toSummonerKey()))
                .batchSize(COMPETITIVE_REBUILD_BATCH_SIZE)
                .iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                String puuid = document.getString("puuid");
                ProfileStatistics statistics = readProfileStatistics(document);
                if (puuid == null || puuid.isBlank() || statistics == null) continue;
                scanned++;
                statistics.finish();
                if (upsertProfileStatistics(puuid, filter, statistics)) saved++;
            }
        }
        return new OtpRefresh(scanned, saved);
    }

    public record OtpRefresh(long scanned, long saved) {}

    public static boolean upsertMasteries(String puuid, LeagueShard shard, List<Mastery> masteries) {
        List<Document> values = new ArrayList<>();
        if (masteries != null) for (Mastery mastery : masteries) values.add(write(mastery));
        UpdateResult update = summoners().updateOne(summonerFilter(puuid, shard), Updates.set("masteries", values));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo masteries update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

    public static boolean mergeSummonerEmbedded(String puuid, String field, String identityField, List<Document> values) {
        if (puuid == null || puuid.isBlank() || field == null || field.isBlank() || identityField == null || identityField.isBlank()) {
            throw new IllegalArgumentException("Summoner embedded field parameters are required");
        }
        Document source = summoners().find(Filters.eq("_id", puuid)).first();
        if (source == null) return false;
        Map<String, Document> merged = new LinkedHashMap<>();
        for (Document item : documents(source.get(field))) {
            Object identity = item.get(identityField);
            if (identity != null) merged.put(String.valueOf(identity), item);
        }
        if (values != null) for (Document item : values) {
            Object identity = item == null ? null : item.get(identityField);
            if (identity == null) throw new IllegalArgumentException("Missing embedded identity " + field + "." + identityField);
            merged.put(String.valueOf(identity), new Document(item));
        }
        UpdateResult update = summoners().updateOne(Filters.eq("_id", puuid), Updates.set(field, new ArrayList<>(merged.values())));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo embedded update was not acknowledged field=" + field + " puuid=" + puuid);
        return true;
    }

    public static boolean upsertMatch(String fullGameId, Match match) {
        return upsertMatch(fullGameId, match, false);
    }

    public static boolean upsertMatch(String fullGameId, Match match, boolean tracked) {
        if (match == null) return false;
        upsertMatchDocument(fullGameId, match, tracked);
        upsertMatchEvents(fullGameId, match.eventData != null ? match.eventData : match.events == null ? Map.of() : match.events.toMap());
        return true;
    }

    public static boolean insertMatch(Match match) {
        if (match == null || match.gameId == null || match.gameId.isBlank()) return false;
        String fullGameId = match.gameId;
        Document document = write(match);
        document.put("_id", fullGameId);
        document.put("tracked", false);
        UpdateResult update = matches().updateOne(Filters.eq("_id", fullGameId),
                new Document("$setOnInsert", document), new UpdateOptions().upsert(true));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo match insert was not acknowledged id=" + fullGameId);
        if (update.getUpsertedId() == null) return false;
        upsertMatchEvents(fullGameId, match.eventData != null ? match.eventData : match.events == null ? Map.of() : match.events.toMap());
        return true;
    }

    public static boolean updateUntrackedParticipantRankProgress(String fullGameId, String puuid, RankProgress progress) {
        if (fullGameId == null || fullGameId.isBlank() || puuid == null || puuid.isBlank()
                || !RankProgressUtils.hasCurrentSnapshot(progress)) return false;
        Bson missingSnapshot = Filters.or(Filters.exists("rankProgress", false), Filters.exists("rankProgress.rank", false),
                Filters.exists("rankProgress.lp", false));
        Bson filter = Filters.and(Filters.eq("_id", fullGameId), Filters.ne("tracked", true),
                Filters.elemMatch("participants", Filters.and(Filters.eq("puuid", puuid), missingSnapshot)));
        UpdateResult update = matches().updateOne(filter, Updates.set("participants.$.rankProgress", rankProgressDocument(progress)));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo rank snapshot update was not acknowledged id=" + fullGameId);
        return update.getModifiedCount() > 0;
    }

    public static boolean restoreUntrackedParticipantRankProgress(String fullGameId, String puuid, RankProgress progress) {
        if (fullGameId == null || fullGameId.isBlank() || puuid == null || puuid.isBlank()
                || !RankProgressUtils.hasCurrentSnapshot(progress)) return false;
        RankProgress restored = new RankProgress(progress.rank, progress.lp, progress.gain, null, null);
        Bson filter = Filters.and(Filters.eq("_id", fullGameId), Filters.ne("tracked", true),
                Filters.elemMatch("participants", Filters.eq("puuid", puuid)));
        UpdateResult update = matches().updateOne(filter, Updates.set("participants.$.rankProgress", rankProgressDocument(restored)));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo rank progress restore was not acknowledged id=" + fullGameId);
        return update.getModifiedCount() > 0;
    }

    public static boolean upsertMatchDocument(String fullGameId, Match match) {
        return upsertMatchDocument(fullGameId, match, false);
    }

    public static boolean upsertMatchDocument(String fullGameId, Match match, boolean tracked) {
        if (match == null) return false;
        Document document = write(match);
        document.put("_id", fullGameId);
        document.put("tracked", tracked);
        replace(matches(), document);
        return true;
    }

    static int normalizeMatchDocuments(List<String> fullGameIds) {
        if (fullGameIds == null || fullGameIds.isEmpty()) return 0;
        int updated = 0;
        for (int start = 0; start < fullGameIds.size(); start += EXISTS_QUERY_BATCH_SIZE) {
            int end = Math.min(fullGameIds.size(), start + EXISTS_QUERY_BATCH_SIZE);
            for (Document document : matches().find(Filters.in("_id", fullGameIds.subList(start, end)))
                    .projection(Projections.include("_id", "region", "leagueShard", "patch", "patchMajor", "fullGameId", "gameId", "game_id"))) {
                List<Bson> updates = new ArrayList<>();
                String region = document.getString("region");
                if (region == null || region.isBlank()) {
                    region = document.getString("leagueShard");
                    if (region == null || region.isBlank()) region = regionFromMatchId(document.getString("_id"));
                    if (region != null && !region.isBlank()) updates.add(Updates.set("region", region));
                }

                String major = patchMajor(document.getString("patch"));
                if (major == null) {
                    if (document.containsKey("patchMajor")) updates.add(Updates.unset("patchMajor"));
                } else if (!major.equals(document.getString("patchMajor"))) {
                    updates.add(Updates.set("patchMajor", major));
                }

                for (String field : List.of("fullGameId", "gameId", "game_id", "leagueShard")) {
                    if (document.containsKey(field)) updates.add(Updates.unset(field));
                }
                if (updates.isEmpty()) continue;
                UpdateResult result = matches().updateOne(Filters.eq("_id", document.get("_id")), Updates.combine(updates));
                if (!result.wasAcknowledged()) throw new IllegalStateException("Mongo match normalization was not acknowledged id=" + document.get("_id"));
                if (result.getModifiedCount() > 0) updated++;
            }
        }
        return updated;
    }

    public static RankProgressPage migrateRankProgressSchemaPage(String afterId, int limit, boolean dryRun) {
        int boundedLimit = Math.max(1, Math.min(RANK_PROGRESS_SCHEMA_PAGE_SIZE, limit));
        Bson legacyProgress = Filters.or(Filters.exists("participants.rank"), Filters.exists("participants.lp"), Filters.exists("participants.gain"));
        Bson filter = afterId == null || afterId.isBlank() ? legacyProgress : Filters.and(Filters.gt("_id", afterId), legacyProgress);
        int processed = 0;
        String cursor = afterId;
        for (Document match : matches().find(filter)
                .projection(Projections.include("_id"))
                .sort(Sorts.ascending("_id"))
                .limit(boundedLimit)) {
            cursor = match.getString("_id");
            processed++;
        }
        if (processed == 0 || dryRun) return new RankProgressPage(cursor, processed, 0);
        Bson page = afterId == null || afterId.isBlank()
                ? Filters.lte("_id", cursor)
                : Filters.and(Filters.gt("_id", afterId), Filters.lte("_id", cursor));
        UpdateResult result = matches().updateMany(Filters.and(page, legacyProgress), rankProgressSchemaUpdate());
        if (!result.wasAcknowledged()) throw new IllegalStateException("Mongo RankProgress schema update was not acknowledged");
        return new RankProgressPage(cursor, processed, (int) result.getModifiedCount());
    }

    private static List<Bson> rankProgressSchemaUpdate() {
        Document legacyFields = new Document("$in", List.of("$$field.k", List.of("rank", "lp", "gain")));
        Document withoutLegacyFields = new Document("$arrayToObject", new Document("$filter", new Document("input",
                new Document("$objectToArray", "$$participant")).append("as", "field")
                .append("cond", new Document("$not", List.of(legacyFields)))));
        Document noProgress = new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList("$$progress", null)), null));
        Document hasRank = new Document("$ne", Arrays.asList(new Document("$ifNull", Arrays.asList("$$participant.rank", null)), null));
        Document hasLegacyGain = new Document("$ne", Arrays.asList(new Document("$ifNull", Arrays.asList("$$participant.gain", null)), null));
        Document progressHasNoGain = new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList("$$progress.gain", null)), null));
        Document migratedProgress = new Document("$mergeObjects", List.of(
                new Document("rank", "$$participant.rank").append("lp", new Document("$ifNull", List.of("$$participant.lp", 0))),
                new Document("$cond", List.of(hasLegacyGain, new Document("gain", "$$participant.gain"), new Document()))));
        Document newProgress = new Document("rankProgress", migratedProgress);
        Document completedProgress = new Document("rankProgress", new Document("$mergeObjects", List.of(
                "$$progress", new Document("gain", "$$participant.gain"))));
        Document needsNewProgress = new Document("$and", List.of(noProgress, hasRank));
        Document needsCompletedProgress = new Document("$and", List.of(
                new Document("$not", List.of(noProgress)), progressHasNoGain, hasLegacyGain));
        Document progress = new Document("$cond", List.of(needsNewProgress, newProgress,
                new Document("$cond", List.of(needsCompletedProgress, completedProgress, new Document()))));
        Document participant = new Document("$let", new Document("vars", new Document("progress", "$$participant.rankProgress"))
                .append("in", new Document("$mergeObjects", List.of(withoutLegacyFields, progress))));
        Document participants = new Document("$map", new Document("input", "$participants").append("as", "participant")
                .append("in", participant));
        return List.of(new Document("$set", new Document("participants", participants)));
    }

    public static MongoCursor<Document> rankProgressSubjectCursor(String afterCursor) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("queue", new Document("$in", List.of(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), GameQueueType.RANKED_SOLO_5X5.name())))));
        pipeline.add(new Document("$unwind", "$participants"));
        pipeline.add(new Document("$match", new Document("participants.puuid", new Document("$nin", Arrays.asList(null, "")))));
        pipeline.add(new Document("$group", new Document("_id", new Document("region", "$region")
                .append("puuid", "$participants.puuid"))));
        if (afterCursor != null && !afterCursor.isBlank()) {
            int separator = afterCursor.indexOf('|');
            if (separator <= 0 || separator == afterCursor.length() - 1)
                throw new IllegalArgumentException("Invalid RankProgress subject cursor=" + afterCursor);
            String region = afterCursor.substring(0, separator);
            String puuid = afterCursor.substring(separator + 1);
            pipeline.add(new Document("$match", new Document("$or", List.of(
                    new Document("_id.region", new Document("$gt", region)),
                    new Document("_id.region", region).append("_id.puuid", new Document("$gt", puuid))))));
        }
        pipeline.add(new Document("$sort", new Document("_id.region", 1).append("_id.puuid", 1)));
        return matches().aggregate(pipeline).allowDiskUse(true).batchSize(RANK_PROGRESS_HISTORY_BULK_SIZE).iterator();
    }

    public static int rebuildRankProgressHistory(RankProgressSubject subject, boolean dryRun) {
        if (subject == null || subject.puuid().isBlank() || subject.region().isBlank()) return 0;
        Document filter = new Document("participants.puuid", subject.puuid())
                .append("region", subject.region())
                .append("queue", new Document("$in", List.of(
                        GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), GameQueueType.RANKED_SOLO_5X5.name())));
        List<Bson> pipeline = List.of(
                new Document("$match", filter),
                new Document("$sort", new Document("timeStart", -1).append("_id", -1)),
                new Document("$unwind", "$participants"),
                new Document("$match", new Document("participants.puuid", subject.puuid())),
                new Document("$project", new Document("_id", 1).append("tracked", 1)
                        .append("rankProgress", "$participants.rankProgress")));
        Document newerMatch = null;
        RankProgress newerProgress = null;
        List<WriteModel<Document>> updates = new ArrayList<>(RANK_PROGRESS_HISTORY_BULK_SIZE);
        int updated = 0;
        for (Document match : matches().aggregate(pipeline).batchSize(RANK_PROGRESS_HISTORY_BULK_SIZE)) {
            Document progressDocument = match.get("rankProgress", Document.class);
            RankProgress current = progressDocument == null ? null : readRankProgress(matchRecord(progressDocument));
            if (newerMatch != null) {
                WriteModel<Document> update = rankProgressHistoryUpdate(
                        newerMatch, subject.puuid(), newerProgress, current);
                if (update != null) {
                    updates.add(update);
                    updated++;
                    if (!dryRun && updates.size() == RANK_PROGRESS_HISTORY_BULK_SIZE) {
                        bulkWrite(matches(), updates);
                        updates.clear();
                    }
                }
            }
            newerMatch = match;
            newerProgress = current;
        }
        if (newerMatch != null) {
            WriteModel<Document> update = rankProgressHistoryUpdate(newerMatch, subject.puuid(), newerProgress, null);
            if (update != null) {
                updates.add(update);
                updated++;
            }
        }
        if (!dryRun && !updates.isEmpty()) bulkWrite(matches(), updates);
        return updated;
    }

    private static WriteModel<Document> rankProgressHistoryUpdate(
            Document match,
            String puuid,
            RankProgress current,
            RankProgress previous) {
        if (!RankProgressUtils.hasCurrentSnapshot(current)) return null;
        RankProgress rebuilt = new RankProgress(current.rank, current.lp, current.gain, null, null);
        if (RankProgressUtils.hasCurrentSnapshot(previous)) {
            int expectedGain = RankProgressUtils.calculateGain(GameQueueType.RANKED_SOLO_5X5, rebuilt, previous);
            boolean tracked = Boolean.TRUE.equals(match.getBoolean("tracked"));
            boolean unrankedTransition = rebuilt.rank == TierDivisionType.UNRANKED || previous.rank == TierDivisionType.UNRANKED;
            if (tracked || unrankedTransition || rebuilt.gain != null && rebuilt.gain == expectedGain) {
                rebuilt.previousRank = previous.rank;
                rebuilt.previousLp = previous.lp;
                if (unrankedTransition) rebuilt.gain = expectedGain;
            }
        }
        if (rankProgressEquals(current, rebuilt)) return null;
        return new UpdateOneModel<>(Filters.and(Filters.eq("_id", match.getString("_id")),
                Filters.eq("participants.puuid", puuid)),
                Updates.set("participants.$.rankProgress", rankProgressDocument(rebuilt)));
    }

        public static boolean upsertParticipant(String fullGameId, Participant participant) {
        if (participant == null) return false;
        Document value = participantDocument(participant);
        Document filter = new Document("$filter", new Document("input", new Document("$ifNull", List.of("$participants", List.of())))
                .append("as", "participant")
                .append("cond", new Document("$ne", List.of("$$participant.puuid", participant.puuid))));
        Document updatePipeline = new Document("$set", new Document("participants", new Document("$concatArrays", List.of(filter, List.of(value)))));
        UpdateResult update = matches().updateOne(Filters.eq("_id", fullGameId), List.of(updatePipeline));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo participant update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

        public static boolean updateMatchRank(String fullGameId, TierType rank) {
        UpdateResult update = matches().updateOne(Filters.eq("_id", fullGameId),
                Updates.set("rank", rank == null ? null : rank.name()));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo match rank update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

    public static boolean updateMatchEvents(String fullGameId, Map<String, Object> events) {
        return upsertMatchEvents(fullGameId, events);
    }

    public static boolean upsertMatchEvents(String fullGameId, Map<String, Object> events) {
        Map<String, Object> source = events == null ? Map.of() : events;
        if (source.isEmpty()) {
            matchEvents().deleteOne(Filters.eq("_id", fullGameId));
            return true;
        }
        byte[] payload = eventJson(source);
        Document document = new Document("_id", fullGameId)
                .append("encoding", "json")
                .append("uncompressedBytes", payload.length)
                .append("data", new String(payload, StandardCharsets.UTF_8))
                .append("checksum", sha256(payload));
        replace(matchEvents(), document);
        return true;
    }

    public static boolean upsertMatchEventsJson(String fullGameId, String json) {
        String source = json == null ? "" : json.trim();
        if (source.isEmpty() || "{}".equals(source) || "null".equals(source)) {
            matchEvents().deleteOne(Filters.eq("_id", fullGameId));
            return true;
        }
        byte[] payload = source.getBytes(StandardCharsets.UTF_8);
        Document document = new Document("_id", fullGameId)
                .append("encoding", "json")
                .append("uncompressedBytes", payload.length)
                .append("data", source)
                .append("checksum", sha256(payload));
        replace(matchEvents(), document);
        return true;
    }

    public static boolean upsertProfileStatistics(String puuid, Filter filter, ProfileStatistics statistics) {
        if (puuid == null || puuid.isBlank() || filter == null || statistics == null) return false;
        String filterKey = filter.toSummonerKey();
        Document values = JsonCodec.toDocument(statistics);
        List<Bson> updates = new ArrayList<>(values.size() + 3);
        updates.add(Updates.set("puuid", puuid));
        updates.add(Updates.set("filterKey", filterKey));
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!"_id".equals(entry.getKey())) updates.add(Updates.set(entry.getKey(), entry.getValue()));
        }
        for (String obsolete : List.of("schemaVersion", "statistics", "total", "queueStats", "laneStats", "championStats", "matchups", "duoStats"))
            updates.add(Updates.unset(obsolete));
        updates.add(Updates.setOnInsert("_id", new ObjectId()));
        UpdateResult result = profileStatistics().updateOne(
                Filters.and(Filters.eq("puuid", puuid), Filters.eq("filterKey", filterKey)),
                Updates.combine(updates), new UpdateOptions().upsert(true));
        return result.wasAcknowledged();
    }

    public static boolean upsertProfileActivity(String puuid, Filter filter, ProfileActivity activity) {
        if (puuid == null || puuid.isBlank() || filter == null || activity == null) return false;
        String filterKey = filter.toSummonerKey();
        UpdateResult result = profileActivity().updateOne(
                Filters.and(Filters.eq("puuid", puuid), Filters.eq("filterKey", filterKey)),
                Updates.combine(
                        Updates.set("puuid", puuid),
                        Updates.set("filterKey", filterKey),
                        Updates.set("activity", structuredWithoutMetadata(activity)),
                        Updates.setOnInsert("_id", new ObjectId())),
                new UpdateOptions().upsert(true));
        return result.wasAcknowledged();
    }

    public static boolean upsertProfileMatchups(String puuid, Filter filter, ProfileMatchups matchups) {
        if (puuid == null || puuid.isBlank() || filter == null || matchups == null) return false;
        String filterKey = filter.toSummonerKey();
        UpdateResult result = profileMatchups().updateOne(
                Filters.and(Filters.eq("puuid", puuid), Filters.eq("filterKey", filterKey)),
                Updates.combine(
                        Updates.set("puuid", puuid),
                        Updates.set("filterKey", filterKey),
                        Updates.set("matchups", structuredWithoutMetadata(matchups)),
                        Updates.setOnInsert("_id", new ObjectId())),
                new UpdateOptions().upsert(true));
        return result.wasAcknowledged();
    }

    public static boolean upsertProfileRecords(String puuid, Filter filter, List<ProfileRecord> records) {
        if (puuid == null || puuid.isBlank() || filter == null) return false;
        String filterKey = filter.toSummonerKey();
        List<ProfileRecord> values = records == null ? List.of() : records;
        List<String> metrics = new ArrayList<>(values.size());
        List<WriteModel<Document>> operations = new ArrayList<>(values.size());
        for (ProfileRecord record : values) {
            if (record == null || record.metric == null) continue;
            metrics.add(record.metric.name());
            Document document = JsonCodec.toDocument(record);
            List<Bson> updates = new ArrayList<>(document.size() + 3);
            updates.add(Updates.set("puuid", puuid));
            updates.add(Updates.set("filterKey", filterKey));
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                if ("_id".equals(entry.getKey()) || "puuid".equals(entry.getKey()) || "filterKey".equals(entry.getKey())
                        || "riotId".equals(entry.getKey()) || "icon".equals(entry.getKey())) continue;
                if (entry.getValue() == null) updates.add(Updates.unset(entry.getKey()));
                else updates.add(Updates.set(entry.getKey(), entry.getValue()));
            }
            for (String optional : List.of("mmr", "team", "actorPuuid", "gameShared"))
                if (!document.containsKey(optional)) updates.add(Updates.unset(optional));
            updates.add(Updates.unset("riotId"));
            updates.add(Updates.unset("icon"));
            updates.add(Updates.setOnInsert("_id", new ObjectId()));
            operations.add(new UpdateOneModel<>(Filters.and(
                    Filters.eq("puuid", puuid),
                    Filters.eq("filterKey", filterKey),
                    Filters.eq("metric", record.metric.name())), Updates.combine(updates), new UpdateOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(profileRecords(), operations);
        Bson identity = Filters.and(Filters.eq("puuid", puuid), Filters.eq("filterKey", filterKey));
        profileRecords().deleteMany(metrics.isEmpty() ? identity : Filters.and(identity, Filters.nin("metric", metrics)));
        return true;
    }

    public static boolean upsertProfileStatistics(String puuid, long seasonStart, ProfileStatistics statistics) {
        return upsertProfileStatistics(puuid, Filter.summoner(seasonStart, 0), statistics);
    }

    public static boolean deleteProfileStatistics(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return false;
        return profileStatistics().deleteOne(Filters.and(
                Filters.eq("puuid", puuid), Filters.eq("filterKey", filter.toSummonerKey()))).getDeletedCount() > 0;
    }

    public static boolean deleteProfileStatistics(String puuid, long seasonStart) {
        return deleteProfileStatistics(puuid, Filter.summoner(seasonStart, 0));
    }

    public static boolean upsertChampionBuild(Build build) {
        if (build == null || build.filter() == null) return false;
        Document document = buildDocument(build);
        replace(builds(), document);
        return true;
    }

        public static boolean upsertChampionBuilds(List<Build> builds) {
        if (builds == null || builds.isEmpty()) return false;
        List<WriteModel<Document>> operations = new ArrayList<>(builds.size());
        for (Build build : builds) if (build != null && build.filter() != null) {
            Document document = buildDocument(build);
            operations.add(new ReplaceOneModel<>(Filters.eq("_id", document.get("_id")), document,
                    new ReplaceOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(builds(), operations);
        return true;
    }

    // New shape: 1 doc per scope (queue|rankBehavior|rank|patch|region) with lanes inside
    public static boolean upsertChampionStatsDocument(com.safjnest.lol.model.statistics.ChampionStatsDocument doc) {
        if (doc == null || doc.scope == null) return false;
        doc._id = doc.scope.toKey();
        doc.updatedAt = System.currentTimeMillis();
        doc.ready = true;
        Document document = new Document("_id", doc._id)
            .append("scope", new Document("queue", doc.scope.queue() == null ? null : doc.scope.queue().name())
                .append("rank", doc.scope.rank() == null ? null : doc.scope.rank().name())
                .append("rankBehavior", doc.scope.rankBehavior().name())
                .append("patch", doc.scope.patch())
                .append("region", doc.scope.region() == null ? null : doc.scope.region().name()))
            .append("games", doc.games)
            .append("banGames", doc.banGames)
            .append("previousPatch", doc.previousPatch)
            .append("ready", true)
            .append("updatedAt", doc.updatedAt)
            .append("champions", write(doc.champions));
        replace(championStats(), document);
        return true;
    }

    public static com.safjnest.lol.model.statistics.ChampionStatsDocument findChampionStatsDocument(com.safjnest.lol.model.statistics.shared.ChampionStatsScope scope) {
        if (scope == null) return null;
        Document doc = championStats().find(Filters.eq("_id", scope.toKey())).first();
        if (doc == null) return null;
        return readStructured(doc, com.safjnest.lol.model.statistics.ChampionStatsDocument.class);
    }

    public static void upsertChampionIndexables(String patch, List<ChampionIndexable> values) {
        String majorPatch = patchMajor(patch);
        if (majorPatch == null || majorPatch.isBlank() || values == null) return;

        Map<String, Document> previous = new HashMap<>();
        for (Document document : championIndexables().find()) {
            Object id = document.get("_id");
            if (id != null) previous.put(String.valueOf(id), document);
        }

        long now = System.currentTimeMillis();
        Set<String> ids = new HashSet<>();
        List<WriteModel<Document>> operations = new ArrayList<>();
        for (ChampionIndexable value : values) {
            if (value == null || value.champion() == 0 || value.role() == null) continue;
            String id = value.champion() + "_" + value.role().name();
            Document old = previous.get(id);
            boolean changed = old == null || old.getBoolean("indexable", false) != value.indexable();
            long previousUpdate = old == null ? 0L : number(old.get("lastUpdate"));
            long lastUpdate = changed
                    ? previousUpdate == Long.MAX_VALUE ? now : Math.max(now, previousUpdate + 1)
                    : previousUpdate == 0L ? now : previousUpdate;
            Document document = new Document("_id", id)
                    .append("patchMajor", majorPatch)
                    .append("championId", value.champion())
                    .append("role", value.role().name())
                    .append("games", value.games())
                    .append("indexable", value.indexable())
                    .append("lastUpdate", lastUpdate);
            ids.add(id);
            operations.add(new ReplaceOneModel<>(Filters.eq("_id", id), document,
                    new ReplaceOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(championIndexables(), operations);
        championIndexables().deleteMany(ids.isEmpty() ? new Document() : Filters.nin("_id", ids));
    }

    public static <T> T read(QueryRecord record, Class<T> type) {
        if (record == null) return null;
        try {
            Object value = switch (type.getName()) {
                case "com.safjnest.lol.model.summoner.Summoner" -> readSummoner(record);
                case "com.safjnest.lol.model.summoner.Rank" -> readRank(record);
                case "com.safjnest.lol.model.summoner.Mastery" -> readMastery(record);
                case "com.safjnest.lol.model.match.Participant" -> readParticipant(record);
                case "com.safjnest.lol.model.match.Match" -> readMatch(record);
                case "com.safjnest.lol.model.match.MatchResult" -> readMatchResult(record);
                case "com.safjnest.lol.model.statistics.ProfileStatistics" -> readProfileStatistics(QueryRecordParser.toDocument(record));
                case "com.safjnest.lol.model.Build" -> readBuild(QueryRecordParser.toDocument(record));
                default -> readStructured(QueryRecordParser.toDocument(record), type);
            };
            return type.cast(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to read Mongo record id=" + record.getAsString("_id")
                    + " as " + type.getName(), exception);
        }
    }

    private static Document write(Object value) {
        if (value == null) throw new IllegalArgumentException("Mongo value cannot be null");
        Document document;
        if (value instanceof Summoner summoner) {
            if (summoner.puuid() == null || summoner.puuid().isBlank()) throw new IllegalArgumentException("Summoner.puuid is required");
            document = new Document("_id", summoner.puuid()).append("level", summoner.level()).append("icon", summoner.icon());
            putIfNotNull(document, "riotId", summoner.riotId());
            putIfNotNull(document, "region", summoner.region() == null ? null : summoner.region().name());
            putIfNotNull(document, "userId", summoner.userId());
            if (summoner.tracking()) document.put("tracking", true);
            document.put("ranks", writeRanks(summoner.ranks()));
            if (!summoner.masteries().isEmpty()) document.put("masteries", writeMasteries(summoner.masteries()));
        } else if (value instanceof Rank rank) {
            document = new Document("rank", rank.tier() == null ? null : rank.tier().name()).append("lp", rank.lp())
                    .append("wins", rank.wins()).append("losses", rank.losses());
        } else if (value instanceof Mastery mastery) {
            document = new Document("championId", mastery.championId()).append("level", mastery.level()).append("points", mastery.points());
        } else if (value instanceof Participant participant) {
            document = participantDocument(participant);
        } else if (value instanceof Match match) {
            document = matchDocument(match);
        } else if (value instanceof MatchResult matchResult) {
            document = matchResultDocument(matchResult);
        } else {
            document = structured(value);
            if (document == null) throw new IllegalArgumentException("Unable to serialize " + value.getClass().getName());
        }
        return document;
    }

    private static Summoner readSummoner(QueryRecord record) {
        String puuid = record.getAsString("puuid");
        if (puuid == null) puuid = record.getAsString("_id");
        Map<GameQueueType, Rank> ranks = ranks(QueryRecordParser.toDocument(record));
        List<Mastery> masteries = new ArrayList<>();
        for (QueryRecord mastery : record.getAsRecords("masteries")) masteries.add(readMastery(mastery));
        return Summoner.hydrated(puuid, record.getAsString("riotId"),
                parseShard(record.getAsString("region")), record.getAsInt("level"), record.getAsInt("icon"),
                record.getAsString("userId"), record.getAsBoolean("tracking"), ranks, masteries);
    }

    private static Rank readRank(QueryRecord record) {
        return new Rank(record.getAsEnum("rank", TierDivisionType.class),
                record.getAsInt("lp"), record.getAsInt("wins"), record.getAsInt("losses"));
    }

    private static Mastery readMastery(QueryRecord record) {
        return new Mastery(record.getAsInt("championId"), record.getAsInt("level"), record.getAsInt("points"));
    }

    private static Document writeRanks(Map<GameQueueType, Rank> ranks) {
        Document result = new Document();
        if (ranks != null) for (Map.Entry<GameQueueType, Rank> entry : ranks.entrySet()) {
            GameQueueType queue = entry.getKey() == null ? null : GameQueueTypeUtils.canonicalQueue(entry.getKey());
            Rank rank = entry.getValue();
            if (queue == null || rank == null) continue;
            result.put(queue.name(), write(rank));
        }
        return result;
    }

    private static List<Document> writeMasteries(List<Mastery> masteries) {
        List<Document> result = new ArrayList<>();
        if (masteries != null) for (Mastery mastery : masteries) if (mastery != null) result.add(write(mastery));
        return result;
    }

    private static Participant readParticipant(QueryRecord record) {
        Participant participant = new Participant();
        participant.id = record.getAsInt("id"); participant.win = record.getAsBoolean("win");
        participant.kda = record.getAsString("kda"); participant.kills = record.containsKey("kills") ? record.getAsInt("kills") : kdaValue(participant.kda, 0);
        participant.deaths = record.containsKey("deaths") ? record.getAsInt("deaths") : kdaValue(participant.kda, 1);
        participant.assists = record.containsKey("assists") ? record.getAsInt("assists") : kdaValue(participant.kda, 2);
        participant.champion = record.getAsInt("champion");
        participant.lane = record.getAsEnum("lane", no.stelar7.api.r4j.basic.constants.types.lol.LaneType.class);
        participant.team = record.getAsEnum("team", no.stelar7.api.r4j.basic.constants.types.lol.TeamType.class);
        participant.roleQuestId = record.getAsInt("roleQuestId"); participant.rankProgress = readRankProgress(record.getAsRecord("rankProgress"));
        participant.damage = record.getAsInt("damage"); participant.damageTaken = integerValue(record, "damageTaken");
        participant.damageBuilding = record.getAsInt("damageBuilding"); participant.healing = record.getAsInt("healing"); participant.cs = record.getAsInt("cs");
        participant.goldEarned = record.getAsInt("goldEarned"); participant.ward = record.getAsInt("ward"); participant.wardKilled = record.getAsInt("wardKilled");
        participant.visionScore = record.getAsInt("visionScore"); participant.pings = new HashMap<>(readIntegerMap(record, "pings"));
        participant.subTeam = record.getAsInt("subTeam"); participant.subTeamPlacement = record.getAsInt("subTeamPlacement");
        participant.puuid = record.getAsString("puuid"); participant.riotId = record.getAsString("riotId"); participant.riotTag = record.getAsString("riotTag");
        participant.championLevel = integerValue(record, "championLevel"); participant.doubles = record.getAsInt("doubles"); participant.triples = record.getAsInt("triples");
        participant.quadruples = record.getAsInt("quadruples"); participant.pentas = record.getAsInt("pentas");
        participant.item0 = record.getAsInt("item0"); participant.item1 = record.getAsInt("item1"); participant.item2 = record.getAsInt("item2");
        participant.item3 = record.getAsInt("item3"); participant.item4 = record.getAsInt("item4"); participant.item5 = record.getAsInt("item5"); participant.item6 = record.getAsInt("item6"); participant.turretKills = record.getAsInt("turretKills");
        participant.q = record.getAsInt("q"); participant.w = record.getAsInt("w"); participant.e = record.getAsInt("e"); participant.r = record.getAsInt("r");
        participant.d = record.getAsInt("d"); participant.f = record.getAsInt("f"); participant.summonerSpell1 = record.getAsInt("summonerSpell1"); participant.summonerSpell2 = record.getAsInt("summonerSpell2");
        participant.primaryRunes = readIntegerList(record, "primaryRunes"); participant.secondaryRunes = readIntegerList(record, "secondaryRunes");
        participant.statsRunes = readIntegerList(record, "statsRunes"); participant.skillOrder = readIntegerList(record, "skillOrder"); participant.augments = readIntegerList(record, "augments");
        participant.starterItems = readIntegerList(record, "starterItems"); participant.buildPath = readIntegerList(record, "buildPath"); participant.boots = record.getAsInt("boots"); participant.supportItem = record.getAsInt("supportItem");
        return participant;
    }

    private static Match readMatch(QueryRecord record) {
        Document source = QueryRecordParser.toDocument(record);
        String fullGameId = record.getAsString("_id"); if (fullGameId == null) fullGameId = record.getAsString("fullGameId");
        if (fullGameId == null || fullGameId.isBlank()) throw new IllegalArgumentException("Mongo match _id is required");
        Match match = Match.hydrated();
        match.gameId = fullGameId;
        String region = source.containsKey("region") ? record.getAsString("region") : null;
        match.leagueShard = region == null ? record.getAsEnum("leagueShard", LeagueShard.class) : parseShard(region);
        if (match.leagueShard == null && fullGameId.indexOf('_') > 0) match.leagueShard = parseShard(fullGameId.substring(0, fullGameId.indexOf('_')));
        match.queue = record.getAsEnum("queue", GameQueueType.class); match.rank = record.getAsEnum("rank", TierType.class);
        match.lastUpdate = record.getAsLong("lastUpdate"); match.timeStart = record.getAsLong("timeStart"); match.timeEnd = record.getAsLong("timeEnd"); match.patch = record.getAsString("patch");
        match.bans = readBans(record); match.participants = readParticipants(record);
        match.eventData = record.containsKey("events") ? readEventMap(record.getValue("events")) : new LinkedHashMap<>(); match.restoreEvents();
        return match;
    }

    private static MatchResult readMatchResult(QueryRecord record) {
        return new MatchResult(record.getAsString("gameId"), record.getAsEnum("queue", GameQueueType.class), record.getAsLong("timeStart"), record.getAsLong("timeEnd"),
                record.getAsBoolean("win"), record.getAsString("kda"), record.getAsInt("championId"), record.getAsEnum("lane", no.stelar7.api.r4j.basic.constants.types.lol.LaneType.class),
                record.getAsInt("damage"), record.getAsInt("cs"), record.getAsInt("gold"), record.getAsInt("vision"), record.getAsInt("teamKills"), readIntegerList(record, "items"), readIntegerList(record, "summonerSpells"),
                readIntegerList(record, "primaryRunes"), readIntegerList(record, "secondaryRunes"), readIntegerList(record, "statsRunes"), readParticipants(record));
    }

    private static RankProgress readRankProgress(QueryRecord record) {
        if (record == null) return null;
        TierDivisionType rank = record.getAsEnum("rank", TierDivisionType.class);
        if (rank == null) return null;
        Integer lp = integerValue(record, "lp");
        if (lp == null) return null;
        return new RankProgress(rank, lp, integerValue(record, "gain"),
                record.getAsEnum("previousRank", TierDivisionType.class), integerValue(record, "previousLp"));
    }

    private static boolean rankProgressEquals(RankProgress left, RankProgress right) {
        return left.rank == right.rank && Objects.equals(left.lp, right.lp)
                && Objects.equals(left.gain, right.gain) && left.previousRank == right.previousRank
                && Objects.equals(left.previousLp, right.previousLp);
    }

    private static Integer integerValue(QueryRecord record, String key) {
        return record != null && record.containsKey(key) && record.getValue(key) != null ? record.getAsInt(key) : null;
    }

    private static Document rankProgressDocument(RankProgress value) {
        if (!RankProgressUtils.hasCurrentSnapshot(value)) return null;
        Document document = new Document("rank", value.rank.name()).append("lp", value.lp);
        if (value.gain != null) document.append("gain", value.gain);
        if (value.previousRank != null) document.append("previousRank", value.previousRank.name());
        if (value.previousLp != null) document.append("previousLp", value.previousLp);
        return document;
    }

    public record RankProgressPage(String cursor, int processed, int updated) {
    }

    public record RankProgressSubject(String region, String puuid) {
        public static RankProgressSubject from(Document value) {
            Document id = value == null ? null : value.get("_id", Document.class);
            if (id == null || id.getString("region") == null || id.getString("puuid") == null)
                throw new IllegalArgumentException("Invalid RankProgress subject=" + value);
            return new RankProgressSubject(id.getString("region"), id.getString("puuid"));
        }

        public String cursor() {
            return region + "|" + puuid;
        }
    }

    private static Document participantDocument(Participant value) {
        if (value == null || value.puuid == null || value.puuid.isBlank()) throw new IllegalArgumentException("Participant.puuid is required for Mongo persistence");
        Document document = new Document("id", value.id).append("win", value.win).append("kills", value.kills).append("deaths", value.deaths).append("assists", value.assists).append("champion", value.champion).append("roleQuestId", value.roleQuestId)
                .append("damage", value.damage).append("damageBuilding", value.damageBuilding).append("healing", value.healing).append("cs", value.cs).append("goldEarned", value.goldEarned).append("ward", value.ward).append("wardKilled", value.wardKilled).append("visionScore", value.visionScore).append("pings", integerMapDocument(value.pings))
                .append("subTeam", value.subTeam).append("subTeamPlacement", value.subTeamPlacement).append("doubles", value.doubles).append("triples", value.triples).append("quadruples", value.quadruples).append("pentas", value.pentas)
                .append("item0", value.item0).append("item1", value.item1).append("item2", value.item2).append("item3", value.item3).append("item4", value.item4).append("item5", value.item5).append("item6", value.item6).append("turretKills", value.turretKills).append("q", value.q).append("w", value.w).append("e", value.e).append("r", value.r).append("d", value.d).append("f", value.f).append("summonerSpell1", value.summonerSpell1).append("summonerSpell2", value.summonerSpell2)
                .append("primaryRunes", integerList(value.primaryRunes)).append("secondaryRunes", integerList(value.secondaryRunes)).append("statsRunes", integerList(value.statsRunes)).append("skillOrder", integerList(value.skillOrder)).append("augments", integerList(value.augments)).append("starterItems", integerList(value.starterItems)).append("buildPath", integerList(value.buildPath)).append("boots", value.boots).append("supportItem", value.supportItem);
        putIfNotNull(document, "kda", value.kda); putIfNotNull(document, "puuid", value.puuid); putIfNotNull(document, "riotId", value.riotId); putIfNotNull(document, "riotTag", value.riotTag); putIfNotNull(document, "damageTaken", value.damageTaken); putIfNotNull(document, "championLevel", value.championLevel); putEnum(document, "lane", value.lane); putEnum(document, "team", value.team);
        Document rankProgress = rankProgressDocument(value.rankProgress);
        if (rankProgress != null) document.append("rankProgress", rankProgress);
        return document;
    }

    private static Document matchDocument(Match value) {
        if (value.leagueShard == null) throw new IllegalArgumentException("Match.leagueShard is required");
        Document document = new Document("_id", value.gameId).append("region", value.leagueShard.name())
                .append("lastUpdate", value.lastUpdate).append("timeStart", value.timeStart)
                .append("timeEnd", value.timeEnd).append("bans", writeBans(value.bans)).append("participants", writeParticipants(value.participants));
        putEnum(document, "queue", value.queue); putEnum(document, "rank", value.rank);
        putIfNotNull(document, "patch", value.patch); putIfNotNull(document, "patchMajor", patchMajor(value.patch));
        return document;
    }

    private static Document matchResultDocument(MatchResult value) {
        Document document = new Document("gameId", value.gameId).append("timeStart", value.timeStart).append("timeEnd", value.timeEnd).append("win", value.win).append("championId", value.championId).append("damage", value.damage).append("cs", value.cs).append("gold", value.gold).append("vision", value.vision).append("teamKills", value.teamKills).append("items", integerList(value.items)).append("summonerSpells", integerList(value.summonerSpells)).append("primaryRunes", integerList(value.primaryRunes)).append("secondaryRunes", integerList(value.secondaryRunes)).append("statsRunes", integerList(value.statsRunes)).append("participants", writeParticipants(value.participants));
        putEnum(document, "queue", value.queue); putEnum(document, "lane", value.lane); putIfNotNull(document, "kda", value.kda); return document;
    }

    private static String gameId(Document document) {
        String fullGameId = document.getString("_id");
        if (fullGameId != null) return fullGameId;
        String value = document.getString("game_id");
        if (value != null) return value;
        value = document.getString("gameId");
        return value != null ? value : document.getString("_id");
    }

    private static String patchMajor(String patch) {
        String value = patch == null ? null : patch.trim();
        if (value == null || value.isBlank()) return null;
        int firstSeparator = value.indexOf('.');
        if (firstSeparator < 0) return value;
        int secondSeparator = value.indexOf('.', firstSeparator + 1);
        return secondSeparator < 0 ? value : value.substring(0, secondSeparator);
    }

    private static String regionFromMatchId(String fullGameId) {
        if (fullGameId == null) return null;
        int separator = fullGameId.indexOf('_');
        return separator > 0 ? fullGameId.substring(0, separator) : null;
    }

    private static void putEnum(Document document, String field, Enum<?> value) {
        if (value != null) document.put(field, value.name());
    }

    private static void putIfNotNull(Document document, String field, Object value) {
        if (value != null) document.put(field, value);
    }

    private static List<Integer> integerList(List<Integer> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static List<Integer> readIntegerList(QueryRecord record, String field) {
        List<Integer> values = record.getAsList(field, Integer.class);
        List<Integer> result = new ArrayList<>(values.size());
        for (Integer value : values) {
            if (value == null) throw new IllegalArgumentException("Mongo field " + field + " contains a null integer");
            result.add(value);
        }
        return result;
    }

    private static Document integerMapDocument(Map<String, Integer> values) {
        Document document = new Document();
        if (values == null) return document;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) throw new IllegalArgumentException("Participant pings cannot contain null keys or values");
            document.put(entry.getKey(), entry.getValue());
        }
        return document;
    }

    private static Map<String, Integer> readIntegerMap(QueryRecord record, String field) {
        QueryRecord nested = record.getAsRecord(field);
        if (nested == null) return new LinkedHashMap<>();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : nested.keySet()) result.put(key, nested.getAsInt(key));
        return result;
    }

    private static Document writeBans(Map<no.stelar7.api.r4j.basic.constants.types.lol.TeamType, List<Integer>> bans) {
        Document document = new Document("BLUE", List.of()).append("RED", List.of());
        if (bans == null) return document;
        boolean legacyBlueRedOrdinals = bans.containsKey(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.SUBTEAM)
                && bans.containsKey(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE)
                && !bans.containsKey(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED);
        for (Map.Entry<no.stelar7.api.r4j.basic.constants.types.lol.TeamType, List<Integer>> entry : bans.entrySet()) {
            if (entry.getKey() == no.stelar7.api.r4j.basic.constants.types.lol.TeamType.SUBTEAM && legacyBlueRedOrdinals) {
                document.put("BLUE", integerList(entry.getValue()));
                continue;
            }
            if (entry.getKey() == no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE && legacyBlueRedOrdinals) {
                document.put("RED", integerList(entry.getValue()));
                continue;
            }
            if (entry.getKey() == null || (entry.getKey() != no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE && entry.getKey() != no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED)) {
                throw new IllegalArgumentException("Match bans support only BLUE and RED");
            }
            document.put(entry.getKey().name(), integerList(entry.getValue()));
        }
        return document;
    }

    private static Map<no.stelar7.api.r4j.basic.constants.types.lol.TeamType, List<Integer>> readBans(QueryRecord record) {
        QueryRecord nested = record.getAsRecord("bans");
        Map<no.stelar7.api.r4j.basic.constants.types.lol.TeamType, List<Integer>> result = new HashMap<>();
        result.put(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE, List.of());
        result.put(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED, List.of());
        if (nested == null) return result;
        for (String key : nested.keySet()) {
            no.stelar7.api.r4j.basic.constants.types.lol.TeamType team;
            try {
                team = no.stelar7.api.r4j.basic.constants.types.lol.TeamType.valueOf(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid Mongo bans field bans." + key, exception);
            }
            if (team != no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE && team != no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED) throw new IllegalArgumentException("Invalid Mongo bans team " + key);
            result.put(team, readIntegerList(nested, key));
        }
        return result;
    }

    private static List<Document> writeParticipants(List<Participant> participants) {
        List<Document> result = new ArrayList<>();
        if (participants == null) return result;
        for (Participant participant : participants) {
            if (participant == null) throw new IllegalArgumentException("Match participants cannot contain null values");
            result.add(participantDocument(participant));
        }
        return result;
    }

    private static List<Participant> readParticipants(QueryRecord record) {
        List<Participant> result = new ArrayList<>();
        for (QueryRecord nested : record.getAsRecords("participants")) {
            if (nested == null) throw new IllegalArgumentException("Mongo participants cannot contain null values");
            result.add(readParticipant(nested));
        }
        return result;
    }

    private static String bansJson(Object value) {
        if (value == null) return "{}";
        if (value instanceof Document document) return document.toJson();
        if (value instanceof String string) {
            try {
                return new JSONObject(string).toString();
            } catch (RuntimeException ignored) {
                return "{}";
            }
        }
        return "{}";
    }

    private static int kdaValue(String kda, int index) {
        if (kda == null || kda.isBlank()) return 0;
        String[] values = kda.split("/");
        if (index < 0 || index >= values.length) return 0;
        try { return Integer.parseInt(values[index]); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static Map<String, Object> readEventMap(Object value) {
        if (value instanceof Document document) return new LinkedHashMap<>(document);
        if (value instanceof String json) {
            try { return new JSONObject(json).toMap(); }
            catch (RuntimeException exception) { throw new IllegalStateException("Invalid inline match event JSON", exception); }
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static void attachEvents(List<? extends Match> matches) {
        if (matches == null || matches.isEmpty()) return;
        List<String> ids = new ArrayList<>();
        Map<String, Match> byId = new HashMap<>();
        for (Match match : matches) {
            if (match == null || match.gameId == null || match.leagueShard == null) continue;
            ids.add(match.gameId);
            byId.put(match.gameId, match);
        }
        if (ids.isEmpty()) return;
        try (MongoCursor<Document> cursor = matchEvents().find(Filters.in("_id", ids)).iterator()) {
            while (cursor.hasNext()) {
                Document event = cursor.next();
                String id = event.getString("_id");
                Match match = byId.get(id);
                if (match == null) continue;
                match.eventData = decodeMatchEvents(event);
                match.restoreEvents();
            }
        }
    }

    private static void flushProfileRecordMatches(List<Match> matches, Consumer<Match> consumer) {
        if (matches.isEmpty()) return;
        try {
            attachEvents(matches);
            for (Match match : matches) {
                try {
                    consumer.accept(match);
                } finally {
                    MatchMemoryUtils.release(match);
                }
            }
        } finally {
            MatchMemoryUtils.release(matches);
        }
    }

    private static Map<String, Object> decodeMatchEvents(Document document) {
        String data = decodeMatchEventsJson(document);
        if (data.isEmpty()) return new LinkedHashMap<>();
        try {
            return new JSONObject(data).toMap();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid compressed match event JSON id=" + document.get("_id"), exception);
        }
    }

    private static String decodeMatchEventsJson(Document document) {
        if (document == null) return "";
        String encoding = document.getString("encoding");
        if (!"json".equals(encoding)) throw new IllegalStateException("Unsupported match event encoding=" + encoding + " id=" + document.get("_id"));
        String data = document.getString("data");
        int size = document.getInteger("uncompressedBytes", 0);
        if (data == null || size < 0) throw new IllegalStateException("Invalid match events id=" + document.get("_id"));
        byte[] decoded = data.getBytes(StandardCharsets.UTF_8);
        if (decoded.length != size) throw new IllegalStateException("Match event size mismatch id=" + document.get("_id"));
        if (!sha256(decoded).equals(document.getString("checksum"))) throw new IllegalStateException("Match event checksum mismatch id=" + document.get("_id"));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static byte[] eventJson(Map<String, Object> events) {
        try {
            return JsonCodec.toJson(events == null ? Map.of() : events).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to serialize match events", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item));
        return result.toString();
    }

    private static MatchResult toMatchResult(Match match, String puuid) {
        List<Participant> participants = match.participants == null ? List.of() : match.participants;
        Participant player = null;
        for (Participant participant : participants) if (participant != null && puuid != null && puuid.equals(participant.puuid)) { player = participant; break; }
        if (player == null) return null;
        int teamKills = 0;
        for (Participant participant : participants) if (participant != null && participant.team == player.team) teamKills += kills(participant.kda);
        return new MatchResult(match.gameId, match.queue, match.timeStart, match.timeEnd, player.win, player.kda, player.champion, player.lane, player.damage, player.cs, player.goldEarned, player.visionScore, teamKills,
                List.of(player.item0, player.item1, player.item2, player.item3, player.item4, player.item5, player.item6), List.of(player.summonerSpell1, player.summonerSpell2),
                player.primaryRunes, player.secondaryRunes, player.statsRunes, participants);
    }

    private static int kills(String kda) {
        if (kda == null || kda.isBlank()) return 0;
        try { return Integer.parseInt(kda.split("/", 2)[0]); } catch (RuntimeException ignored) { return 0; }
    }

    private static void traceRead(String operation, String details) {
        //if (App.isTesting()) BotLogger.trace("[MONGO] " + operation + " " + details);
    }

    public static long estimatedMatchCount() {
        try {
            return matches().estimatedDocumentCount();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static long estimatedSummonerCount() {
        try {
            return summoners().estimatedDocumentCount();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static long totalMasteriesCount() {
        try {
            Document result = summoners().aggregate(List.of(
                new Document("$project", new Document("count", new Document("$size", new Document("$ifNull", List.of("$masteries", List.of()))))),
                new Document("$group", new Document("_id", null).append("total", new Document("$sum", "$count")))
            )).first();
            return result == null ? 0 : number(result, "total");
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static Map<String, Long> rankTotalsByQueue() {
        try {
            Map<String, Long> totals = new LinkedHashMap<>();
            for (Document entry : summoners().aggregate(List.of(
                new Document("$project", new Document("queues", new Document("$objectToArray", "$ranks"))),
                new Document("$unwind", "$queues"),
                new Document("$group", new Document("_id", "$queues.k").append("total", new Document("$sum", 1)))
            ))) {
                String queue = entry.getString("_id");
                if (queue != null && !queue.isBlank()) totals.put(queue, number(entry, "total"));
            }
            return totals.isEmpty() ? Map.of() : Map.copyOf(totals);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static MongoCollection<Document> matches() {
        return database().getCollection("match");
    }

    private static MongoCollection<Document> matchEvents() {
        return database().getCollection("match_events");
    }

    private static MongoCollection<Document> profileStatistics() {
        return database().getCollection("profile_statistics");
    }

    private static MongoCollection<Document> profileActivity() {
        return database().getCollection("profile_activity");
    }

    private static MongoCollection<Document> profileMatchups() {
        return database().getCollection("profile_matchups");
    }

    private static MongoCollection<Document> profileRecords() {
        return database().getCollection(PROFILE_RECORDS_COLLECTION);
    }

    private static MongoCollection<Document> builds() {
        return database().getCollection("champion_builds");
    }

    private static MongoCollection<Document> championStats() {
        return database().getCollection("champion_stats");
    }

    private static MongoCollection<Document> championIndexables() {
        return database().getCollection(CHAMPION_INDEXABLES_COLLECTION);
    }

    private static MongoCollection<Document> profileIndexables() {
        return database().getCollection(PROFILE_INDEXABLES_COLLECTION);
    }

    private static QueryRecord matchRecord(Document document) {
        return QueryRecordParser.fromDocument(document);
    }

    private static Map<String, Object> aiTrainingSample(Document match, String side, List<Map<String, Object>> participants) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("gameId", match.getString("_id"));
        sample.put("patch", match.getString("patch"));
        sample.put("queue", match.getString("queue"));
        sample.put("startedAt", match.getLong("timeStart"));
        sample.put("side", side);
        sample.put("win", aiTrainingWin(match, side));
        sample.put("participants", participants);
        return sample;
    }

    private static boolean aiTrainingWin(Document match, String side) {
        for (Document participant : documents(match.get("participants")))
            if (side.equals(participant.getString("team"))) return participant.getBoolean("win", false);
        return false;
    }

    private static List<Map<String, Object>> aiTrainingParticipants(Document match, String side) {
        Map<String, Integer> champions = new LinkedHashMap<>();
        for (Document participant : documents(match.get("participants"))) {
            if (!side.equals(participant.getString("team"))) continue;
            String role = aiTrainingRole(participant.getString("lane"));
            Integer championId = participant.getInteger("champion");
            if (role == null || championId == null || championId <= 0 || champions.putIfAbsent(role, championId) != null) return null;
        }
        if (champions.size() != 5) return null;

        List<Map<String, Object>> participants = new ArrayList<>();
        for (String role : List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT")) {
            Integer championId = champions.get(role);
            if (championId == null) return null;
            Map<String, Object> participant = new LinkedHashMap<>();
            participant.put("championId", championId);
            participant.put("role", role);
            participants.add(participant);
        }
        return participants;
    }

    private static String aiTrainingRole(String lane) {
        if (lane == null) return null;
        return switch (lane) {
            case "TOP" -> "TOP";
            case "JUNGLE" -> "JUNGLE";
            case "MID" -> "MID";
            case "BOT" -> "ADC";
            case "UTILITY" -> "SUPPORT";
            default -> null;
        };
    }

    private static QueryRecord profileRecord(Document document) {
        return QueryRecordParser.fromDocument(document);
    }

    private static org.bson.conversions.Bson matchFilter(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        List<org.bson.conversions.Bson> filters = new ArrayList<>();
        if (puuid != null && !puuid.isBlank()) {
            filters.add(Filters.elemMatch("participants", Filters.eq("puuid", puuid)));
        }
        if (shard != null) filters.add(Filters.eq("region", shard.name()));
        if (queue != null) filters.add(Filters.eq("queue", queue.name()));
        if (timeStart != 0) filters.add(Filters.gte("timeStart", timeStart));
        if (timeEnd != 0) filters.add(Filters.lte("timeEnd", timeEnd));
        return Filters.and(filters);
    }

    private static Bson buildMatchFilter(String puuid, LeagueShard shard, Filter filter, long afterTime, long untilTime) {
        List<Bson> filters = new ArrayList<>();
        LeagueShard selectedShard = filter.region() != null ? filter.region() : shard;
        if (selectedShard != null) filters.add(Filters.eq("region", selectedShard.name()));
        if (filter.queue() != null) filters.add(Filters.eq("queue", filter.queue().name()));

        long start = filter.timeStart() == 0 ? afterTime : Math.max(filter.timeStart(), afterTime);
        long end = filter.timeEnd() == 0 ? untilTime : untilTime == 0 ? filter.timeEnd() : Math.min(filter.timeEnd(), untilTime);
        if (start != 0) filters.add(Filters.gte("timeStart", start));
        if (end != 0) filters.add(Filters.lte("timeEnd", end));
        if (filter.patch() != null) filters.add(patchMajorFilter(filter.patch()));
        if (filter.rank() != null) filters.add(rankFilter(filter));

        List<Bson> participantFilters = new ArrayList<>();
        participantFilters.add(Filters.eq("puuid", puuid));
        if (filter.champion() != 0) participantFilters.add(Filters.eq("champion", filter.champion()));
        if (filter.lane() != null) participantFilters.add(Filters.eq("lane", filter.lane().name()));
        filters.add(Filters.elemMatch("participants", Filters.and(participantFilters)));
        if (filter.opponent() != 0) filters.add(Filters.elemMatch("participants", Filters.eq("champion", filter.opponent())));
        if (filter.duo() != 0) filters.add(Filters.elemMatch("participants", Filters.eq("champion", filter.duo())));
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static Bson rankFilter(Filter filter) {
        if (filter.rankBehavior() == Filter.RankBehavior.EXACT) return Filters.eq("rank", filter.rank().name());
        List<String> ranks = new ArrayList<>();
        for (TierType tier : TierType.values()) if (tier.ordinal() <= filter.rank().ordinal()) ranks.add(tier.name());
        return Filters.in("rank", ranks);
    }

    private static Bson matchResultProjection() {
        return Projections.include(
                "_id", "queue", "timeStart", "timeEnd",
                "participants.puuid", "participants.riotId", "participants.riotTag", "participants.championLevel",
                "participants.win", "participants.kda", "participants.champion", "participants.lane",
                "participants.team", "participants.rankProgress", "participants.damage", "participants.cs", "participants.goldEarned",
                "participants.visionScore", "participants.item0", "participants.item1", "participants.item2",
                "participants.item3", "participants.item4", "participants.item5", "participants.item6",
                "participants.summonerSpell1", "participants.summonerSpell2", "participants.primaryRunes",
                "participants.secondaryRunes", "participants.statsRunes");
    }

    private static Bson rankHistoryProjection() {
        return Projections.include("_id", "queue", "patch", "timeStart", "timeEnd",
                "participants.puuid", "participants.win", "participants.champion", "participants.lane",
                "participants.team", "participants.rankProgress");
    }

    private static Bson profileStatisticsMatchProjection() {
        return Projections.include(
                "_id", "region", "queue", "rank", "lastUpdate", "timeStart", "timeEnd", "patch", "patchMajor", "participants");
    }

    private static Bson championMatchFilter(Filter filter, String puuid) {
        List<Bson> filters = new ArrayList<>();
        if (filter != null) {
            List<Bson> participantFilters = new ArrayList<>();
            if (filter.champion() != 0) participantFilters.add(Filters.eq("champion", filter.champion()));
            if (filter.lane() != null && GameQueueTypeUtils.hasLane(filter.queue())) {
                participantFilters.add(Filters.eq("lane", filter.lane().name()));
            }
            if (!participantFilters.isEmpty()) filters.add(Filters.elemMatch("participants", Filters.and(participantFilters)));
            if (filter.queue() != null) filters.add(Filters.eq("queue", filter.queue().name()));
            if (filter.patch() != null) filters.add(patchMajorFilter(filter.patch()));
            if (filter.region() != null) filters.add(Filters.eq("region", filter.region().name()));
            if (filter.rank() != null) filters.add(rankFilter(filter));
        }
        if (puuid != null) filters.add(Filters.elemMatch("participants", Filters.eq("puuid", puuid)));
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static Document championRawProjection() {
        return new Document("_id", 1)
                .append("region", 1)
                .append("rank", 1)
                .append("bans", 1)
                .append("timeStart", 1)
                .append("timeEnd", 1)
                .append("participants.champion", 1)
                .append("participants.lane", 1)
                .append("participants.win", 1)
                .append("participants.team", 1)
                .append("participants.kda", 1)
                .append("participants.cs", 1)
                .append("participants.goldEarned", 1)
                .append("participants.puuid", 1);
    }

    private static Document championRawWithBuildProjection() {
        return championRawProjection()
            .append("participants.starterItems", 1)
            .append("participants.boots", 1)
            .append("participants.supportItem", 1)
            .append("participants.item0", 1)
            .append("participants.item1", 1)
            .append("participants.item2", 1)
            .append("participants.item3", 1)
            .append("participants.item4", 1)
            .append("participants.item5", 1)
            .append("participants.skillOrder", 1)
            .append("participants.augments", 1)
            .append("participants.summonerSpell1", 1)
            .append("participants.summonerSpell2", 1)
            .append("participants.primaryRunes", 1)
            .append("participants.secondaryRunes", 1)
            .append("participants.statsRunes", 1);
    }

    private static Bson patchMajorFilter(String patch) {
        return Filters.eq("patchMajor", patchMajor(patch));
    }

    private static Bson profileIndexableFilter() {
        List<Bson> ranks = new ArrayList<>();
        for (GameQueueType queue : List.of(GameQueueType.RANKED_SOLO_5X5, GameQueueType.RANKED_FLEX_SR))
            ranks.add(Filters.in(rankPath(queue) + ".rank", INDEXABLE_PROFILE_RANKS));
        return Filters.or(Filters.eq("tracking", true), Filters.or(ranks));
    }

    private static List<String> playableRoleNames() {
        List<String> result = new ArrayList<>(LaneTypeUtils.playables().size());
        for (LaneType role : LaneTypeUtils.playables()) result.add(role.name());
        return result;
    }

    static Bson competitiveFilter(TierType rank, GameQueueType queue, String region, LaneType role) {
        return competitiveFilter(rank, queue, region, role, null);
    }

    static Bson competitiveFilter(TierType rank, GameQueueType queue, String region, LaneType role, Integer otpChampionId) {
        List<Bson> filters = new ArrayList<>();
        if (queue != null) filters.add(Filters.eq("queue", GameQueueTypeUtils.canonicalQueue(queue).name()));
        if (region != null && !"GLOBAL".equals(region)) filters.add(Filters.eq("region", region));
        if (role != null) filters.add(Filters.eq("primary", role.name()));
        if (otpChampionId != null) filters.add(Filters.eq("otpChampionId", otpChampionId));
        if (rank != null) {
            TierDivisionUtils.MmrRange range = TierDivisionUtils.getMmrRange(rank);
            filters.add(Filters.gte("mmr", range.minimum()));
            if (range.maximum() != null) filters.add(Filters.lt("mmr", range.maximum()));
        }
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static List<QueryRecord> matchProjections(List<String> fullGameIds, boolean participantsOnly) {
        if (fullGameIds == null || fullGameIds.isEmpty()) return List.of();
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.in("_id", fullGameIds))) {
            if (participantsOnly) {
                document = new Document("_id", document.get("_id"))
                        .append("participants", document.get("participants"));
            }
            result.add(matchRecord(document));
        }
        return result;
    }

    private static List<Filter> readFilters(MongoCollection<Document> collection, boolean generic) {
        Map<String, Filter> result = new LinkedHashMap<>();
        for (Document document : collection.find().projection(Projections.include("filterKey"))) {
            String key = document.getString("filterKey");
            if (key == null) continue;
            try {
                Filter filter = generic ? Filter.fromGenericKey(key) : Filter.fromKey(key);
                result.putIfAbsent(key, filter);
            } catch (RuntimeException ignored) {
                // Invalid historical filters are ignored; they are reported by migration verification.
            }
        }
        return new ArrayList<>(result.values());
    }

    private static void readProfileRefreshFilters(
            MongoCollection<Document> collection,
            String puuid,
            Map<String, Filter> filters) {
        for (Document document : collection.find(Filters.eq("puuid", puuid))
                .projection(Projections.include("filterKey"))) {
            String key = document.getString("filterKey");
            if (key == null || filters.containsKey(key)) continue;
            try {
                filters.put(key, Filter.fromSummonerKey(key));
            } catch (RuntimeException ignored) {
                // Invalid historical filters are ignored; they are reported by migration verification.
            }
        }
    }

    private static List<Filter> findChampionSourceFilters(String patch, boolean includeChampion) {
        if (patch == null || patch.isBlank()) return List.of();
        Map<String, Filter> result = new LinkedHashMap<>();
        for (Document match : matches().find(Filters.eq("patchMajor", patchMajor(patch)))
                .projection(Projections.include("queue", "rank", "region", "participants.champion", "participants.lane"))) {
            GameQueueType queue = enumValue(GameQueueType.class, match.getString("queue"));
            TierType rank = enumValue(TierType.class, match.getString("rank"));
            LeagueShard region = enumValue(LeagueShard.class, match.getString("region"));
            for (Document participant : documents(match.get("participants"))) {
                Filter filter = new Filter()
                        .setPatch(patch)
                        .setQueue(queue)
                        .setRank(rank)
                        .setRegion(region);
                if (includeChampion) filter.setChampion(participant.getInteger("champion", 0));
                if (GameQueueTypeUtils.hasLane(queue)) filter.setLane(enumValue(LaneType.class, participant.getString("lane")));
                result.putIfAbsent(includeChampion ? filter.toKey() : filter.genericKey(), filter);
            }
        }
        return new ArrayList<>(result.values());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static Summoner summoner(Document document) {
        String puuid = puuid(document);
        return Summoner.hydrated(puuid, document.getString("riotId"),
                parseShard(document.getString("region")), document.getInteger("level", 0), document.getInteger("icon", 0),
                document.getString("userId"), document.getBoolean("tracking", false), ranks(document), masteries(document));
    }

    private static String puuid(Document document) {
        String puuid = document.getString("puuid");
        return puuid == null ? document.getString("_id") : puuid;
    }

    private static Rank soloRank(Document document) {
        return ranks(document).get(GameQueueType.RANKED_SOLO_5X5);
    }

    private static Map<GameQueueType, Rank> ranks(Document document) {
        Object value = document.get("ranks");
        if (value instanceof Document values) return objectRanks(values);
        if (!(value instanceof List<?> values)) return Map.of();
        // TODO remove legacy ranks array compatibility after Mongo migration
        Map<GameQueueType, Rank> result = new LinkedHashMap<>();
        for (Object item : values) if (item instanceof Document rank) {
            GameQueueType queue = queue(rank.getString("queue"));
            if (queue != null) result.put(queue, rankFromDocument(rank));
        }
        return result;
    }

    private static Map<GameQueueType, Rank> objectRanks(Document values) {
        Map<GameQueueType, Rank> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            GameQueueType queue = queue(entry.getKey());
            if (queue != null && entry.getValue() instanceof Document rank) result.put(queue, rankFromDocument(rank));
        }
        return result;
    }

    private static Rank rankFromDocument(Document document) {
        return new Rank(division(document.getString("rank")), document.getInteger("lp", 0),
                document.getInteger("wins", 0), document.getInteger("losses", 0));
    }

    private static List<Mastery> masteries(Document document) {
        Object value = document.get("masteries");
        if (!(value instanceof List<?> values)) return List.of();
        List<Mastery> result = new ArrayList<>(values.size());
        for (Object item : values) if (item instanceof Document mastery) {
            result.add(new Mastery(mastery.getInteger("championId", 0), mastery.getInteger("level", 0),
                    mastery.getInteger("points", 0)));
        }
        return result;
    }

    private static List<String> boundedIds(List<String> ids) {
        List<String> result = new ArrayList<>(Math.min(MAX_BATCH_IDS, ids.size()));
        for (String id : ids) {
            if (id != null && !id.isBlank()) result.add(id);
            if (result.size() == MAX_BATCH_IDS) break;
        }
        return result;
    }

    private static long number(Document document, String field) {
        Object value = document.get(field);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static ChampionTierSource tierSource(boolean ready, long lastUpdate, Object value) {
        Map<Integer, ChampionTierSource.Champion> champions = new LinkedHashMap<>();
        if (value instanceof List<?> entries) for (Object entry : entries) if (entry instanceof Document document) {
            int champion = integer(document.get("championId"));
            ChampionTierSource.Champion source = tierChampion(document);
            if (champion != 0 && source != null) champions.put(champion, source);
        }
        return new ChampionTierSource(ready, lastUpdate, champions);
    }

    private static ChampionTierSource.Champion tierChampion(Object value) {
        if (!(value instanceof Document document)) return null;
        Document overview = document.get("overview", Document.class);
        if (overview == null) return null;
        ChampionTierList.Statistics statistics = new ChampionTierList.Statistics(
            integer(overview.get("games")),
            integer(overview.get("picks")),
            integer(overview.get("bans")),
            integer(overview.get("wins")),
            decimal(overview.get("winrate")),
            decimal(overview.get("pickrate")),
            overview.containsKey("banrate") && overview.get("banrate") != null ? decimal(overview.get("banrate")) : null
        );
        List<ChampionTierSource.Matchup> matchups = new ArrayList<>();
        Object rawMatchups = document.get("matchups");
        if (rawMatchups instanceof List<?> entries) for (Object entry : entries) if (entry instanceof Document matchup) {
            int champion = integer(matchup.get("champion"));
            int games = integer(matchup.get("games"));
            if (champion != 0 && games > 0) matchups.add(new ChampionTierSource.Matchup(champion, games,
                integer(matchup.get("wins"))));
        }
        if (rawMatchups instanceof Document entries) for (Object entry : entries.values()) if (entry instanceof Document matchup) {
            int champion = integer(matchup.get("champion"));
            int games = integer(matchup.get("matches"));
            if (champion != 0 && games > 0) matchups.add(new ChampionTierSource.Matchup(champion, games,
                integer(matchup.get("wins"))));
        }
        return new ChampionTierSource.Champion(statistics, matchups);
    }

    private static int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (!(value instanceof String string)) return 0;
        try { return Integer.parseInt(string); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static TierDivisionType division(String value) {
        if (value == null) return null;
        try { return TierDivisionType.valueOf(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static LeagueShard parseShard(String value) {
        if (value == null) return LeagueShard.UNKNOWN;
        try { return LeagueShard.valueOf(value); }
        catch (IllegalArgumentException ignored) { return LeagueShard.UNKNOWN; }
    }

    private static ProfileStatistics readProfileStatistics(Document document) {
        Object legacyStatistics = document.get("statistics");
        if (legacyStatistics != null) return readStructured(legacyStatistics, ProfileStatistics.class);

        Document values = new Document(document);
        values.remove("_id");
        values.remove("puuid");
        values.remove("filterKey");
        values.remove("seasonStart");
        return readStructured(values, ProfileStatistics.class);
    }

    private static ProfileActivity readProfileActivity(Document document) {
        return readStructured(document.get("activity"), ProfileActivity.class);
    }

    private static ProfileMatchups readProfileMatchups(Document document) {
        return readStructured(document.get("matchups"), ProfileMatchups.class);
    }

    private static ProfileRecord readProfileRecord(Document document) {
        if (document == null) return null;
        Document values = new Document(document);
        values.remove("_id");
        return readStructured(values, ProfileRecord.class);
    }

    private static Build readBuild(Document document) {
        return readStructured(document.get("build"), Build.class);
    }

    private static <T> T readStructured(Object value, Class<T> type) {
        return JsonCodec.fromDocument(value, type);
    }

    private static Document structured(Object value) {
        return JsonCodec.toDocument(value);
    }

    private static Document structuredWithoutMetadata(Object value) {
        Document document = structured(value);
        document.remove("metadata");
        return document;
    }

    private static Bson summonerUpdate(Summoner summoner, String userId) {
        Document fields = write(summoner);
        fields.remove("_id");
        List<Bson> updates = new ArrayList<>(fields.size() + 2);
        for (Map.Entry<String, Object> field : fields.entrySet()) updates.add(Updates.set(field.getKey(), field.getValue()));
        String riotSearch = normalizedRiotId(summoner.riotId());
        if (!riotSearch.isBlank()) updates.add(Updates.set("riotSearch", riotSearch));
        if (userId != null) updates.add(Updates.set("userId", userId));
        return Updates.combine(updates);
    }

    private static Document buildDocument(Build build) {
        String id = build.filter().toKey();
        return new Document("_id", id)
                .append("filterKey", id)
                .append("games", build.games())
                .append("winrate", build.winrate())
                .append("lastUpdate", System.currentTimeMillis())
                .append("build", JsonCodec.toDocument(build));
    }

    private static Bson entityUpdateStage(Map<String, Object> operation) {
        String type = String.valueOf(operation.get("type"));
        String path = String.valueOf(operation.get("path"));
        return switch (type) {
            case "set" -> new Document("$set", new Document(path, mongoValue(operation.get("value"))));
            case "unset" -> new Document("$unset", path);
            case "push" -> new Document("$set", new Document(path, appendArrayExpression(path, operation.get("value"))));
            case "pullValue" -> pullValueStage(path, operation.get("value"));
            case "pull" -> filterArrayStage(path, operation.get("keyField"), operation.get("keyValue"), false, null);
            case "replaceArrayElement" -> replaceArrayStage(path, operation);
            case "setArrayElementField" -> setArrayElementFieldStage(path, operation);
            case "replaceOrAppendArrayElement" -> replaceOrAppendArrayStage(path, operation);
            default -> throw new IllegalArgumentException("Unsupported entity update operation=" + type);
        };
    }

    private static Document appendArrayExpression(String path, Object value) {
        List<Object> appended = new ArrayList<>(1);
        appended.add(mongoValue(value));
        return new Document("$concatArrays", List.of(
                new Document("$ifNull", List.of("$" + path, List.of())),
                appended));
    }

    private static Document filterArrayStage(
            String path,
            Object keyField,
            Object keyValue,
            boolean append,
            Object replacement) {
        String field = String.valueOf(keyField);
        Document filter = new Document("$filter", new Document("input", new Document("$ifNull", List.of("$" + path, List.of())))
                .append("as", "item")
                .append("cond", new Document("$ne", List.of("$$item." + field, literal(keyValue)))));
        if (!append) return new Document("$set", new Document(path, filter));

        List<Object> values = new ArrayList<>(2);
        values.add(filter);
        List<Object> appended = new ArrayList<>(1);
        appended.add(mongoValue(replacement));
        values.add(appended);
        return new Document("$set", new Document(path, new Document("$concatArrays", values)));
    }

    private static Document pullValueStage(String path, Object value) {
        Document filter = new Document("$filter", new Document("input", new Document("$ifNull", List.of("$" + path, List.of())))
                .append("as", "item")
                .append("cond", new Document("$ne", List.of("$$item", literal(value)))));
        return new Document("$set", new Document(path, filter));
    }

    private static Document replaceArrayStage(String path, Map<String, Object> operation) {
        String keyField = String.valueOf(operation.get("keyField"));
        Object keyValue = operation.get("keyValue");
        Document condition = new Document("$eq", List.of("$$item." + keyField, literal(keyValue)));
        Document replacement = new Document("$map", new Document("input", new Document("$ifNull", List.of("$" + path, List.of())))
                .append("as", "item")
                .append("in", new Document("$cond", List.of(condition, mongoValue(operation.get("value")), "$$item"))));
        return new Document("$set", new Document(path, replacement));
    }

    private static Document setArrayElementFieldStage(String path, Map<String, Object> operation) {
        String keyField = String.valueOf(operation.get("keyField"));
        Object keyValue = operation.get("keyValue");
        String targetField = String.valueOf(operation.get("targetField"));
        Document condition = new Document("$eq", List.of("$$item." + keyField, literal(keyValue)));
        Document merged = new Document("$mergeObjects", List.of(
                "$$item",
                new Document(targetField, mongoValue(operation.get("value")))));
        Document mapped = new Document("$map", new Document("input", new Document("$ifNull", List.of("$" + path, List.of())))
                .append("as", "item")
                .append("in", new Document("$cond", List.of(condition, merged, "$$item"))));
        return new Document("$set", new Document(path, mapped));
    }

    private static Document replaceOrAppendArrayStage(String path, Map<String, Object> operation) {
        return filterArrayStage(path, operation.get("keyField"), operation.get("keyValue"), true, operation.get("value"));
    }

    private static Document literal(Object value) {
        return new Document("$literal", mongoValue(value));
    }

    private static Object mongoValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof byte[]) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value instanceof Map<?, ?> map) {
            Document document = new Document();
            for (Map.Entry<?, ?> entry : map.entrySet()) document.put(String.valueOf(entry.getKey()), mongoValue(entry.getValue()));
            return document;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) result.add(mongoValue(item));
            return result;
        }
        if (value instanceof Rank || value instanceof Mastery || value instanceof Participant || value instanceof Match || value instanceof Summoner) {
            return write(value);
        }
        return value;
    }

    private static List<Document> documents(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Document> result = new ArrayList<>(list.size());
        for (Object item : list) if (item instanceof Document document) result.add(document);
        return result;
    }

    private static void replace(MongoCollection<Document> collection, Document document) {
        UpdateResult update = collection.replaceOne(Filters.eq("_id", document.get("_id")), document, new ReplaceOptions().upsert(true));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo replace was not acknowledged for id=" + document.get("_id"));
    }

    private static void bulkWrite(MongoCollection<Document> collection, List<WriteModel<Document>> operations) {
        for (int start = 0; start < operations.size(); start += MAX_BATCH_IDS) {
            int end = Math.min(operations.size(), start + MAX_BATCH_IDS);
            if (!collection.bulkWrite(operations.subList(start, end), new BulkWriteOptions().ordered(false)).wasAcknowledged()) {
                throw new IllegalStateException("Mongo bulk replace was not acknowledged for collection=" + collection.getNamespace().getCollectionName());
            }
        }
    }

    private static Bson summonerFilter(String puuid, LeagueShard shard) {
        if (shard == null || shard == LeagueShard.UNKNOWN) return Filters.eq("_id", puuid);
        return Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()));
    }

    private static String normalizedRiotId(String riotId) {
        if (riotId == null) return "";
        String value = riotId.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character) && character != '-' && character != '#') result.append(character);
        }
        return result.toString();
    }

    private static QueryRecord record(Document document) {
        return QueryRecordParser.fromDocument(document);
    }
}
