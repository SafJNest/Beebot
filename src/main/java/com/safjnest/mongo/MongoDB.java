package com.safjnest.mongo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mongodb.MongoCommandException;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
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
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.utils.KryoUtils;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public final class MongoDB {

    private static final int MAX_SEARCH_RESULTS = 25;
    private static final String EVENTS_STORAGE_ENGINE_CONFIG = "block_compressor=zstd";
    private static final ObjectMapper JSON = new ObjectMapper();


    private static void ensureSchema(MongoDatabase database) {
        Map<String, List<IndexSpec>> schemas = new LinkedHashMap<>();
        schemas.put("summoner", List.of(
                index("summoners_region_riot_search", new Document("region", 1).append("riotSearch", 1), false, false),
                index("summoners_user_id", new Document("userId", 1), false, true),
                partialIndex("summoners_tracking_region_active", new Document("tracking", 1).append("region", 1), false, false,
                        Filters.eq("tracking", true))));
        schemas.put("match", List.of(
                index("matches_participant_time", new Document("participants.puuid", 1).append("timeEnd", -1), false, false),
                index("matches_shard_queue_start", new Document("leagueShard", 1).append("queue", 1).append("timeStart", -1), false, false),
                index("matches_patch_queue", new Document("patch", 1).append("queue", 1), false, false),
                index("matches_start", new Document("timeStart", -1), false, false)));
        schemas.put("match_events", List.of());
        schemas.put("profile_statistics", List.of(
                index("profile_statistics_puuid_season", new Document("puuid", 1).append("seasonStart", 1), true, false)));
        schemas.put("leaderboard_distribution", List.of(
                index("distribution_queue_rank_region", new Document("queue", 1).append("rank", 1).append("region", 1), true, false)));
        schemas.put("leaderboard_entries", List.of(
                index("leaderboard_queue_region_rank_mmr", new Document("queue", 1).append("region", 1).append("rank", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_region_mmr", new Document("queue", 1).append("region", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_rank_mmr", new Document("queue", 1).append("rank", 1).append("mmr", -1), false, false),
                index("leaderboard_queue_mmr", new Document("queue", 1).append("mmr", -1), false, false)));
        schemas.put("champion", List.of());
        schemas.put("champion_builds", List.of(index("champion_builds_filter", new Document("filterKey", 1), false, false)));
        schemas.put("champion_stats", List.of(index("champion_stats_filter_champion", new Document("filterKey", 1).append("championId", 1), true, false)));
        schemas.put("migration_runs", List.of(index("migration_runs_status_updated", new Document("status", 1).append("updatedAt", -1), false, false)));

        List<String> existing = database.listCollectionNames().into(new ArrayList<>());
        for (Map.Entry<String, List<IndexSpec>> schema : schemas.entrySet()) {
            if (!existing.contains(schema.getKey())) {
                try {
                    database.createCollection(schema.getKey(), collectionOptions(schema.getKey()));
                } catch (MongoCommandException exception) {
                    if (exception.getCode() != 48 && !"NamespaceExists".equals(exception.getErrorCodeName())) throw exception;
                }
            }
            MongoCollection<Document> collection = database.getCollection(schema.getKey());
            for (IndexSpec index : schema.getValue()) {
                IndexOptions options = new IndexOptions().name(index.name()).unique(index.unique()).sparse(index.sparse());
                if (index.partialFilterExpression() != null) options.partialFilterExpression(index.partialFilterExpression());
                collection.createIndex(index.keys(), options);
            }
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
    private static boolean schemaReady;

    private MongoDB() {
    }

    public static synchronized MongoDatabase getDatabase() {
        if (database == null) {
            database = getClient().getDatabase(App.isTesting() ? TEST_DATABASE : PRODUCTION_DATABASE);
        }
        if (!schemaReady) {
            ensureSchema(database);
            schemaReady = true;
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
            client = MongoClients.create(uri);
        }
        return client;
    }

    public static synchronized void close() {
        if (client != null) client.close();
        client = null;
        database = null;
        schemaReady = false;
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

    public static MongoRecord findRecord(String collection, Object id) {
        Document document = database().getCollection(collection).find(Filters.eq("_id", id)).first();
        return document == null ? null : new MongoRecord(collection, id, document);
    }

    public static void upsertDocument(String collection, Document document) {
        if (collection == null || document == null || document.get("_id") == null) throw new IllegalArgumentException("Mongo collection, document and _id are required");
        replace(database().getCollection(collection), document);
    }

    public static Document toDocument(Object value) {
        return write(value).toDocument();
    }

    public static QueryResult getRegisteredLolAccounts(long timeStart) {
        QueryResult result = new QueryResult();
        for (Document summoner : summoners().find(Filters.eq("tracking", true))) {
            QueryRecord row = latestRegisteredRow(summoner, timeStart);
            if (row != null) result.add(row);
        }
        result.setSuccess(true);
        return result;
    }

    public static QueryRecord getRegisteredLolAccount(String puuid, long timeStart) {
        Document summoner = summoners().find(Filters.eq("_id", puuid)).first();
        return summoner == null ? new QueryRecord() : latestRegisteredRow(summoner, timeStart);
    }

    public static QueryResult getAllGamesForAccount(String puuid, long timeStart, long timeEnd) {
        QueryResult result = new QueryResult();
        Summoner summoner = findSummoner(puuid, null);
        if (summoner == null) { result.setSuccess(true); return result; }
        for (Document document : matches().find(matchFilter(summoner.puuid(), parseShard(summoner.region()), timeStart, timeEnd, null)).sort(Sorts.descending("timeStart"))) {
            for (Document participant : documents(document.get("participants"))) if (summoner.puuid().equals(participant.getString("puuid"))) {
                result.add(row(new Document("game_id", publicGameId(document.getString("_id"))).append("queue", participant.get("queue", document.get("queue"))).append("win", participant.get("win", false))));
                break;
            }
        }
        result.setSuccess(true);
        return result;
    }

    public static List<Match> getMatchHistory(String puuid, LeagueMessageParameter parameter) {
        Summoner summoner = findSummoner(puuid, null);
        if (summoner == null || parameter == null) return List.of();
        LeagueShard shard = parseShard(summoner.region());
        List<Match> result = new ArrayList<>();
        int offset = Math.max(0, parameter.getOffset());
        int limit = Math.max(0, parameter.getMessageType().getPageItem());
        int skipped = 0;
        List<Match> candidates = new ArrayList<>();
        for (Document document : matches().find(matchFilter(summoner.puuid(), shard, parameter.getTimeStart(), parameter.getTimeEnd(), parameter.getQueueType())).sort(Sorts.descending("timeStart"))) {
            Match match = readMatch(matchRecord(document));
            Participant player = participant(match, summoner.puuid());
            if (player == null) continue;
            if (parameter.getShowingChampion() != 0 && player.champion != parameter.getShowingChampion()) continue;
            if (parameter.getLaneType() != null && player.lane != parameter.getLaneType()) continue;
            if (skipped++ < offset) continue;
            if (candidates.size() >= limit) break;
            candidates.add(match);
        }
        attachEvents(candidates);
        result.addAll(candidates);
        return result;
    }

    public static int countMatchHistory(String puuid, LeagueMessageParameter parameter) {
        Summoner summoner = findSummoner(puuid, null);
        if (summoner == null || parameter == null) return 0;
        int count = 0;
        for (Document document : matches().find(matchFilter(summoner.puuid(), parseShard(summoner.region()), parameter.getTimeStart(), parameter.getTimeEnd(), parameter.getQueueType()))) {
            Match match = readMatch(matchRecord(document));
            Participant player = participant(match, summoner.puuid());
            if (player != null && (parameter.getShowingChampion() == 0 || player.champion == parameter.getShowingChampion()) && (parameter.getLaneType() == null || player.lane == parameter.getLaneType())) count++;
        }
        return count;
    }

    public static int getMatchIdByGameId(String gameId) {
        String id = gameId == null ? "" : gameId;
        Document document = id.indexOf('_') > 0 ? matches().find(Filters.eq("_id", id)).first() : matches().find(Filters.regex("_id", Pattern.compile("^.*_" + Pattern.quote(id) + "$"))).first();
        return document == null ? 0 : document.getInteger("legacyMatchId", 0);
    }

    public static long findLatestMatchTime(String patch, LeagueShard shard) {
        Bson filter = Filters.and(Filters.regex("patch", "^" + Pattern.quote(patch == null ? "" : patch)), Filters.eq("leagueShard", shard.name()));
        Document document = matches().find(filter).sort(Sorts.descending("timeStart")).first();
        return document == null ? 0L : ((Number) document.getOrDefault("timeStart", 0L)).longValue() / 1000L;
    }

    public static QueryResult findMatchBans(String patch) {
        QueryResult result = new QueryResult();
        for (Document document : matches().find(Filters.regex("patch", "^" + Pattern.quote(patch == null ? "" : patch)))) {
            QueryRecord row = new QueryRecord();
            row.put("bans", bansJson(document.get("bans")));
            result.add(row);
        }
        result.setSuccess(true);
        return result;
    }

    public static QueryResult findChampionWins(String patch, int champion, no.stelar7.api.r4j.basic.constants.types.lol.LaneType lane) {
        QueryResult result = new QueryResult();
        for (Document document : matches().find(Filters.regex("patch", "^" + Pattern.quote(patch == null ? "" : patch)))) {
            for (Document participant : documents(document.get("participants"))) {
                if (participant.getInteger("champion", 0) != champion) continue;
                String laneName = participant.getString("lane");
                if (lane != null && (laneName == null || !lane.name().equals(laneName))) continue;
                QueryRecord row = new QueryRecord();
                row.put("win", String.valueOf(participant.getBoolean("win", false)));
                result.add(row);
            }
        }
        result.setSuccess(true);
        return result;
    }

    public static QueryResult getChampionBuildsRaw(Filter filter) {
        QueryResult result = new QueryResult();
        for (Document match : matches().find(championMatchFilter(filter, null))) {
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
                QueryRecord row = new QueryRecord();
                row.put("game_id", publicGameId(match.getString("_id")));
                row.put("win", String.valueOf(participant.getBoolean("win", false)));
                row.put("build", build.toString());
                row.put("summoner_id", String.valueOf(participant.getInteger("summonerId", 0)));
                result.add(row);
            }
        }
        result.setSuccess(true);
        return result;
    }

    public static long countChampionMatchesByFilter(Filter filter) {
        return countChampionMatches(filter);
    }

    public static List<String> findChampionLegacyMatchIds(Filter filter, long afterLegacyId, int limit) {
        List<String> result = new ArrayList<>();
        int boundedLimit = Math.max(0, Math.min(10_000, limit));
        if (boundedLimit == 0) return result;
        for (Document document : matches().find(Filters.and(championMatchFilter(filter, null), Filters.gt("legacyMatchId", afterLegacyId)))
                .projection(Projections.include("legacyMatchId")).sort(Sorts.ascending("legacyMatchId")).limit(boundedLimit)) {
            result.add(String.valueOf(document.get("legacyMatchId")));
        }
        return result;
    }

    public static List<MongoRecord> findChampionRecordsByLegacyIds(List<String> legacyIds) {
        List<Integer> ids = new ArrayList<>();
        if (legacyIds != null) for (String id : legacyIds) try { ids.add(Integer.valueOf(id)); } catch (NumberFormatException ignored) { }
        if (ids.isEmpty()) return List.of();
        List<MongoRecord> result = new ArrayList<>();
        for (Document document : matches().find(Filters.in("legacyMatchId", ids))) result.add(matchRecord(document));
        return result;
    }

    public static String getSummonerNameById(String puuid, LeagueShard shard) {
        return findSummoner(puuid, shard) == null ? null : findSummoner(puuid, shard).riotId();
    }

    private static QueryRecord latestRegisteredRow(Document summoner, long timeStart) {
        String puuid = summoner.getString("puuid");
        Document latest = matches().find(matchFilter(puuid, parseShard(summoner.getString("region")), timeStart, 0, GameQueueType.TEAM_BUILDER_RANKED_SOLO)).sort(Sorts.descending("timeStart")).first();
        QueryRecord row = new QueryRecord();
        row.put("puuid", puuid); row.put("region", summoner.getString("region"));
        if (latest == null) return row;
        row.put("game_id", publicGameId(latest.getString("_id")));
        row.put("time_start", String.valueOf(latest.get("timeStart", 0L)));
        for (Document participant : documents(latest.get("participants"))) if (puuid.equals(participant.getString("puuid"))) {
            row.put("rank", participant.getString("rank")); row.put("lp", String.valueOf(participant.getInteger("lp", 0))); break;
        }
        return row;
    }

    private static QueryRecord row(Document document) {
        QueryRecord result = new QueryRecord();
        for (Map.Entry<String, Object> entry : document.entrySet()) if (entry.getValue() != null) result.put(entry.getKey(), String.valueOf(entry.getValue()));
        return result;
    }

    private static boolean matchesChampionFilter(Document participant, Filter filter) {
        if (filter == null) return true;
        if (filter.champion() != 0 && participant.getInteger("champion", 0) != filter.champion()) return false;
        return filter.lane() == null || filter.lane().name().equals(participant.getString("lane"));
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

        public static String findPuuid(String riotId, LeagueShard shard) {
        List<Summoner> result = findSummonersByRiotId(riotId, shard, 1);
        return result.isEmpty() ? null : result.get(0).puuid();
    }

        public static Summoner findSummoner(String puuid, LeagueShard shard) {
        if (puuid == null || puuid.isBlank()) return null;
        Bson filter = shard == null ? Filters.eq("_id", puuid) : Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()));
        Document document = summoners().find(filter).first();
        return document == null ? null : record(document).getAs(Summoner.class);
    }

        public static List<Summoner> findSummonersByRiotId(String normalizedQuery, LeagueShard shard, int limit) {
        int boundedLimit = Math.max(0, Math.min(MAX_SEARCH_RESULTS, limit));
        if (boundedLimit == 0) return List.of();
        Pattern prefix = Pattern.compile("^" + Pattern.quote(normalizedQuery == null ? "" : normalizedQuery), Pattern.CASE_INSENSITIVE);
        List<Summoner> result = new ArrayList<>();
        for (Document document : summoners()
                .find(Filters.and(Filters.eq("region", shard.name()), Filters.regex("riotSearch", prefix)))
                .projection(Projections.include("_id", "puuid", "riotId", "region", "level", "icon"))
                .sort(Sorts.ascending("riotId"))
                .limit(boundedLimit)) {
            result.add(record(document).getAs(Summoner.class));
        }
        return result;
    }

        public static List<MongoRecord> findFocusedSummoners(String normalizedQuery, LeagueShard shard, int limit) {
        int boundedLimit = Math.max(0, Math.min(MAX_SEARCH_RESULTS, limit));
        if (boundedLimit == 0) return List.of();
        Pattern prefix = Pattern.compile("^" + Pattern.quote(normalizedQuery == null ? "" : normalizedQuery), Pattern.CASE_INSENSITIVE);
        List<MongoRecord> result = new ArrayList<>();
        for (Document document : summoners()
                .find(Filters.and(Filters.eq("region", shard.name()), Filters.regex("riotSearch", prefix)))
                .projection(Projections.include("_id", "puuid", "riotId", "region"))
                .sort(Sorts.ascending("riotId"))
                .limit(boundedLimit)) {
            result.add(record(document));
        }
        return result;
    }

        public static List<MongoRecord> findAccountsByUserId(String userId) {
        List<MongoRecord> result = new ArrayList<>();
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
        MongoRecord summoner = findRecord(puuid, shard);
        if (summoner == null) return null;
        for (MongoRecord rankRecord : summoner.getAsRecords("ranks")) {
            Rank rank = rankRecord.getAs(Rank.class);
            if (queue == null || rank.queue() == queue) return rank;
        }
        return null;
    }

        public static List<Rank> findRanks(String puuid, LeagueShard shard) {
        MongoRecord summoner = findRecord(puuid, shard);
        if (summoner == null) return List.of();
        List<Rank> result = new ArrayList<>();
        for (MongoRecord rankRecord : summoner.getAsRecords("ranks")) result.add(rankRecord.getAs(Rank.class));
        return result;
    }

        public static Map<String, Rank> findSoloRanksByPuuid(List<String> puuids, LeagueShard shard) {
        Map<String, Rank> result = new HashMap<>();
        if (puuids == null || puuids.isEmpty()) return result;
        for (String puuid : puuids) {
            Rank rank = findRank(puuid, shard, GameQueueType.RANKED_SOLO_5X5);
            if (rank != null) result.put(puuid, rank);
        }
        return result;
    }

        public static List<Mastery> findMasteries(String puuid, LeagueShard shard) {
        MongoRecord summoner = findRecord(puuid, shard);
        if (summoner == null) return List.of();
        List<Mastery> result = new ArrayList<>();
        for (MongoRecord masteryRecord : summoner.getAsRecords("masteries")) result.add(masteryRecord.getAs(Mastery.class));
        return result;
    }

        public static com.safjnest.lol.model.match.Match findMatch(String fullGameId) {
        Document document = matches().find(Filters.eq("_id", fullGameId(fullGameId, null))).first();
        if (document == null) return null;
        Match match = matchRecord(document).getAs(com.safjnest.lol.model.match.Match.class);
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
        List<com.safjnest.lol.model.match.MatchResult> result = new ArrayList<>();
        int boundedOffset = Math.max(0, offset);
        int boundedLimit = Math.max(0, Math.min(100, limit));
        if (boundedLimit == 0) return result;
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue))
                .sort(Sorts.descending("timeStart"))
                .skip(boundedOffset)
                .limit(boundedLimit)) {
            com.safjnest.lol.model.match.Match match = matchRecord(document).getAs(com.safjnest.lol.model.match.Match.class);
            com.safjnest.lol.model.match.MatchResult matchResult = toMatchResult(match, puuid);
            if (matchResult != null) result.add(matchResult);
        }
        return result;
    }

        public static List<com.safjnest.lol.model.match.Match> findAnalysisMatches(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        List<com.safjnest.lol.model.match.Match> result = new ArrayList<>();
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue)).sort(Sorts.descending("timeStart"))) {
            result.add(matchRecord(document).getAs(com.safjnest.lol.model.match.Match.class));
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
        Map<String, Boolean> puuids = new LinkedHashMap<>();
        for (Document document : matches().find(matchFilter(null, shard, seasonStart, seasonEnd, null))
                .projection(Projections.include("participants"))) {
            for (Document participant : documents(document.get("participants"))) {
                String puuid = participant.getString("puuid");
                if (puuid != null && !puuid.isBlank()) puuids.put(puuid, Boolean.TRUE);
            }
        }
        return new ArrayList<>(puuids.keySet());
    }

        public static MongoRecord findSummaryProjection(
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

    public static List<MongoRecord> findAdvancedProfileProjections(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        Map<Integer, AdvancedChampionAggregate> aggregates = new HashMap<>();
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue))) {
            for (Document participant : documents(document.get("participants"))) {
                if (!puuid.equals(participant.getString("puuid"))) continue;
                int champion = participant.getInteger("champion", 0);
                AdvancedChampionAggregate aggregate = aggregates.computeIfAbsent(champion, AdvancedChampionAggregate::new);
                aggregate.add(participant);
            }
        }

        List<MongoRecord> result = new ArrayList<>();
        List<AdvancedChampionAggregate> ordered = new ArrayList<>(aggregates.values());
        ordered.sort((left, right) -> {
            int games = Integer.compare(right.games, left.games);
            return games != 0 ? games : Integer.compare(left.champion, right.champion);
        });
        for (AdvancedChampionAggregate aggregate : ordered) {
            Document document = new Document("champion", aggregate.champion)
                    .append("games", aggregate.games)
                    .append("wins", aggregate.wins)
                    .append("losses", aggregate.losses)
                    .append("avg_kills", aggregate.average(aggregate.kills))
                    .append("avg_deaths", aggregate.average(aggregate.deaths))
                    .append("avg_assists", aggregate.average(aggregate.assists))
                    .append("total_lp_gain", aggregate.totalLpGain)
                    .append("lanes_played", aggregate.lanesPlayed());
            result.add(new MongoRecord("match", "advanced:" + puuid + ":" + aggregate.champion, document));
        }
        return result;
    }

    public static List<MongoRecord> findSummonerData(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        List<MongoRecord> result = new ArrayList<>();
        for (Document document : matches().find(matchFilter(puuid, shard, timeStart, timeEnd, queue))
                .sort(Sorts.ascending("timeStart", "game_id"))) {
            String gameId = gameId(document);
            for (Document participant : documents(document.get("participants"))) {
                if (!puuid.equals(participant.getString("puuid"))) continue;
                Document row = new Document("summoner_id", participant.get("summonerId", 0))
                        .append("game_id", gameId)
                        .append("rank", participant.get("rank"))
                        .append("lp", participant.get("lp", 0))
                        .append("gain", participant.get("gain", 0))
                        .append("win", participant.get("win", false))
                        .append("time_start", document.get("timeStart", 0L))
                        .append("time_end", document.get("timeEnd", 0L))
                        .append("patch", document.get("patch"));
                result.add(new MongoRecord("match", document.get("_id") + ":" + puuid, row));
                break;
            }
        }
        return result;
    }

        public static List<MongoRecord> findTrackedSummoners(long timeStart) {
        List<MongoRecord> result = new ArrayList<>();
        for (Document document : summoners().find(Filters.eq("tracking", true))) {
            result.add(record(document));
        }
        return result;
    }

        public static MongoRecord findTrackedSummoner(String puuid, long timeStart) {
        Document document = summoners().find(Filters.and(
                Filters.eq("_id", puuid), Filters.eq("tracking", true))).first();
        return document == null ? null : record(document);
    }

        public static ProfileStatistics findProfileStatistics(String puuid, long seasonStart) {
        Document document = profileStatistics().find(Filters.eq("_id", statisticsId(puuid, seasonStart))).first();
        return document == null ? null : readProfileStatistics(document);
    }

        public static Map<String, ProfileStatistics> findProfileStatistics(List<String> puuids, long seasonStart) {
        if (puuids == null || puuids.isEmpty()) return Map.of();
        Map<String, ProfileStatistics> result = new HashMap<>();
        for (String puuid : puuids) {
            ProfileStatistics statistics = findProfileStatistics(puuid, seasonStart);
            if (statistics != null) result.put(puuid, statistics);
        }
        return result;
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

        public static List<MongoRecord> findChampionBuildSource(Filter filter) {
        return findChampionMatchProjections(findChampionMatchIds(filter, null, 10_000));
    }

        public static List<Filter> findStoredChampionBuildFilters() {
        return readFilters(builds(), false);
    }

        public static List<Filter> findChampionBuildRefreshFilters(String patch) {
        return readFilters(builds(), false);
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
        return readFilters(championStats(), true);
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

        public static List<MongoRecord> findChampionMatchProjections(List<String> fullGameIds) {
        return matchProjections(fullGameIds, false);
    }

        public static List<MongoRecord> findChampionParticipantProjections(List<String> fullGameIds) {
        return matchProjections(fullGameIds, true);
    }

        public static List<MongoRecord> findChampionTrendProjections(List<String> fullGameIds) {
        return matchProjections(fullGameIds, false);
    }

        public static long countLeaderboard(TierType rank, GameQueueType queue, String region) {
        return leaderboard().countDocuments(leaderboardFilter(rank, queue, region));
    }

        public static List<LeaderboardRow> findLeaderboardRows(
            TierType rank,
            GameQueueType queue,
            String region,
            long offset,
            int limit) {
        int boundedLimit = Math.max(0, Math.min(50, limit));
        if (boundedLimit == 0) return List.of();
        List<LeaderboardRow> result = new ArrayList<>();
        for (Document entry : leaderboard().find(leaderboardFilter(rank, queue, region))
                .sort(Sorts.orderBy(Sorts.descending("mmr"), Sorts.ascending("puuid")))
                .skip((int) Math.min(Integer.MAX_VALUE, Math.max(0, offset)))
                .limit(boundedLimit)) {
            String puuid = entry.getString("puuid");
            LeagueShard shard = parseShard(entry.getString("region"));
            Summoner summoner = findSummoner(puuid, shard);
            if (summoner == null) continue;
            result.add(new LeaderboardRow(summoner, rank(entry, queue)));
        }
        return result;
    }

        public static List<LeaderboardDistribution.Entry> findRankDistribution(GameQueueType queue, String region) {
        Map<String, Long> counts = new HashMap<>();
        for (Document entry : leaderboard().find(leaderboardFilter(null, queue, region))) {
            TierDivisionType division = division(entry.getString("rank"));
            String key = division == null || division.getTier() == null ? TierType.UNRANKED.name() : division.getTier();
            counts.merge(key, 1L, Long::sum);
        }
        List<LeaderboardDistribution.Entry> result = new ArrayList<>();
        for (TierType tier : TierType.values()) {
            if (tier != TierType.UNRANKED) result.add(new LeaderboardDistribution.Entry(
                    tier.name(), counts.getOrDefault(tier.name(), 0L)));
        }
        return result;
    }

        public static List<LeaderboardDistribution.Entry> findTopRegions(GameQueueType queue, TierType rank) {
        Map<String, Long> counts = new HashMap<>();
        for (Document entry : leaderboard().find(leaderboardFilter(rank, queue, "GLOBAL"))) {
            counts.merge(entry.getString("region"), 1L, Long::sum);
        }
        List<LeaderboardDistribution.Entry> result = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .forEach(entry -> result.add(new LeaderboardDistribution.Entry(entry.getKey(), entry.getValue())));
        return result;
    }

        public static boolean upsertSummoner(Summoner summoner, String userId) {
        if (summoner == null || summoner.puuid() == null) return false;
        Document document = write(summoner).toDocument();
        Document previous = summoners().find(Filters.eq("_id", summoner.puuid())).first();
        preserve(document, previous, "ranks", "masteries", "lastUpdate");
        if (previous != null && previous.getBoolean("tracking", false)) document.put("tracking", true);
        if (previous != null && previous.getString("userId") != null) document.put("userId", previous.getString("userId"));
        if (userId != null) document.put("userId", userId);
        String riotSearch = normalizedRiotId(summoner.riotId());
        if (!riotSearch.isBlank()) document.put("riotSearch", riotSearch);
        replace(summoners(), document);
        return true;
    }

        public static boolean upsertSummoners(List<Summoner> summoners) {
        if (summoners == null) return false;
        for (Summoner summoner : summoners) upsertSummoner(summoner, null);
        return true;
    }

        public static boolean detachSummonerUser(String puuid, String userId) {
        return summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)),
                Updates.unset("userId")).getMatchedCount() > 0;
    }

        public static boolean setSummonerTracking(String puuid, String userId, boolean tracked) {
        return summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)),
                Updates.set("tracking", tracked)).getMatchedCount() > 0;
    }

    public static boolean upsertRanks(String puuid, LeagueShard shard, List<Rank> ranks, Map<GameQueueType, Long> mmrByQueue) {
        Document document = findDocument(puuid, shard);
        if (document == null) return false;
        List<Document> values = new ArrayList<>();
        if (ranks != null) {
            for (Rank rank : ranks) {
                Document value = write(rank).toDocument();
                if (mmrByQueue != null && mmrByQueue.containsKey(rank.queue())) value.put("mmr", mmrByQueue.get(rank.queue()));
                values.add(value);
            }
        }
        UpdateResult update = summoners().updateOne(Filters.eq("_id", puuid), Updates.set("ranks", values));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo ranks update was not acknowledged");
        return true;
    }

    public static boolean upsertMasteries(String puuid, LeagueShard shard, List<Mastery> masteries) {
        if (findDocument(puuid, shard) == null) return false;
        List<Document> values = new ArrayList<>();
        if (masteries != null) for (Mastery mastery : masteries) values.add(write(mastery).toDocument());
        UpdateResult update = summoners().updateOne(Filters.eq("_id", puuid), Updates.set("masteries", values));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo masteries update was not acknowledged");
        return true;
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
        Document document = write(match).toDocument();
        document.put("_id", id);
        replace(matches(), document);
        upsertMatchEvents(id, match.eventData != null ? match.eventData : match.events == null ? Map.of() : match.events.toMap());
        return true;
    }

        public static boolean upsertParticipant(String fullGameId, Participant participant) {
        String id = fullGameId(fullGameId, null);
        Document match = matches().find(Filters.eq("_id", id)).first();
        if (match == null || participant == null) return false;
        List<Document> values = documents(match.get("participants"));
        Document value = participantDocument(participant);
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            String current = values.get(index).getString("puuid");
            int currentSummonerId = values.get(index).getInteger("summonerId", 0);
            boolean sameParticipant = participant.puuid != null
                    ? participant.puuid.equals(current)
                    : participant.summonerId != 0 && participant.summonerId == currentSummonerId;
            if (sameParticipant) {
                values.set(index, value);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(value);
        UpdateResult update = matches().updateOne(Filters.eq("_id", id), Updates.set("participants", values));
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
        if (matches().countDocuments(Filters.eq("_id", id)) == 0) return false;
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

        public static boolean upsertProfileStatistics(String puuid, long seasonStart, ProfileStatistics statistics) {
        if (statistics == null) return false;
        String encoded = KryoUtils.encode(statistics);
        Document document = new Document("_id", statisticsId(puuid, seasonStart))
                .append("puuid", puuid)
                .append("seasonStart", seasonStart)
                .append("timeStart", statistics.timeStart)
                .append("timeEnd", statistics.timeEnd)
                .append("legacyPayload", encoded);
        Document structured = structured(statistics);
        if (structured != null) document.put("statistics", structured);
        replace(profileStatistics(), document);
        return true;
    }

        public static boolean deleteProfileStatistics(String puuid, long seasonStart) {
        return profileStatistics().deleteOne(Filters.eq("_id", statisticsId(puuid, seasonStart))).getDeletedCount() > 0;
    }

        public static boolean upsertChampionBuild(Build build) {
        if (build == null || build.filter() == null) return false;
        String id = build.filter().toKey();
        String encoded = build.encode();
        Document document = new Document("_id", id)
                .append("filterKey", id)
                .append("games", build.games())
                .append("winrate", build.winrate())
                .append("legacyPayload", encoded);
        Document structured = structured(build);
        if (structured != null) document.put("build", structured);
        replace(builds(), document);
        return true;
    }

        public static boolean upsertChampionBuilds(List<Build> builds) {
        if (builds == null) return false;
        for (Build build : builds) upsertChampionBuild(build);
        return true;
    }

        public static boolean upsertChampionStatistics(ChampionStatistics statistics) {
        if (statistics == null || statistics.filter() == null) return false;
        String filterKey = statistics.filter().genericKey();
        String id = filterKey + ":" + statistics.filter().champion();
        String encoded = statistics.encode();
        Document document = new Document("_id", id)
                .append("filterKey", filterKey)
                .append("championId", statistics.filter().champion())
                .append("legacyPayload", encoded);
        Document structured = structured(statistics);
        if (structured != null) document.put("statistics", structured);
        replace(championStats(), document);
        return true;
    }

        public static boolean upsertChampionStatistics(Map<Integer, ChampionStatistics> statistics) {
        if (statistics == null) return false;
        for (ChampionStatistics value : statistics.values()) upsertChampionStatistics(value);
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
        for (Document entry : leaderboard().find()) {
            TierDivisionType division = division(entry.getString("rank"));
            String tier = division == null || division.getTier() == null ? TierType.UNRANKED.name() : division.getTier();
            String key = entry.getString("queue") + ":" + tier + ":" + entry.getString("region");
            counts.merge(key, 1L, Long::sum);
        }
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String[] parts = entry.getKey().split(":", 3);
            String id = parts[0] + ":" + parts[1] + ":" + parts[2];
            replace(leaderboardDistribution(), new Document("_id", id)
                    .append("queue", parts[0]).append("rank", parts[1]).append("region", parts[2])
                    .append("players", entry.getValue()).append("updatedAt", System.currentTimeMillis()));
        }
        return true;
    }

    public static <T> T read(MongoRecord record, Class<T> type) {
        if (record == null) return null;
        if (type == MongoRecord.class) return type.cast(record);
        try {
            Object value = switch (type.getName()) {
                case "com.safjnest.lol.model.summoner.Summoner" -> readSummoner(record);
                case "com.safjnest.lol.model.summoner.Rank" -> readRank(record);
                case "com.safjnest.lol.model.summoner.Mastery" -> readMastery(record);
                case "com.safjnest.lol.model.match.Participant" -> readParticipant(record);
                case "com.safjnest.lol.model.match.Match" -> readMatch(record);
                case "com.safjnest.lol.model.match.MatchResult" -> readMatchResult(record);
                case "com.safjnest.lol.model.statistics.ProfileStatistics" -> readProfileStatistics(record.toDocument());
                case "com.safjnest.lol.model.Build" -> readBuild(record.toDocument());
                case "com.safjnest.lol.model.ChampionStatistics" -> readChampionStatistics(record.toDocument());
                default -> readStructured(record.toDocument(), type);
            };
            return type.cast(value);
        } catch (MongoRecord.ConversionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw record.conversionException(record.documentField(), type.getName(), record.toDocument(), exception);
        }
    }

    private static MongoRecord write(Object value) {
        if (value == null) throw new IllegalArgumentException("Mongo value cannot be null");
        Document document;
        String collection;
        Object id = null;
        if (value instanceof Summoner summoner) {
            if (summoner.puuid() == null || summoner.puuid().isBlank()) throw new IllegalArgumentException("Summoner.puuid is required");
            document = new Document("_id", summoner.puuid()).append("level", summoner.level()).append("icon", summoner.icon());
            putIfNotNull(document, "riotId", summoner.riotId()); putIfNotNull(document, "region", summoner.region());
            collection = "summoner"; id = summoner.puuid();
        } else if (value instanceof Rank rank) {
            document = new Document("queue", rank.queue() == null ? null : rank.queue().name())
                    .append("rank", rank.tier() == null ? null : rank.tier().name()).append("lp", rank.lp())
                    .append("wins", rank.wins()).append("losses", rank.losses());
            collection = "summoner";
        } else if (value instanceof Mastery mastery) {
            document = new Document("championId", mastery.championId()).append("level", mastery.level()).append("points", mastery.points());
            collection = "summoner";
        } else if (value instanceof Participant participant) {
            document = participantDocument(participant); collection = "match";
        } else if (value instanceof Match match) {
            document = matchDocument(match); collection = "match"; id = document.get("_id");
        } else if (value instanceof MatchResult matchResult) {
            document = matchResultDocument(matchResult); collection = "match"; id = matchResult.gameId;
        } else {
            document = structured(value);
            if (document == null) throw new IllegalArgumentException("Unable to serialize " + value.getClass().getName());
            collection = "lol";
        }
        return new MongoRecord(collection, id, document);
    }

    private static Summoner readSummoner(MongoRecord record) {
        String puuid = record.getAsString("puuid");
        if (puuid == null && record.getId() instanceof String id) puuid = id;
        return new Summoner(record.getAsInt("legacySummonerId"), puuid, record.getAsString("riotId"),
                record.getAsString("region"), record.getAsInt("level"), record.getAsInt("icon"));
    }

    private static Rank readRank(MongoRecord record) {
        return new Rank(record.getAsEnum("queue", GameQueueType.class), record.getAsEnum("rank", TierDivisionType.class),
                record.getAsInt("lp"), record.getAsInt("wins"), record.getAsInt("losses"));
    }

    private static Mastery readMastery(MongoRecord record) {
        return new Mastery(record.getAsInt("championId"), record.getAsInt("level"), record.getAsInt("points"));
    }

    private static Participant readParticipant(MongoRecord record) {
        Document source = record.toDocument();
        Participant participant = new Participant();
        participant.id = source.containsKey("legacyParticipantId") ? record.getAsInt("legacyParticipantId") : record.getAsInt("id");
        participant.summonerId = record.getAsInt("summonerId"); participant.matchId = record.getAsInt("matchId"); participant.win = record.getAsBoolean("win");
        participant.kda = record.getAsString("kda"); participant.champion = record.getAsInt("champion");
        participant.lane = record.getAsEnum("lane", no.stelar7.api.r4j.basic.constants.types.lol.LaneType.class);
        participant.team = record.getAsEnum("team", no.stelar7.api.r4j.basic.constants.types.lol.TeamType.class);
        participant.roleQuestId = record.getAsInt("roleQuestId"); participant.rank = record.getAsEnum("rank", TierDivisionType.class);
        participant.lp = record.getAsInt("lp"); participant.gain = record.getAsInt("gain"); participant.damage = record.getAsInt("damage");
        participant.damageBuilding = record.getAsInt("damageBuilding"); participant.healing = record.getAsInt("healing"); participant.cs = record.getAsInt("cs");
        participant.goldEarned = record.getAsInt("goldEarned"); participant.ward = record.getAsInt("ward"); participant.wardKilled = record.getAsInt("wardKilled");
        participant.visionScore = record.getAsInt("visionScore"); participant.pings = new HashMap<>(readIntegerMap(record, "pings"));
        participant.subTeam = record.getAsInt("subTeam"); participant.subTeamPlacement = record.getAsInt("subTeamPlacement");
        participant.puuid = record.getAsString("puuid"); participant.riotId = record.getAsString("riotId"); participant.riotTag = record.getAsString("riotTag");
        participant.level = record.getAsInt("level"); participant.doubles = record.getAsInt("doubles"); participant.triples = record.getAsInt("triples");
        participant.quadruples = record.getAsInt("quadruples"); participant.pentas = record.getAsInt("pentas");
        participant.item0 = record.getAsInt("item0"); participant.item1 = record.getAsInt("item1"); participant.item2 = record.getAsInt("item2");
        participant.item3 = record.getAsInt("item3"); participant.item4 = record.getAsInt("item4"); participant.item5 = record.getAsInt("item5"); participant.item6 = record.getAsInt("item6");
        participant.q = record.getAsInt("q"); participant.w = record.getAsInt("w"); participant.e = record.getAsInt("e"); participant.r = record.getAsInt("r");
        participant.d = record.getAsInt("d"); participant.f = record.getAsInt("f"); participant.summonerSpell1 = record.getAsInt("summonerSpell1"); participant.summonerSpell2 = record.getAsInt("summonerSpell2");
        participant.primaryRunes = readIntegerList(record, "primaryRunes"); participant.secondaryRunes = readIntegerList(record, "secondaryRunes");
        participant.statsRunes = readIntegerList(record, "statsRunes"); participant.skillOrder = readIntegerList(record, "skillOrder"); participant.augments = readIntegerList(record, "augments");
        participant.starterItems = readIntegerList(record, "starterItems"); participant.buildPath = readIntegerList(record, "buildPath"); participant.boots = record.getAsInt("boots"); participant.supportItem = record.getAsInt("supportItem");
        return participant;
    }

    private static Match readMatch(MongoRecord record) {
        Document source = record.toDocument();
        String fullGameId = record.getAsString("fullGameId"); if (fullGameId == null) fullGameId = record.getAsString("_id");
        if (fullGameId == null || fullGameId.isBlank()) throw record.conversionException("fullGameId", String.class.getName(), fullGameId, null);
        Match match = new Match(); match.id = source.containsKey("legacyMatchId") ? record.getAsInt("legacyMatchId") : record.getAsInt("id");
        String publicId = source.containsKey("game_id") ? record.getAsString("game_id") : record.getAsString("gameId");
        match.gameId = publicId != null ? publicId : publicGameId(fullGameId);
        String region = source.containsKey("region") ? record.getAsString("region") : null;
        match.leagueShard = region == null ? record.getAsEnum("leagueShard", LeagueShard.class) : parseShard(region);
        if (match.leagueShard == null && fullGameId.indexOf('_') > 0) match.leagueShard = parseShard(fullGameId.substring(0, fullGameId.indexOf('_')));
        match.queue = record.getAsEnum("queue", GameQueueType.class); match.rank = record.getAsEnum("rank", TierType.class);
        match.lastUpdate = record.getAsLong("lastUpdate"); match.timeStart = record.getAsLong("timeStart"); match.timeEnd = record.getAsLong("timeEnd"); match.patch = record.getAsString("patch");
        match.bans = readBans(record); match.participants = readParticipants(record);
        match.eventData = record.toDocument().containsKey("events") ? readEventMap(record.get("events")) : new LinkedHashMap<>(); match.restoreEvents();
        return match;
    }

    private static MatchResult readMatchResult(MongoRecord record) {
        return new MatchResult(record.getAsString("gameId"), record.getAsEnum("queue", GameQueueType.class), record.getAsLong("timeStart"), record.getAsLong("timeEnd"),
                record.getAsBoolean("win"), record.getAsString("kda"), record.getAsInt("championId"), record.getAsEnum("lane", no.stelar7.api.r4j.basic.constants.types.lol.LaneType.class),
                record.getAsInt("damage"), record.getAsInt("cs"), record.getAsInt("gold"), record.getAsInt("vision"), record.getAsInt("teamKills"), readIntegerList(record, "items"), readIntegerList(record, "summonerSpells"), readParticipants(record));
    }

    private static Document participantDocument(Participant value) {
        Document document = new Document("legacyParticipantId", value.id).append("summonerId", value.summonerId).append("matchId", value.matchId).append("win", value.win).append("champion", value.champion).append("roleQuestId", value.roleQuestId)
                .append("lp", value.lp).append("gain", value.gain).append("damage", value.damage).append("damageBuilding", value.damageBuilding).append("healing", value.healing).append("cs", value.cs).append("goldEarned", value.goldEarned).append("ward", value.ward).append("wardKilled", value.wardKilled).append("visionScore", value.visionScore).append("pings", integerMapDocument(value.pings))
                .append("subTeam", value.subTeam).append("subTeamPlacement", value.subTeamPlacement).append("level", value.level).append("doubles", value.doubles).append("triples", value.triples).append("quadruples", value.quadruples).append("pentas", value.pentas)
                .append("item0", value.item0).append("item1", value.item1).append("item2", value.item2).append("item3", value.item3).append("item4", value.item4).append("item5", value.item5).append("item6", value.item6).append("q", value.q).append("w", value.w).append("e", value.e).append("r", value.r).append("d", value.d).append("f", value.f).append("summonerSpell1", value.summonerSpell1).append("summonerSpell2", value.summonerSpell2)
                .append("primaryRunes", integerList(value.primaryRunes)).append("secondaryRunes", integerList(value.secondaryRunes)).append("statsRunes", integerList(value.statsRunes)).append("skillOrder", integerList(value.skillOrder)).append("augments", integerList(value.augments)).append("starterItems", integerList(value.starterItems)).append("buildPath", integerList(value.buildPath)).append("boots", value.boots).append("supportItem", value.supportItem);
        putIfNotNull(document, "kda", value.kda); putIfNotNull(document, "puuid", value.puuid); putIfNotNull(document, "riotId", value.riotId); putIfNotNull(document, "riotTag", value.riotTag); putEnum(document, "lane", value.lane); putEnum(document, "team", value.team); putEnum(document, "rank", value.rank);
        return document;
    }

    private static Document matchDocument(Match value) {
        if (value.leagueShard == null) throw new IllegalArgumentException("Match.leagueShard is required");
        String fullGameId = fullGameId(value.gameId, value.leagueShard);
        String publicId = publicGameId(fullGameId);
        Document document = new Document("_id", fullGameId).append("legacyMatchId", value.id).append("fullGameId", fullGameId)
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

    private static List<Integer> readIntegerList(MongoRecord record, String field) {
        List<Integer> values = record.getAsList(field, Integer.class);
        List<Integer> result = new ArrayList<>(values.size());
        for (Integer value : values) {
            if (value == null) throw record.conversionException(field, Integer.class.getName(), null, null);
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

    private static Map<String, Integer> readIntegerMap(MongoRecord record, String field) {
        MongoRecord nested = record.getAsRecord(field);
        if (nested == null) return new LinkedHashMap<>();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : nested.toDocument().keySet()) result.put(key, nested.getAsInt(key));
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

    private static Map<no.stelar7.api.r4j.basic.constants.types.lol.TeamType, List<Integer>> readBans(MongoRecord record) {
        MongoRecord nested = record.getAsRecord("bans");
        Map<no.stelar7.api.r4j.basic.constants.types.lol.TeamType, List<Integer>> result = new HashMap<>();
        result.put(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE, List.of());
        result.put(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED, List.of());
        if (nested == null) return result;
        for (String key : nested.toDocument().keySet()) {
            no.stelar7.api.r4j.basic.constants.types.lol.TeamType team;
            try {
                team = no.stelar7.api.r4j.basic.constants.types.lol.TeamType.valueOf(key);
            } catch (IllegalArgumentException exception) {
                throw record.conversionException("bans." + key, "BLUE or RED", key, exception);
            }
            if (team != no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE && team != no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED) throw record.conversionException("bans." + key, "BLUE or RED", key, null);
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

    private static List<Participant> readParticipants(MongoRecord record) {
        List<Participant> result = new ArrayList<>();
        for (MongoRecord nested : record.getAsRecords("participants")) {
            if (nested == null) throw record.conversionException("participants", Participant.class.getName(), null, null);
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

    private static final class AdvancedChampionAggregate {
        private final int champion;
        private final Map<String, int[]> lanes = new HashMap<>();
        private int games;
        private int wins;
        private int losses;
        private long kills;
        private long deaths;
        private long assists;
        private long totalLpGain;

        private AdvancedChampionAggregate(int champion) {
            this.champion = champion;
        }

        private void add(Document participant) {
            games++;
            if (participant.getBoolean("win", false)) wins++;
            else losses++;
            String kda = participant.getString("kda");
            kills += kdaValue(kda, 0);
            deaths += kdaValue(kda, 1);
            assists += kdaValue(kda, 2);
            totalLpGain += participant.getInteger("gain", 0);

            String lane = participant.getString("lane");
            if (lane != null && !lane.isBlank()) {
                int[] laneStats = lanes.computeIfAbsent(lane, ignored -> new int[2]);
                if (participant.getBoolean("win", false)) laneStats[0]++;
                else laneStats[1]++;
            }
        }

        private double average(long value) {
            return games == 0 ? 0D : (double) value / games;
        }

        private String lanesPlayed() {
            List<String> names = new ArrayList<>(lanes.keySet());
            Collections.sort(names);
            StringBuilder result = new StringBuilder();
            for (String lane : names) {
                if (result.length() > 0) result.append(", ");
                int[] values = lanes.get(lane);
                result.append(lane).append('-').append(values[0]).append('-').append(values[1]);
            }
            return result.toString();
        }
    }

    private static int kdaValue(String kda, int index) {
        if (kda == null || kda.isBlank()) return 0;
        String[] values = kda.split("/", -1);
        if (index >= values.length) return 0;
        try {
            return Integer.parseInt(values[index].trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
        for (Document event : matchEvents().find(Filters.in("_id", ids))) {
            String id = event.getString("_id");
            Match match = byId.get(id);
            if (match == null) continue;
            match.eventData = decodeMatchEvents(event);
            match.restoreEvents();
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
            return JSON.writeValueAsBytes(events == null ? Map.of() : events);
        } catch (JsonProcessingException exception) {
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

    public static void mirrorSummoner(String puuid, LeagueShard shard, String riotId, int level, int icon) {
        mirror("summoner", "summoner", puuid, () -> {
            if (puuid == null || shard == null) return;
            QueryRecord row = LeagueDB.get().lineQuery("SELECT id, user_id, tracking FROM summoner WHERE puuid = '" + sql(puuid) + "' AND region = '" + shard.name() + "'");
            int id = row == null ? 0 : row.getAsInt("id");
            String userId = row == null ? null : row.get("user_id");
            upsertSummoner(new Summoner(id, puuid, riotId, shard.name(), level, icon), userId);
            if (row != null) {
                if (row.getAsInt("tracking") != 0) summoners().updateOne(Filters.eq("_id", puuid), Updates.set("tracking", true));
                else summoners().updateOne(Filters.eq("_id", puuid), Updates.unset("tracking"));
                if (userId == null) summoners().updateOne(Filters.eq("_id", puuid), Updates.unset("userId"));
            }
        });
    }

    public static void detachSummoner(String userId, String puuid) {
        mirror("detachSummoner", "summoner", puuid, () -> {
            if (!summoners().updateOne(Filters.and(Filters.eq("_id", puuid), Filters.eq("userId", userId)), Updates.unset("userId")).wasAcknowledged()) {
                throw new IllegalStateException("Mongo detach was not acknowledged");
            }
        });
    }

    public static void mirrorParticipant(String puuid, int legacyMatchId) {
        mirror("participant", "match", legacyMatchId, () -> {
            QueryRecord row = LeagueDB.get().lineQuery("SELECT game_id, region FROM `match` WHERE id = " + legacyMatchId);
            if (row == null || row.isEmpty()) throw new IllegalStateException("MariaDB match row not found");
            String fullGameId = row.get("region") + "_" + row.get("game_id");
            Match match = LeagueDB.getMatch(LeagueShard.valueOf(row.get("region")), row.get("game_id"));
            if (match == null || match.participants == null) throw new IllegalStateException("MariaDB match participants not found");
            for (Participant participant : match.participants) if (participant != null && puuid.equals(participant.puuid)) {
                if (!upsertParticipant(fullGameId, participant)) throw new IllegalStateException("Mongo match participant update matched no match");
                return;
            }
            throw new IllegalStateException("MariaDB participant row not found");
        });
    }

    public static void mirrorMatch(int legacyMatchId) {
        mirror("match", "match", legacyMatchId, () -> {
            QueryRecord row = LeagueDB.get().lineQuery("SELECT game_id, region FROM `match` WHERE id = " + legacyMatchId);
            if (row == null || row.isEmpty()) throw new IllegalStateException("MariaDB match row not found");
            Match match = LeagueDB.getMatch(LeagueShard.valueOf(row.get("region")), row.get("game_id"));
            if (match == null) throw new IllegalStateException("MariaDB match payload not found");
            if (!upsertMatch(row.get("region") + "_" + row.get("game_id"), match)) throw new IllegalStateException("Mongo match upsert failed");
        });
    }

    public static void mirrorMatchEvents(int legacyMatchId, Map<String, Object> events) {
        mirror("match.events", "match", legacyMatchId, () -> {
            QueryRecord row = LeagueDB.get().lineQuery("SELECT game_id, region FROM `match` WHERE id = " + legacyMatchId);
            if (row == null || row.isEmpty()) throw new IllegalStateException("MariaDB match row not found");
            if (!updateMatchEvents(row.get("region") + "_" + row.get("game_id"), events)) throw new IllegalStateException("Mongo match events update matched no match");
        });
    }

    public static void mirrorMatchEvents(int legacyMatchId, String json) {
        mirror("match.events", "match", legacyMatchId, () -> {
            Map<String, Object> events = new JSONObject(json == null ? "{}" : json).toMap();
            QueryRecord row = LeagueDB.get().lineQuery("SELECT game_id, region FROM `match` WHERE id = " + legacyMatchId);
            if (row == null || row.isEmpty()) throw new IllegalStateException("MariaDB match row not found");
            if (!updateMatchEvents(row.get("region") + "_" + row.get("game_id"), events)) throw new IllegalStateException("Mongo match events update matched no match");
        });
    }

    public static void mirrorMatchRank(int legacyMatchId, TierType rank) {
        mirror("match.rank", "match", legacyMatchId, () -> {
            QueryRecord row = LeagueDB.get().lineQuery("SELECT game_id, region FROM `match` WHERE id = " + legacyMatchId);
            if (row == null || row.isEmpty()) throw new IllegalStateException("MariaDB match row not found");
            if (!updateMatchRank(row.get("region") + "_" + row.get("game_id"), rank)) throw new IllegalStateException("Mongo match rank update matched no match");
        });
    }

    public static void mirrorTracking(String userId, String puuid, boolean tracked) {
        mirror("tracking", "summoner", puuid, () -> setSummonerTracking(puuid, userId, tracked));
    }

    public static void mirrorMasteries(String puuid, LeagueShard shard, List<com.safjnest.lol.model.summoner.Mastery> masteries) {
        mirror("masteries", "summoner", puuid, () -> {
            if (puuid != null && shard != null) upsertMasteries(puuid, shard, masteries);
        });
    }

    public static void mirrorRanks(String puuid, LeagueShard shard, List<Rank> ranks) {
        mirror("ranks", "summoner", puuid, () -> {
            if (puuid != null && shard != null) {
                upsertRanks(puuid, shard, ranks, Map.of());
                if (ranks != null) for (Rank rank : ranks) {
                    long mmr = TierDivisionUtils.getMmr(rank.tier(), rank.lp());
                    upsertLeaderboardEntry(puuid, shard, rank, mmr);
                }
            }
        });
    }

    public static void saveProfileStatistics(String key, String puuid, long timeStart, long timeEnd, byte[] data) {
        mirror("profile_statistics", "profile_statistics", key, () -> {
            if (puuid == null || data == null) return;
            String encoded = Base64.getEncoder().encodeToString(data);
            ProfileStatistics statistics = KryoUtils.decode(encoded, ProfileStatistics.class);
            if (statistics == null) return;
            statistics.timeStart = timeStart; statistics.timeEnd = timeEnd;
            upsertProfileStatistics(puuid, timeStart, statistics);
        });
    }

    private static void mirror(String operation, String collection, Object id, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            try { BotLogger.error("Mongo mirror failed operation=" + operation + " collection=" + collection + " id=" + id + " error=" + exception.getMessage()); }
            catch (RuntimeException ignored) { }
        }
    }

    private static String sql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static MongoRecord findRecord(String puuid, LeagueShard shard) {
        Bson filter = shard == null ? Filters.eq("_id", puuid) : Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()));
        Document document = summoners().find(filter).first();
        return document == null ? null : record(document);
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

    private static MongoRecord matchRecord(Document document) {
        Object id = document.get("_id");
        return new MongoRecord("match", id, document);
    }

    private static MongoRecord profileRecord(Document document) {
        Object id = document.get("_id");
        return new MongoRecord("profile_statistics", id, document);
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

    private static Bson championMatchFilter(Filter filter, String puuid) {
        List<Bson> filters = new ArrayList<>();
        if (filter != null) {
            filters.add(Filters.elemMatch("participants", Filters.eq("champion", filter.champion())));
            if (filter.lane() != null) filters.add(Filters.elemMatch("participants", Filters.eq("lane", filter.lane().name())));
            if (filter.queue() != null) filters.add(Filters.eq("queue", filter.queue().name()));
            if (filter.patch() != null) filters.add(Filters.eq("patch", filter.patch()));
            if (filter.region() != null) filters.add(Filters.eq("leagueShard", filter.region().name()));
            if (filter.rank() != null) filters.add(Filters.in("rank", divisionNames(filter.rank())));
        }
        if (puuid != null) filters.add(Filters.elemMatch("participants", Filters.eq("puuid", puuid)));
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static Bson leaderboardFilter(TierType rank, GameQueueType queue, String region) {
        List<Bson> filters = new ArrayList<>();
        if (queue != null) filters.add(Filters.eq("queue", queue.name()));
        if (region != null && !"GLOBAL".equals(region)) filters.add(Filters.eq("region", region));
        if (rank != null) filters.add(Filters.in("rank", divisionNames(rank)));
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    private static List<MongoRecord> matchProjections(List<String> fullGameIds, boolean participantsOnly) {
        if (fullGameIds == null || fullGameIds.isEmpty()) return List.of();
        List<MongoRecord> result = new ArrayList<>();
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

    private static List<String> divisionNames(TierType tier) {
        if (tier == null) return List.of();
        List<String> result = new ArrayList<>();
        for (TierDivisionType division : TierDivisionType.values()) {
            if (tier.name().equals(division.getTier())) result.add(division.name());
        }
        return result;
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

    private static String statisticsId(String puuid, long seasonStart) {
        if (puuid == null || puuid.isBlank()) throw new IllegalArgumentException("puuid is required");
        return puuid + ":" + seasonStart;
    }

    private static ProfileStatistics decodeProfileStatistics(String encoded) {
        return encoded == null ? null : KryoUtils.decode(encoded, ProfileStatistics.class);
    }

    private static ProfileStatistics readProfileStatistics(Document document) {
        ProfileStatistics statistics = decodeProfileStatistics(payload(document));
        if (statistics != null) return statistics;
        ProfileStatistics structuredStatistics = readStructured(document.get("statistics"), ProfileStatistics.class);
        return structuredStatistics != null ? structuredStatistics : readStructured(document, ProfileStatistics.class);
    }

    private static Build readBuild(Document document) {
        Build build = Build.decode(payload(document));
        if (build != null) return build;
        Build structuredBuild = readStructured(document.get("build"), Build.class);
        return structuredBuild != null ? structuredBuild : readStructured(document, Build.class);
    }

    private static ChampionStatistics readChampionStatistics(Document document) {
        ChampionStatistics statistics = ChampionStatistics.decode(payload(document));
        if (statistics != null) return statistics;
        ChampionStatistics structuredStatistics = readStructured(document.get("statistics"), ChampionStatistics.class);
        return structuredStatistics != null ? structuredStatistics : readStructured(document, ChampionStatistics.class);
    }

    private static String payload(Document document) {
        String payload = document.getString("legacyPayload");
        return payload == null ? document.getString("data") : payload;
    }

    private static <T> T readStructured(Object value, Class<T> type) {
        if (value == null) return null;
        try {
            return JSON.readValue(JSON.writeValueAsString(value), type);
        } catch (RuntimeException | JsonProcessingException ignored) {
            return null;
        }
    }

    private static Document structured(Object value) {
        if (value == null) return null;
        try {
            return Document.parse(JSON.writeValueAsString(value));
        } catch (RuntimeException | JsonProcessingException ignored) {
            return null;
        }
    }

    private static List<Document> documents(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Document> result = new ArrayList<>(list.size());
        for (Object item : list) if (item instanceof Document document) result.add(new Document(document));
        return result;
    }

    private static void preserve(Document target, Document source, String... fields) {
        if (source == null) return;
        for (String field : fields) if (source.containsKey(field)) target.put(field, source.get(field));
    }

    private static void replace(MongoCollection<Document> collection, Document document) {
        UpdateResult update = collection.replaceOne(Filters.eq("_id", document.get("_id")), document, new ReplaceOptions().upsert(true));
        if (!update.wasAcknowledged()) throw new IllegalStateException("Mongo replace was not acknowledged for id=" + document.get("_id"));
    }

    private static Document findDocument(String puuid, LeagueShard shard) {
        if (shard == null || shard == LeagueShard.UNKNOWN) return summoners().find(Filters.eq("_id", puuid)).first();
        return summoners().find(Filters.and(Filters.eq("_id", puuid), Filters.eq("region", shard.name()))).first();
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

    private static MongoRecord record(Document document) {
        Object id = document.get("_id");
        return new MongoRecord("summoner", id, document);
    }
}
