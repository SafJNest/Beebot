package com.safjnest.mongo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.IndexOptions;
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
import com.safjnest.utils.SettingsLoader;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.leaderboard.LeaderboardRow;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.utils.JsonCodec;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryRecordParser;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public final class MongoDB {

    private static final int MAX_SEARCH_RESULTS = 25;
    private static final int MAX_BATCH_IDS = 2_000;
    private static final int EXISTS_QUERY_BATCH_SIZE = 2_000;
    private static final String EVENTS_STORAGE_ENGINE_CONFIG = "block_compressor=zstd";
    private static Map<String, List<IndexSpec>> schemas() {
        Map<String, List<IndexSpec>> schemas = new LinkedHashMap<>();
        schemas.put("summoner", List.of(
                index("summoners_region_riot_search", new Document("region", 1).append("riotSearch", 1), false, false),
                index("summoners_user_id", new Document("userId", 1), false, true),
                partialIndex("summoners_tracking_region_active", new Document("tracking", 1).append("region", 1), false, false,
                        Filters.eq("tracking", true)),
                index("summoners_rank_lp", new Document("ranks.rank", 1).append("ranks.lp", -1), false, false),
                index("summoners_mastery_level_points", new Document("masteries.level", -1)
                        .append("masteries.points", -1), false, false)));
        schemas.put("match", List.of(
                index("matches_participant_time", new Document("participants.puuid", 1).append("timeEnd", -1), false, false),
                index("matches_participant_shard_queue_start", new Document("participants.puuid", 1)
                        .append("leagueShard", 1).append("queue", 1).append("timeStart", -1), false, false),
                index("matches_champion_queue_region_patch", new Document("queue", 1).append("leagueShard", 1)
                        .append("patch", 1).append("participants.champion", 1), false, false),
                index("matches_shard_queue_start", new Document("leagueShard", 1).append("queue", 1).append("timeStart", -1), false, false),
                index("matches_patch_queue", new Document("patch", 1).append("queue", 1), false, false),
                index("matches_start", new Document("timeStart", -1), false, false)));
        schemas.put("match_events", List.of());
        schemas.put("profile_statistics", List.of(
                index("profile_statistics_puuid_filter", new Document("puuid", 1).append("filterKey", 1), true, false)));
        schemas.put("leaderboard_distribution", List.of(
                index("distribution_queue_rank_region", new Document("queue", 1).append("rank", 1).append("region", 1), true, false)));
        schemas.put("leaderboard_entries", List.of(
                index("leaderboard_queue_region_rank_mmr", new Document("queue", 1).append("region", 1).append("rank", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_region_rank_mmr_puuid", new Document("queue", 1).append("region", 1)
                        .append("rank", 1).append("mmr", -1).append("puuid", 1), false, false),
                index("leaderboard_queue_region_mmr", new Document("queue", 1).append("region", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_region_mmr_puuid", new Document("queue", 1).append("region", 1)
                        .append("mmr", -1).append("puuid", 1), false, false),
                index("leaderboard_queue_rank_mmr", new Document("queue", 1).append("rank", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_rank_mmr_puuid", new Document("queue", 1).append("rank", 1)
                        .append("mmr", -1).append("puuid", 1), false, false),
                index("leaderboard_queue_mmr", new Document("queue", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_mmr_puuid", new Document("queue", 1).append("mmr", -1).append("puuid", 1), false, false)));
        schemas.put("champion", List.of());
        schemas.put("champion_builds", List.of(index("champion_builds_filter", new Document("filterKey", 1), false, false)));
        schemas.put("champion_stats", List.of(index("champion_stats_filter_champion", new Document("filterKey", 1).append("championId", 1), true, false)));
        schemas.put("migration_runs", List.of(index("migration_runs_status_updated", new Document("status", 1).append("updatedAt", -1), false, false)));

        return schemas;
    }

    private static void ensureCollections(MongoDatabase database) {
        List<String> existing = database.listCollectionNames().into(new ArrayList<>());
        for (String name : schemas().keySet()) if (!existing.contains(name)) {
            try {
                database.createCollection(name, collectionOptions(name));
            } catch (MongoCommandException exception) {
                if (exception.getCode() != 48 && !"NamespaceExists".equals(exception.getErrorCodeName())) throw exception;
            }
        }
    }

    private static void ensureIndexes(MongoDatabase database) {
        dropLegacyProfileStatisticsIndex(database);
        for (Map.Entry<String, List<IndexSpec>> schema : schemas().entrySet()) {
            MongoCollection<Document> collection = database.getCollection(schema.getKey());
            for (IndexSpec index : schema.getValue()) {
                IndexOptions options = new IndexOptions().name(index.name()).unique(index.unique()).sparse(index.sparse());
                if (index.partialFilterExpression() != null) options.partialFilterExpression(index.partialFilterExpression());
                collection.createIndex(index.keys(), options);
            }
        }
    }

    private static void dropLegacyProfileStatisticsIndex(MongoDatabase database) {
        try {
            database.getCollection("profile_statistics").dropIndex("profile_statistics_puuid_season");
        } catch (MongoCommandException exception) {
            if (exception.getCode() != 27 && !"IndexNotFound".equals(exception.getErrorCodeName())) throw exception;
        }
    }

    private static IndexSpec index(String name, Document keys, boolean unique, boolean sparse) {
        return new IndexSpec(name, keys, unique, sparse, null);
    }

    private static IndexSpec partialIndex(String name, Document keys, boolean unique, boolean sparse, Bson partialFilterExpression) {
        return new IndexSpec(name, keys, unique, sparse, partialFilterExpression);
    }

    private static CreateCollectionOptions collectionOptions(String collection) {
        if (!"match_events".equals(collection)) return new CreateCollectionOptions();
        return new CreateCollectionOptions().storageEngineOptions(new Document("wiredTiger", new Document("configString", EVENTS_STORAGE_ENGINE_CONFIG)));
    }

    private static record IndexSpec(String name, Document keys, boolean unique, boolean sparse, Bson partialFilterExpression) {
    }

    public static final String PRODUCTION_DATABASE = "beebot";
    public static final String TEST_DATABASE = "beebot_test";
    private static final String MONGO_URI_ERROR = "Mongo URI is missing from settings.json";
    private static MongoClient client;
    private static MongoDatabase database;
    private static final AtomicLong COMMAND_COUNT = new AtomicLong();
    private static final CommandListener COMMAND_LISTENER = new CommandListener() {
        @Override
        public void commandStarted(CommandStartedEvent event) {
            COMMAND_COUNT.incrementAndGet();
        }
    };
    private static boolean collectionsReady;
    private static boolean indexesReady;
    private static boolean migrationMode;

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
        if (!migrationMode && !indexesReady) {
            ensureIndexes(database);
            indexesReady = true;
        }
        return database;
    }

    public static synchronized void beginMigration() {
        migrationMode = true;
        getDatabase();
    }

    public static synchronized void finishMigration(boolean createIndexes) {
        if (createIndexes) {
            getDatabase();
            ensureIndexes(database);
            indexesReady = true;
        }
        migrationMode = false;
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
                    .addCommandListener(COMMAND_LISTENER)
                    .build());
        }
        return client;
    }

    public static synchronized void close() {
        if (client != null) client.close();
        client = null;
        database = null;
        collectionsReady = false;
        indexesReady = false;
        migrationMode = false;
    }

    public static long commandCount() {
        return COMMAND_COUNT.get();
    }

    public static long resetCommandCount() {
        return COMMAND_COUNT.getAndSet(0);
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
                        .append("hasUserId", new Document("$cond", List.of(new Document("$ne", List.of("$userId", null)), 1, 0)))
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

    public static List<Match> getMatchHistory(String puuid, LeagueMessageParameter parameter) {
        traceRead("match.getMatchHistory", "puuid=" + puuid);
        if (puuid == null || puuid.isBlank() || parameter == null) return List.of();
        List<Match> result = new ArrayList<>();
        int offset = Math.max(0, parameter.getOffset());
        int limit = Math.max(0, parameter.getMessageType().getPageItem());
        List<Match> candidates = new ArrayList<>();
        Filter filter = parameter.toFilter();
        boolean relationalFilter = filter.opponent() != 0 || filter.duo() != 0;
        FindIterable<Document> matches = matches().find(historyFilter(puuid, null, parameter))
                .sort(Sorts.descending("timeStart"))
                .skip(relationalFilter ? 0 : offset);
        matches = matches.limit(relationalFilter ? 0 : limit > 0 ? Math.min(100, limit) : 100);
        int skipped = 0;
        for (Document document : matches) {
            Match match = readMatch(matchRecord(document));
            if (!ProfileStatistics.matchesFilter(match, puuid, filter)) continue;
            if (relationalFilter && skipped++ < offset) continue;
            candidates.add(match);
            if (limit > 0 && candidates.size() >= Math.min(100, limit)) break;
        }
        attachEvents(candidates);
        result.addAll(candidates);
        return result;
    }

    public static int countMatchHistory(String puuid, LeagueMessageParameter parameter) {
        traceRead("match.countMatchHistory", "puuid=" + puuid);
        if (puuid == null || puuid.isBlank() || parameter == null) return 0;
        Filter filter = parameter.toFilter();
        if (filter.opponent() == 0 && filter.duo() == 0)
            return (int) Math.min(Integer.MAX_VALUE, matches().countDocuments(historyFilter(puuid, null, parameter)));
        int count = 0;
        for (Document document : matches().find(historyFilter(puuid, null, parameter)).projection(profileStatisticsMatchProjection())) {
            Match match = readMatch(matchRecord(document));
            if (ProfileStatistics.matchesFilter(match, puuid, filter)) count++;
        }
        return count;
    }

    public static boolean hasMatchByGameId(String gameId) {
        String id = gameId == null ? "" : gameId;
        Document document = id.indexOf('_') > 0 ? matches().find(Filters.eq("_id", id)).first() : matches().find(Filters.regex("_id", Pattern.compile("^.*_" + Pattern.quote(id) + "$"))).first();
        return document != null;
    }

    public static long findLatestMatchTime(String patch, LeagueShard shard) {
        Bson filter = Filters.and(Filters.regex("patch", "^" + Pattern.quote(patch == null ? "" : patch)), Filters.eq("leagueShard", shard.name()));
        Document document = matches().find(filter).sort(Sorts.descending("timeStart")).first();
        return document == null ? 0L : ((Number) document.getOrDefault("timeStart", 0L)).longValue() / 1000L;
    }

    public static List<QueryRecord> findMatchBans(String patch) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.regex("patch", "^" + Pattern.quote(patch == null ? "" : patch)))
                .projection(Projections.include("bans"))) {
            result.add(QueryRecordParser.fromMap(Map.of("bans", bansJson(document.get("bans")))));
        }
        return result;
    }

    public static List<QueryRecord> findChampionWins(String patch, int champion, no.stelar7.api.r4j.basic.constants.types.lol.LaneType lane) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.regex("patch", "^" + Pattern.quote(patch == null ? "" : patch)))
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

    // TODO Mongo build aggregation: use buildPath for the timeline build and include rune arrays.
    public static List<QueryRecord> getChampionBuildsRaw(Filter filter) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document match : matches().find(championMatchFilter(filter, null)).projection(Projections.include(
                "_id", "participants.champion", "participants.lane", "participants.win",
                "participants.starterItems", "participants.boots", "participants.supportItem",
                "participants.item0", "participants.item1", "participants.item2", "participants.item3",
                "participants.item4", "participants.item5", "participants.skillOrder", "participants.augments",
                "participants.summonerSpell1", "participants.summonerSpell2"))) {
            for (Document participant : documents(match.get("participants"))) {
                if (!matchesChampionFilter(participant, filter)) continue;
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
                build.put("summoner_spells", new JSONArray(List.of(participant.getInteger("summonerSpell1", 0), participant.getInteger("summonerSpell2", 0))));
                result.add(QueryRecordParser.fromMap(Map.of(
                        "game_id", publicGameId(match.getString("_id")),
                        "win", participant.getBoolean("win", false),
                        "build", build.toString())));
            }
        }
        return result;
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
                .projection(Projections.include("_id", "timeStart", "participants.puuid", "participants.rank", "participants.lp"))
                .sort(Sorts.descending("timeStart")).first();
        Document row = new Document("puuid", puuid).append("region", summoner.getString("region"));
        if (latest == null) return QueryRecordParser.fromDocument(row);
        row.append("game_id", publicGameId(latest.getString("_id")))
                .append("time_start", latest.get("timeStart", 0L));
        for (Document participant : documents(latest.get("participants"))) if (puuid.equals(participant.getString("puuid"))) {
            row.append("rank", participant.getString("rank"));
            row.append("lp", participant.getInteger("lp", 0));
            break;
        }
        return QueryRecordParser.fromDocument(row);
    }

    private static QueryRecord row(Document document) {
        return QueryRecordParser.fromDocument(document);
    }

    private static boolean matchesChampionFilter(Document participant, Filter filter) {
        if (filter == null) return true;
        if (filter.champion() != 0 && participant.getInteger("champion", 0) != filter.champion()) return false;
        return filter.lane() == null || !GameQueueTypeUtils.hasLane(filter.queue())
                || filter.lane().name().equals(participant.getString("lane"));
    }

    private static List<Integer> readIntegers(Document document, String field) {
        Object value = document.get(field);
        if (!(value instanceof List<?> values)) return List.of();
        List<Integer> result = new ArrayList<>();
        for (Object item : values) if (item instanceof Number number) result.add(number.intValue());
        return result;
    }

    private static Participant participant(Match match, String puuid) {
        if (match == null || match.participants == null) return null;
        for (Participant participant : match.participants) if (participant != null && puuid != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }


    private static MongoCollection<Document> summoners() {
        return database().getCollection("summoner");
    }

    private static MongoCollection<Document> entityCollection(String collectionName) {
        return switch (collectionName) {
            case "summoner" -> summoners();
            case "match" -> matches();
            default -> throw new IllegalArgumentException("Unsupported Mongo entity collection=" + collectionName);
        };
    }

    public record SummonerSearchResult(Summoner summoner, Rank soloRank) {}

    public record ProfileProjection(Summoner summoner, List<Rank> ranks, List<Mastery> masteries) {}

    public static String findPuuid(String riotId, LeagueShard shard) {
        traceRead("summoner.findPuuid", "region=" + shard);
        List<Summoner> result = findSummonersByRiotId(normalizedRiotId(riotId), shard, 1);
        return result.isEmpty() ? null : result.get(0).puuid();
    }

    public static Summoner findSummoner(String puuid, LeagueShard shard) {
        traceRead("summoner.find", "puuid=" + puuid + " region=" + shard);
        if (puuid == null || puuid.isBlank()) return null;
        Bson filter = shard == null ? Filters.eq("_id", puuid) : Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()));
        Document document = summoners().find(filter).first();
        return document == null ? null : summoner(document);
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
                .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon", "summonerId", "ranks"))
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
        for (Rank rank : ranks(document)) if (queue == null || rank.queue() == queue) return rank;
        return null;
    }

    public static List<Rank> findRanks(String puuid, LeagueShard shard) {
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
                .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon", "summonerId", "ranks", "masteries"))
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
        Document document = matches().find(Filters.eq("_id", fullGameId(fullGameId, null))).first();
        if (document == null) return null;
        Match match = read(matchRecord(document), Match.class);
        attachEvents(List.of(match));
        return match;
    }

    public static List<com.safjnest.lol.model.match.MatchResult> findMatchResults(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue,
            int offset,
            int limit) {
        traceRead("match.findResults", "puuid=" + puuid + " queue=" + queue + " offset=" + offset + " limit=" + limit);
        List<com.safjnest.lol.model.match.MatchResult> result = new ArrayList<>();
        int boundedOffset = Math.max(0, offset);
        int boundedLimit = Math.max(0, Math.min(100, limit));
        if (boundedLimit == 0) return result;
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue))
                .projection(matchResultProjection())
                .sort(Sorts.descending("timeStart"))
                .skip(boundedOffset)
                .limit(boundedLimit)) {
            com.safjnest.lol.model.match.Match match = read(matchRecord(document), Match.class);
            com.safjnest.lol.model.match.MatchResult matchResult = toMatchResult(match, puuid);
            if (matchResult != null) result.add(matchResult);
        }
        return result;
    }

    public static List<Match> findProfileStatisticsMatches(
            String puuid,
            LeagueShard shard,
            Filter filter,
            long afterTime,
            long untilTime) {
        traceRead("match.findProfileStatistics", "puuid=" + puuid + " filter=" + (filter == null ? "null" : filter.toSummonerKey()));
        if (puuid == null || puuid.isBlank() || filter == null) return List.of();
        List<Match> result = new ArrayList<>();
        for (Document document : matches().find(profileMatchFilter(puuid, shard, filter, afterTime, untilTime))
                .projection(profileStatisticsMatchProjection())
                .sort(Sorts.ascending("timeStart", "game_id"))) {
            Match match = read(matchRecord(document), Match.class);
            if (ProfileStatistics.matchesFilter(match, puuid, filter)) result.add(match);
        }
        return result;
    }

    public static List<MatchResult> findProfileRecentMatches(
            String puuid,
            LeagueShard shard,
            Filter filter,
            int limit) {
        if (puuid == null || puuid.isBlank() || filter == null || limit <= 0) return List.of();
        List<MatchResult> result = new ArrayList<>();
        boolean relationalFilter = filter.opponent() != 0 || filter.duo() != 0;
        FindIterable<Document> matches = matches().find(profileMatchFilter(puuid, shard, filter, 0, 0))
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

        public static long countMatches(String puuid, LeagueShard shard, long timeStart, long timeEnd, GameQueueType queue) {
        return matches().countDocuments(matchFilter(puuid, shard, timeStart, timeEnd, queue));
    }

        public static boolean hasMatch(String fullGameId) {
        return matches().countDocuments(Filters.eq("_id", fullGameId(fullGameId, null))) > 0;
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
                new Document("$sort", new Document("_id", 1)),
                new Document("$limit", MAX_BATCH_IDS)
        ))) {
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
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue))
                .projection(Projections.include("_id", "gameId", "timeStart", "timeEnd", "patch",
                        "participants.puuid", "participants.rank", "participants.lp", "participants.gain", "participants.win"))
                .sort(Sorts.ascending("timeStart", "game_id"))) {
            String gameId = gameId(document);
            for (Document participant : documents(document.get("participants"))) {
                if (!puuid.equals(participant.getString("puuid"))) continue;
                Document row = new Document("game_id", gameId)
                        .append("rank", participant.get("rank"))
                        .append("lp", participant.get("lp", 0))
                        .append("gain", participant.get("gain", 0))
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

        public static ChampionStatistics findChampionStatistics(Filter filter, int championId) {
        if (filter == null) return null;
        Document document = championStats().find(Filters.and(
                Filters.eq("filterKey", filter.genericKey()), Filters.eq("championId", championId))).first();
        return document == null ? null : readChampionStatistics(document);
    }

        public static Map<Integer, ChampionStatistics> findChampionStatistics(Filter filter) {
        if (filter == null) return Map.of();
        Map<Integer, ChampionStatistics> result = new HashMap<>();
        for (Document document : championStats().find(Filters.eq("filterKey", filter.genericKey()))) {
            ChampionStatistics statistics = readChampionStatistics(document);
            if (statistics != null) result.put(document.getInteger("championId", 0), statistics);
        }
        return result;
    }

        public static List<Filter> findStoredChampionStatisticsFilters() {
        return readFilters(championStats(), true);
    }

    public static List<Filter> findChampionStatisticsRefreshFilters(String patch) {
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

    public static List<Document> findChampionRawDocuments(List<String> fullGameIds) {
        if (fullGameIds == null || fullGameIds.isEmpty()) return List.of();
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
            try (MongoCursor<Document> cursor = matchEvents().find(Filters.in("_id", ids)).iterator()) {
                while (cursor.hasNext()) {
                    Document event = cursor.next();
                    Document match = byId.get(event.getString("_id"));
                    if (match != null) match.put("events", decodeMatchEvents(event));
                }
            }
        }
        return result;
    }

    public static long countLeaderboard(TierType rank, GameQueueType queue, String region) {
        return leaderboard().countDocuments(leaderboardFilter(rank, queue, region));
    }

    public record LeaderboardPageQuery(long total, List<LeaderboardRow> rows, Map<String, List<Mastery>> masteries) {}

    public static LeaderboardPageQuery findLeaderboardPage(
            TierType rank,
            GameQueueType queue,
            String region,
            long offset,
            int limit) {
        int boundedLimit = Math.max(0, Math.min(50, limit));
        int boundedOffset = (int) Math.min(Integer.MAX_VALUE, Math.max(0, offset));
        if (boundedLimit == 0) return new LeaderboardPageQuery(0, List.of(), Map.of());

        Document facet = leaderboard().aggregate(List.of(
                new Document("$match", leaderboardFilter(rank, queue, region)),
                new Document("$facet", new Document("total", List.of(new Document("$count", "value")))
                        .append("rows", List.of(
                                new Document("$sort", new Document("mmr", -1).append("puuid", 1)),
                                new Document("$skip", boundedOffset),
                                new Document("$limit", boundedLimit),
                                new Document("$project", new Document("_id", 0)
                                        .append("puuid", 1).append("region", 1).append("rank", 1)
                                        .append("lp", 1).append("wins", 1).append("losses", 1))
                        )))
        )).first();
        if (facet == null) return new LeaderboardPageQuery(0, List.of(), Map.of());

        long total = 0;
        List<?> totals = facet.getList("total", Object.class, List.of());
        if (!totals.isEmpty() && totals.get(0) instanceof Document totalDocument) {
            total = number(totalDocument, "value");
        }
        List<?> rawRows = facet.getList("rows", Object.class, List.of());
        if (rawRows.isEmpty()) return new LeaderboardPageQuery(total, List.of(), Map.of());

        List<String> puuids = new ArrayList<>(rawRows.size());
        for (Object value : rawRows) if (value instanceof Document row && row.getString("puuid") != null) {
            puuids.add(row.getString("puuid"));
        }
        Map<String, Document> summonerDocuments = new HashMap<>();
        for (Document document : summoners().find(Filters.in("_id", boundedIds(puuids)))
                .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon", "summonerId", "masteries"))
                .limit(puuids.size())) {
            summonerDocuments.put(puuid(document), document);
        }

        List<LeaderboardRow> rows = new ArrayList<>(rawRows.size());
        Map<String, List<Mastery>> masteriesByPuuid = new HashMap<>();
        for (Object value : rawRows) if (value instanceof Document row) {
            String puuid = row.getString("puuid");
            Document summonerDocument = summonerDocuments.get(puuid);
            if (summonerDocument == null) continue;
            rows.add(new LeaderboardRow(summoner(summonerDocument), rank(row, queue)));
            masteriesByPuuid.put(puuid, masteries(summonerDocument));
        }
        return new LeaderboardPageQuery(total, rows, masteriesByPuuid);
    }

    public static List<LeaderboardRow> findLeaderboardRows(
            TierType rank,
            GameQueueType queue,
            String region,
            long offset,
            int limit) {
        return findLeaderboardPage(rank, queue, region, offset, limit).rows();
    }

        public static List<LeaderboardDistribution.Entry> findRankDistribution(GameQueueType queue, String region) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document entry : leaderboard().aggregate(List.of(
                new Document("$match", leaderboardFilter(null, queue, region)),
                new Document("$group", new Document("_id", "$rank").append("players", new Document("$sum", 1)))
        ))) {
            TierDivisionType division = division(entry.getString("_id"));
            String key = division == null || division.getTier() == null ? TierType.UNRANKED.name() : division.getTier();
            counts.merge(key, number(entry, "players"), Long::sum);
        }
        List<LeaderboardDistribution.Entry> result = new ArrayList<>();
        for (TierType tier : TierType.values()) {
            if (tier != TierType.UNRANKED) result.add(new LeaderboardDistribution.Entry(
                    tier.name(), counts.getOrDefault(tier.name(), 0L)));
        }
        return result;
    }

    public static List<LeaderboardDistribution.Entry> findTopRegions(GameQueueType queue, TierType rank) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document entry : leaderboard().aggregate(List.of(
                new Document("$match", leaderboardFilter(rank, queue, "GLOBAL")),
                new Document("$group", new Document("_id", "$region").append("players", new Document("$sum", 1))),
                new Document("$sort", new Document("players", -1).append("_id", 1))
        ))) {
            String region = entry.getString("_id");
            if (region != null) counts.put(region, number(entry, "players"));
        }
        List<LeaderboardDistribution.Entry> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            result.add(new LeaderboardDistribution.Entry(entry.getKey(), entry.getValue()));
        }
        return result;
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

    public static boolean upsertSummoner(String puuid, LeagueShard shard, String riotId, int level, int icon, String userId) {
        if (puuid == null || puuid.isBlank() || shard == null) return false;
        return upsertSummoner(new Summoner(0, puuid, riotId, shard.name(), level, icon), userId);
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

    public static boolean detachSummonerUser(String puuid, String userId) {
        return summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)),
                Updates.combine(Updates.unset("userId"), Updates.set("tracking", false))).getMatchedCount() > 0;
    }

    public static boolean setSummonerTracking(String puuid, String userId, boolean tracked) {
        return summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)),
                Updates.set("tracking", tracked)).getMatchedCount() > 0;
    }

    public static boolean upsertRanks(String puuid, LeagueShard shard, List<Rank> ranks, Map<GameQueueType, Long> mmrByQueue) {
        List<Document> values = new ArrayList<>();
        if (ranks != null) {
            for (Rank rank : ranks) {
                Document value = write(rank);
                if (mmrByQueue != null && mmrByQueue.containsKey(rank.queue())) value.put("mmr", mmrByQueue.get(rank.queue()));
                values.add(value);
            }
        }
        UpdateResult update = summoners().updateOne(summonerFilter(puuid, shard), Updates.set("ranks", values));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo ranks update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

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
        if (match == null) return false;
        String id = fullGameId(fullGameId, match.leagueShard);
        upsertMatchDocument(id, match);
        upsertMatchEvents(id, match.eventData != null ? match.eventData : match.events == null ? Map.of() : match.events.toMap());
        return true;
    }

    public static boolean upsertMatchDocument(String fullGameId, Match match) {
        if (match == null) return false;
        String id = fullGameId(fullGameId, match.leagueShard);
        Document document = write(match);
        document.put("_id", id);
        replace(matches(), document);
        return true;
    }

        public static boolean upsertParticipant(String fullGameId, Participant participant) {
        String id = fullGameId(fullGameId, null);
        if (participant == null) return false;
        Document value = participantDocument(participant);
        Document filter = new Document("$filter", new Document("input", new Document("$ifNull", List.of("$participants", List.of())))
                .append("as", "participant")
                .append("cond", new Document("$ne", List.of("$$participant.puuid", participant.puuid))));
        Document updatePipeline = new Document("$set", new Document("participants", new Document("$concatArrays", List.of(filter, List.of(value)))));
        UpdateResult update = matches().updateOne(Filters.eq("_id", id), List.of(updatePipeline));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo participant update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

        public static boolean updateMatchRank(String fullGameId, TierType rank) {
        UpdateResult update = matches().updateOne(Filters.eq("_id", fullGameId(fullGameId, null)),
                Updates.set("rank", rank == null ? null : rank.name()));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo match rank update was not acknowledged");
        return update.getMatchedCount() > 0;
    }

    public static boolean updateMatchEvents(String fullGameId, Map<String, Object> events) {
        return upsertMatchEvents(fullGameId(fullGameId, null), events);
    }

    public static boolean upsertMatchEvents(String fullGameId, Map<String, Object> events) {
        String id = fullGameId(fullGameId, null);
        Map<String, Object> source = events == null ? Map.of() : events;
        if (source.isEmpty()) {
            matchEvents().deleteOne(Filters.eq("_id", id));
            return true;
        }
        byte[] payload = eventJson(source);
        Document document = new Document("_id", id)
                .append("encoding", "json")
                .append("uncompressedBytes", payload.length)
                .append("data", new String(payload, StandardCharsets.UTF_8))
                .append("checksum", sha256(payload));
        replace(matchEvents(), document);
        return true;
    }

    public static boolean upsertMatchEventsJson(String fullGameId, String json) {
        String id = fullGameId(fullGameId, null);
        String source = json == null ? "" : json.trim();
        if (source.isEmpty() || "{}".equals(source) || "null".equals(source)) {
            matchEvents().deleteOne(Filters.eq("_id", id));
            return true;
        }
        byte[] payload = source.getBytes(StandardCharsets.UTF_8);
        Document document = new Document("_id", id)
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
        updates.add(Updates.setOnInsert("_id", new ObjectId()));
        UpdateResult result = profileStatistics().updateOne(
                Filters.and(Filters.eq("puuid", puuid), Filters.eq("filterKey", filterKey)),
                Updates.combine(updates), new UpdateOptions().upsert(true));
        return result.wasAcknowledged();
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

    public static boolean upsertChampionStatistics(ChampionStatistics statistics) {
        if (statistics == null || statistics.filter() == null) return false;
        Document document = championStatisticsDocument(statistics);
        replace(championStats(), document);
        return true;
    }

        public static boolean upsertChampionStatistics(Map<Integer, ChampionStatistics> statistics) {
        if (statistics == null || statistics.isEmpty()) return false;
        List<WriteModel<Document>> operations = new ArrayList<>(statistics.size());
        for (ChampionStatistics value : statistics.values()) if (value != null && value.filter() != null) {
            Document document = championStatisticsDocument(value);
            operations.add(new ReplaceOneModel<>(Filters.eq("_id", document.get("_id")), document,
                    new ReplaceOptions().upsert(true)));
        }
        if (!operations.isEmpty()) bulkWrite(championStats(), operations);
        return true;
    }

        public static boolean upsertLeaderboardEntry(String puuid, LeagueShard shard, Rank rank, long mmr) {
        if (puuid == null || shard == null || rank == null) return false;
        String id = puuid + ":" + shard.name() + ":" + rank.queue().name();
        Document document = new Document("_id", id)
                .append("puuid", puuid)
                .append("region", shard.name())
                .append("queue", rank.queue().name())
                .append("rank", rank.tier().name())
                .append("lp", rank.lp())
                .append("wins", rank.wins())
                .append("losses", rank.losses())
                .append("mmr", mmr)
                .append("updatedAt", System.currentTimeMillis());
        replace(leaderboard(), document);
        return true;
    }

        public static boolean rebuildLeaderboardDistribution() {
        leaderboardDistribution().deleteMany(new Document());
        Map<String, Long> counts = new HashMap<>();
        for (Document entry : leaderboard().aggregate(List.of(
                new Document("$group", new Document("_id", new Document("queue", "$queue")
                        .append("rank", "$rank").append("region", "$region"))
                        .append("players", new Document("$sum", 1)))
        ))) {
            Document keyDocument = (Document) entry.get("_id");
            TierDivisionType division = division(keyDocument.getString("rank"));
            String tier = division == null || division.getTier() == null ? TierType.UNRANKED.name() : division.getTier();
            String key = keyDocument.getString("queue") + ":" + tier + ":" + keyDocument.getString("region");
            counts.merge(key, number(entry, "players"), Long::sum);
        }
        List<WriteModel<Document>> writes = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String[] parts = entry.getKey().split(":", 3);
            String id = parts[0] + ":" + parts[1] + ":" + parts[2];
            writes.add(new ReplaceOneModel<>(Filters.eq("_id", id), new Document("_id", id)
                    .append("queue", parts[0]).append("rank", parts[1]).append("region", parts[2])
                    .append("players", entry.getValue()).append("updatedAt", System.currentTimeMillis()),
                    new ReplaceOptions().upsert(true)));
        }
        if (!writes.isEmpty()) bulkWrite(leaderboardDistribution(), writes);
        return true;
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
                case "com.safjnest.lol.model.ChampionStatistics" -> readChampionStatistics(QueryRecordParser.toDocument(record));
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
            putIfNotNull(document, "riotId", summoner.riotId()); putIfNotNull(document, "region", summoner.region());
            putIfNotNull(document, "userId", summoner.userId());
            if (summoner.tracking()) document.put("tracking", true);
            if (!summoner.ranks().isEmpty()) document.put("ranks", writeRanks(summoner.ranks()));
            if (!summoner.masteries().isEmpty()) document.put("masteries", writeMasteries(summoner.masteries()));
        } else if (value instanceof Rank rank) {
            document = new Document("queue", rank.queue() == null ? null : rank.queue().name())
                    .append("rank", rank.tier() == null ? null : rank.tier().name()).append("lp", rank.lp())
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
        List<Rank> ranks = new ArrayList<>();
        for (QueryRecord rank : record.getAsRecords("ranks")) ranks.add(readRank(rank));
        List<Mastery> masteries = new ArrayList<>();
        for (QueryRecord mastery : record.getAsRecords("masteries")) masteries.add(readMastery(mastery));
        return Summoner.hydrated(0, puuid, record.getAsString("riotId"),
                record.getAsString("region"), record.getAsInt("level"), record.getAsInt("icon"),
                record.getAsString("userId"), record.getAsBoolean("tracking"), ranks, masteries);
    }

    private static Rank readRank(QueryRecord record) {
        return new Rank(record.getAsEnum("queue", GameQueueType.class), record.getAsEnum("rank", TierDivisionType.class),
                record.getAsInt("lp"), record.getAsInt("wins"), record.getAsInt("losses"));
    }

    private static Mastery readMastery(QueryRecord record) {
        return new Mastery(record.getAsInt("championId"), record.getAsInt("level"), record.getAsInt("points"));
    }

    private static List<Document> writeRanks(List<Rank> ranks) {
        List<Document> result = new ArrayList<>();
        if (ranks != null) for (Rank rank : ranks) if (rank != null) result.add(write(rank));
        return result;
    }

    private static List<Document> writeMasteries(List<Mastery> masteries) {
        List<Document> result = new ArrayList<>();
        if (masteries != null) for (Mastery mastery : masteries) if (mastery != null) result.add(write(mastery));
        return result;
    }

    private static Participant readParticipant(QueryRecord record) {
        Participant participant = new Participant();
        participant.win = record.getAsBoolean("win");
        participant.kda = record.getAsString("kda"); participant.champion = record.getAsInt("champion");
        participant.lane = record.getAsEnum("lane", no.stelar7.api.r4j.basic.constants.types.lol.LaneType.class);
        participant.team = record.getAsEnum("team", no.stelar7.api.r4j.basic.constants.types.lol.TeamType.class);
        participant.roleQuestId = record.getAsInt("roleQuestId"); participant.rank = record.getAsEnum("rank", TierDivisionType.class);
        participant.lp = record.getAsInt("lp"); participant.gain = record.getAsInt("gain"); participant.damage = record.getAsInt("damage"); participant.damageTaken = record.getAsInt("damageTaken");
        participant.damageBuilding = record.getAsInt("damageBuilding"); participant.healing = record.getAsInt("healing"); participant.cs = record.getAsInt("cs");
        participant.goldEarned = record.getAsInt("goldEarned"); participant.ward = record.getAsInt("ward"); participant.wardKilled = record.getAsInt("wardKilled");
        participant.visionScore = record.getAsInt("visionScore"); participant.pings = new HashMap<>(readIntegerMap(record, "pings"));
        participant.subTeam = record.getAsInt("subTeam"); participant.subTeamPlacement = record.getAsInt("subTeamPlacement");
        participant.puuid = record.getAsString("puuid"); participant.riotId = record.getAsString("riotId"); participant.riotTag = record.getAsString("riotTag");
        participant.level = record.getAsInt("level"); participant.doubles = record.getAsInt("doubles"); participant.triples = record.getAsInt("triples");
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
        String fullGameId = record.getAsString("fullGameId"); if (fullGameId == null) fullGameId = record.getAsString("_id");
        if (fullGameId == null || fullGameId.isBlank()) throw new IllegalArgumentException("Mongo match fullGameId is required");
        Match match = Match.hydrated();
        String publicId = source.containsKey("game_id") ? record.getAsString("game_id") : record.getAsString("gameId");
        match.gameId = publicId != null ? publicId : publicGameId(fullGameId);
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
                record.getAsInt("damage"), record.getAsInt("cs"), record.getAsInt("gold"), record.getAsInt("vision"), record.getAsInt("teamKills"), readIntegerList(record, "items"), readIntegerList(record, "summonerSpells"), readParticipants(record));
    }

    private static Document participantDocument(Participant value) {
        if (value == null || value.puuid == null || value.puuid.isBlank()) throw new IllegalArgumentException("Participant.puuid is required for Mongo persistence");
        Document document = new Document("win", value.win).append("champion", value.champion).append("roleQuestId", value.roleQuestId)
                .append("lp", value.lp).append("gain", value.gain).append("damage", value.damage).append("damageTaken", value.damageTaken).append("damageBuilding", value.damageBuilding).append("healing", value.healing).append("cs", value.cs).append("goldEarned", value.goldEarned).append("ward", value.ward).append("wardKilled", value.wardKilled).append("visionScore", value.visionScore).append("pings", integerMapDocument(value.pings))
                .append("subTeam", value.subTeam).append("subTeamPlacement", value.subTeamPlacement).append("level", value.level).append("doubles", value.doubles).append("triples", value.triples).append("quadruples", value.quadruples).append("pentas", value.pentas)
                .append("item0", value.item0).append("item1", value.item1).append("item2", value.item2).append("item3", value.item3).append("item4", value.item4).append("item5", value.item5).append("item6", value.item6).append("turretKills", value.turretKills).append("q", value.q).append("w", value.w).append("e", value.e).append("r", value.r).append("d", value.d).append("f", value.f).append("summonerSpell1", value.summonerSpell1).append("summonerSpell2", value.summonerSpell2)
                .append("primaryRunes", integerList(value.primaryRunes)).append("secondaryRunes", integerList(value.secondaryRunes)).append("statsRunes", integerList(value.statsRunes)).append("skillOrder", integerList(value.skillOrder)).append("augments", integerList(value.augments)).append("starterItems", integerList(value.starterItems)).append("buildPath", integerList(value.buildPath)).append("boots", value.boots).append("supportItem", value.supportItem);
        putIfNotNull(document, "kda", value.kda); putIfNotNull(document, "puuid", value.puuid); putIfNotNull(document, "riotId", value.riotId); putIfNotNull(document, "riotTag", value.riotTag); putEnum(document, "lane", value.lane); putEnum(document, "team", value.team); putEnum(document, "rank", value.rank);
        return document;
    }

    private static Document matchDocument(Match value) {
        if (value.leagueShard == null) throw new IllegalArgumentException("Match.leagueShard is required");
        String fullGameId = fullGameId(value.gameId, value.leagueShard);
        String publicId = publicGameId(fullGameId);
        Document document = new Document("_id", fullGameId).append("fullGameId", fullGameId)
                .append("gameId", publicId).append("region", value.leagueShard.name()).append("game_id", publicId)
                .append("leagueShard", value.leagueShard.name()).append("lastUpdate", value.lastUpdate).append("timeStart", value.timeStart)
                .append("timeEnd", value.timeEnd).append("bans", writeBans(value.bans)).append("participants", writeParticipants(value.participants));
        putEnum(document, "queue", value.queue); putEnum(document, "rank", value.rank); putIfNotNull(document, "patch", value.patch);
        return document;
    }

    private static Document matchResultDocument(MatchResult value) {
        Document document = new Document("gameId", value.gameId).append("timeStart", value.timeStart).append("timeEnd", value.timeEnd).append("win", value.win).append("championId", value.championId).append("damage", value.damage).append("cs", value.cs).append("gold", value.gold).append("vision", value.vision).append("teamKills", value.teamKills).append("items", integerList(value.items)).append("summonerSpells", integerList(value.summonerSpells)).append("participants", writeParticipants(value.participants));
        putEnum(document, "queue", value.queue); putEnum(document, "lane", value.lane); putIfNotNull(document, "kda", value.kda); return document;
    }

    private static String fullGameId(String gameId, LeagueShard shard) {
        if (gameId == null || gameId.isBlank()) throw new IllegalArgumentException("fullGameId cannot be blank");
        String value = gameId.trim();
        if (value.indexOf('_') > 0) return value;
        if (shard == null) throw new IllegalArgumentException("A shard is required for a numeric game ID");
        return shard.name() + "_" + value;
    }

    private static String publicGameId(String fullGameId) {
        int separator = fullGameId.indexOf('_');
        return separator < 0 ? fullGameId : fullGameId.substring(separator + 1);
    }

    private static String gameId(Document document) {
        String value = document.getString("game_id");
        if (value != null) return value;
        value = document.getString("gameId");
        return value != null ? value : publicGameId(document.getString("_id"));
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
            String id = fullGameId(match.gameId, match.leagueShard);
            ids.add(id);
            byId.put(id, match);
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

    private static Map<String, Object> decodeMatchEvents(Document document) {
        if (document == null) return new LinkedHashMap<>();
        String encoding = document.getString("encoding");
        if (!"json".equals(encoding)) throw new IllegalStateException("Unsupported match event encoding=" + encoding + " id=" + document.get("_id"));
        String data = document.getString("data");
        int size = document.getInteger("uncompressedBytes", 0);
        if (data == null || size < 0) throw new IllegalStateException("Invalid match events id=" + document.get("_id"));
        byte[] decoded = data.getBytes(StandardCharsets.UTF_8);
        if (decoded.length != size) throw new IllegalStateException("Match event size mismatch id=" + document.get("_id"));
        if (!sha256(decoded).equals(document.getString("checksum"))) throw new IllegalStateException("Match event checksum mismatch id=" + document.get("_id"));
        try {
            return new JSONObject(new String(decoded, StandardCharsets.UTF_8)).toMap();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid compressed match event JSON id=" + document.get("_id"), exception);
        }
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
                List.of(player.item0, player.item1, player.item2, player.item3, player.item4, player.item5, player.item6), List.of(player.summonerSpell1, player.summonerSpell2), participants);
    }

    private static int kills(String kda) {
        if (kda == null || kda.isBlank()) return 0;
        try { return Integer.parseInt(kda.split("/", 2)[0]); } catch (RuntimeException ignored) { return 0; }
    }

    private static void traceRead(String operation, String details) {
        if (App.isTesting()) BotLogger.trace("[MONGO] " + operation + " " + details);
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

    private static MongoCollection<Document> builds() {
        return database().getCollection("champion_builds");
    }

    private static MongoCollection<Document> championStats() {
        return database().getCollection("champion_stats");
    }

    private static MongoCollection<Document> leaderboard() {
        return database().getCollection("leaderboard_entries");
    }

    private static MongoCollection<Document> leaderboardDistribution() {
        return database().getCollection("leaderboard_distribution");
    }

    private static QueryRecord matchRecord(Document document) {
        return QueryRecordParser.fromDocument(document);
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
        if (shard != null) filters.add(Filters.eq("leagueShard", shard.name()));
        if (queue != null) filters.add(Filters.eq("queue", queue.name()));
        if (timeStart != 0) filters.add(Filters.gte("timeStart", timeStart));
        if (timeEnd != 0) filters.add(Filters.lte("timeEnd", timeEnd));
        return Filters.and(filters);
    }

    private static Bson historyFilter(String puuid, LeagueShard shard, LeagueMessageParameter parameter) {
        return profileMatchFilter(puuid, shard, parameter.toFilter(), 0, 0);
    }

    private static Bson profileMatchFilter(String puuid, LeagueShard shard, Filter filter, long afterTime, long untilTime) {
        List<Bson> filters = new ArrayList<>();
        LeagueShard selectedShard = filter.region() != null ? filter.region() : shard;
        if (selectedShard != null) filters.add(Filters.eq("leagueShard", selectedShard.name()));
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
                "_id", "gameId", "queue", "timeStart", "timeEnd",
                "participants.puuid", "participants.riotId", "participants.riotTag", "participants.level",
                "participants.win", "participants.kda", "participants.champion", "participants.lane",
                "participants.team", "participants.damage", "participants.cs", "participants.goldEarned",
                "participants.visionScore", "participants.item0", "participants.item1", "participants.item2",
                "participants.item3", "participants.item4", "participants.item5", "participants.item6",
                "participants.summonerSpell1", "participants.summonerSpell2");
    }

    private static Bson profileStatisticsMatchProjection() {
        return Projections.include(
                "_id", "fullGameId", "gameId", "game_id", "region", "leagueShard", "queue", "rank", "lastUpdate",
                "timeStart", "timeEnd", "patch", "participants");
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
            if (filter.region() != null) filters.add(Filters.eq("leagueShard", filter.region().name()));
            if (filter.rank() != null) filters.add(Filters.in("rank", divisionNames(filter.rank())));
        }
        if (puuid != null) filters.add(Filters.elemMatch("participants", Filters.eq("puuid", puuid)));
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static Bson patchMajorFilter(String patch) {
        return Filters.regex("patch", "^" + Pattern.quote(patch) + "(?:\\.|$)");
    }

    private static Bson leaderboardFilter(TierType rank, GameQueueType queue, String region) {
        List<Bson> filters = new ArrayList<>();
        if (queue != null) filters.add(Filters.eq("queue", queue.name()));
        if (region != null && !"GLOBAL".equals(region)) filters.add(Filters.eq("region", region));
        if (rank != null) filters.add(Filters.in("rank", divisionNames(rank)));
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

    private static List<Filter> findChampionSourceFilters(String patch, boolean includeChampion) {
        if (patch == null || patch.isBlank()) return List.of();
        Map<String, Filter> result = new LinkedHashMap<>();
        for (Document match : matches().find(Filters.eq("patch", patch))
                .projection(Projections.include("queue", "rank", "leagueShard", "participants.champion", "participants.lane"))) {
            GameQueueType queue = enumValue(GameQueueType.class, match.getString("queue"));
            TierType rank = enumValue(TierType.class, match.getString("rank"));
            LeagueShard region = enumValue(LeagueShard.class, match.getString("leagueShard"));
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

    private static List<String> divisionNames(TierType tier) {
        if (tier == null) return List.of();
        List<String> result = new ArrayList<>();
        for (TierDivisionType division : TierDivisionType.values()) {
            if (tier.name().equals(division.getTier())) result.add(division.name());
        }
        return result;
    }

    private static Summoner summoner(Document document) {
        String puuid = puuid(document);
        return Summoner.hydrated(document.getInteger("summonerId", 0), puuid, document.getString("riotId"),
                document.getString("region"), document.getInteger("level", 0), document.getInteger("icon", 0),
                document.getString("userId"), document.getBoolean("tracking", false), ranks(document), masteries(document));
    }

    private static String puuid(Document document) {
        String puuid = document.getString("puuid");
        return puuid == null ? document.getString("_id") : puuid;
    }

    private static Rank soloRank(Document document) {
        for (Rank rank : ranks(document)) {
            if (rank.queue() == GameQueueType.RANKED_SOLO_5X5) return rank;
        }
        return null;
    }

    private static List<Rank> ranks(Document document) {
        Object value = document.get("ranks");
        if (!(value instanceof List<?> values)) return List.of();
        List<Rank> result = new ArrayList<>(values.size());
        for (Object item : values) if (item instanceof Document rank) result.add(rankFromDocument(rank));
        return result;
    }

    private static Rank rankFromDocument(Document document) {
        GameQueueType queue;
        try { queue = GameQueueType.valueOf(document.getString("queue")); }
        catch (RuntimeException ignored) { queue = GameQueueType.RANKED_SOLO_5X5; }
        return new Rank(queue, division(document.getString("rank")), document.getInteger("lp", 0),
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

    private static TierDivisionType division(String value) {
        if (value == null) return null;
        try { return TierDivisionType.valueOf(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static Rank rank(Document entry, GameQueueType queue) {
        return new Rank(queue, division(entry.getString("rank")), entry.getInteger("lp", 0),
                entry.getInteger("wins", 0), entry.getInteger("losses", 0));
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

    private static Build readBuild(Document document) {
        return readStructured(document.get("build"), Build.class);
    }

    private static ChampionStatistics readChampionStatistics(Document document) {
        return readStructured(document.get("statistics"), ChampionStatistics.class);
    }

    private static <T> T readStructured(Object value, Class<T> type) {
        return JsonCodec.fromDocument(value, type);
    }

    private static Document structured(Object value) {
        return JsonCodec.toDocument(value);
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
                .append("build", JsonCodec.toDocument(build));
    }

    private static Document championStatisticsDocument(ChampionStatistics statistics) {
        String filterKey = statistics.filter().genericKey();
        String id = filterKey + ":" + statistics.filter().champion();
        return new Document("_id", id)
                .append("filterKey", filterKey)
                .append("championId", statistics.filter().champion())
                .append("statistics", JsonCodec.toDocument(statistics));
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
