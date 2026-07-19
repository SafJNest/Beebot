package com.safjnest.mongo;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.safjnest.lol.model.match.Match;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

public final class MongoMigration {

    private static final String CHECKPOINT_COLLECTION = "migration_runs";
    private static final String MIGRATION_VERSION = "raw-v4-bulk-no-checksum";
    private static final int DEFAULT_BATCH_SIZE = 125_000;
    private static final int MAX_BATCH_SIZE = 125_000;
    private static final int MAX_MATCH_BATCH_SIZE = 10_000;
    private static final int MONGO_WRITE_BATCH_SIZE = 2_000;
    private static final int MAX_REPORT_IDENTITIES = 100;
    private static final List<String> PHASES = List.of("matches");

    private MongoMigration() {
    }

    public static MigrationReport migrateAll() {
        return migrateAll(Options.defaults());
    }

    public static MigrationReport migrateAll(Options options) {
        Options effective = options == null ? Options.defaults() : options;
        MigrationReport report = new MigrationReport(effective.dryRun());
        MongoDB.beginMigration();
        boolean completed = false;
        try {
            for (String phase : PHASES) migratePhase(phase, effective, report);
            completed = true;
            return report;
        } finally {
            MongoDB.finishMigration(!effective.dryRun() && completed);
        }
    }

    private static void migratePhase(String phase, Options options, MigrationReport report) {
        Checkpoint checkpoint = options.resume() ? readCheckpoint(options.runId(), phase) : Checkpoint.empty();
        if ("summoners".equals(phase)) {
            migrateSummoners(options, report, checkpoint);
            return;
        }
        long highWaterMark = checkpoint.highWaterMark();
        long processed = checkpoint.processed();
        boolean completed = true;

        while (true) {
            List<Match> matches = LeagueDB.get().getMatchesAfterId(asInt(highWaterMark), pageSize(phase, options.batchSize()));
            if (matches.isEmpty()) break;
            boolean stopped = false;
            try {
                for (Match match : matches) {
                    if (match.id <= highWaterMark) continue;
                    if (options.highWaterMark() > 0 && match.id > options.highWaterMark()) {
                        completed = false;
                        stopped = true;
                        break;
                    }
                    String identity = matchIdentity(match);
                    try {
                        if (!options.dryRun()) MongoDB.upsertMatch(identity, match);
                        highWaterMark = match.id;
                        processed++;
                        report.accept(phase, identity);
                    } catch (RuntimeException exception) {
                        throw migrationFailure(phase, identity, exception);
                    }
                }
                if (!options.dryRun()) writeCheckpoint(options, phase, highWaterMark, processed, stopped ? "PAUSED" : "RUNNING");
            } finally {
                matches.clear();
            }
            if (stopped) break;
        }

        if (!options.dryRun()) writeCheckpoint(options, phase, highWaterMark, processed, completed ? "COMPLETED" : "PAUSED");
    }

    private static void migrateSummoners(Options options, MigrationReport report, Checkpoint checkpoint) {
        long highWaterMark = checkpoint.highWaterMark();
        long processed = checkpoint.processed();
        boolean completed = true;

        while (true) {
            QueryResult rows = querySummonerPage(highWaterMark, options.batchSize());
            if (rows.isEmpty()) break;
            Map<String, Document> documents = new LinkedHashMap<>();
            long batchHighWaterMark = highWaterMark;
            boolean stopped = false;
            try {
                for (QueryRecord row : rows) {
                    long rowHighWaterMark = row.getAsLong("id");
                    if (rowHighWaterMark <= highWaterMark) continue;
                    if (options.highWaterMark() > 0 && rowHighWaterMark > options.highWaterMark()) {
                        completed = false;
                        stopped = true;
                        break;
                    }
                    String puuid = required(row, "puuid");
                    documents.put(puuid, convertSummoner(row));
                    batchHighWaterMark = rowHighWaterMark;
                }

                if (documents.isEmpty()) {
                    if (stopped) break;
                    continue;
                }

                loadEmbeddedRows("ranks", highWaterMark, batchHighWaterMark, documents);
                loadEmbeddedRows("masteries", highWaterMark, batchHighWaterMark, documents);
                if (!options.dryRun()) MongoDB.bulkUpsertDocuments("summoner", documents.values(), MONGO_WRITE_BATCH_SIZE);
                for (String puuid : documents.keySet()) {
                    processed++;
                    report.accept("summoners", puuid);
                }
                highWaterMark = batchHighWaterMark;
                if (!options.dryRun()) writeCheckpoint(options, "summoners", highWaterMark, processed, stopped ? "PAUSED" : "RUNNING");
                if (stopped) break;
            } finally {
                rows.clear();
                documents.clear();
            }
        }

        if (!options.dryRun()) writeCheckpoint(options, "summoners", highWaterMark, processed, completed ? "COMPLETED" : "PAUSED");
    }

    private static QueryResult querySummonerPage(long highWaterMark, int pageSize) {
        String query = "SELECT id, puuid, riot_id, region, level, icon, user_id, tracking, last_update FROM summoner WHERE id > "
                + highWaterMark + " ORDER BY id ASC LIMIT " + pageSize;
        QueryResult result = LeagueDB.get().query(query);
        if (!result.isSuccess()) throw new IllegalStateException("MariaDB migration query failed phase=summoners");
        return result;
    }

    private static QueryResult queryEmbeddedRange(String phase, long fromSummonerId, long toSummonerId) {
        String query = switch (phase) {
            case "ranks" -> "SELECT r.id, r.summoner_id, s.puuid, r.region, r.queue, r.`rank`, r.lp, r.mmr, r.wins, r.losses, r.last_update "
                    + "FROM `rank` r JOIN summoner s ON s.id = r.summoner_id WHERE s.id > " + fromSummonerId
                    + " AND s.id <= " + toSummonerId + " ORDER BY r.id ASC";
            case "masteries" -> "SELECT m.id, m.summoner_id, s.puuid, m.champion_id, m.champion_level, m.champion_points, m.last_play_time "
                    + "FROM masteries m JOIN summoner s ON s.id = m.summoner_id WHERE s.id > " + fromSummonerId
                    + " AND s.id <= " + toSummonerId + " ORDER BY m.id ASC";
            default -> throw new IllegalArgumentException("Unknown embedded migration phase " + phase);
        };
        QueryResult result = LeagueDB.get().query(query);
        if (!result.isSuccess()) throw new IllegalStateException("MariaDB migration query failed phase=" + phase);
        return result;
    }

    private static void loadEmbeddedRows(String phase, long fromSummonerId, long toSummonerId, Map<String, Document> summoners) {
        QueryResult rows = queryEmbeddedRange(phase, fromSummonerId, toSummonerId);
        try {
            for (QueryRecord row : rows) {
                String puuid = required(row, "puuid");
                Document summoner = summoners.get(puuid);
                if (summoner == null) throw new IllegalStateException("Embedded row has no summoner in batch phase=" + phase + " puuid=" + puuid);
                appendEmbedded(summoner, phase, convertEmbedded(phase, row));
            }
        } finally {
            rows.clear();
        }
    }

    private static void appendEmbedded(Document summoner, String phase, Document value) {
        String field = "ranks".equals(phase) ? "ranks" : "masteries";
        String identityField = "ranks".equals(phase) ? "queue" : "championId";
        List<Document> values = new ArrayList<>();
        Object existing = summoner.get(field);
        if (existing instanceof List<?> list) for (Object item : list) if (item instanceof Document document) values.add(new Document(document));
        String identity = String.valueOf(value.get(identityField));
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            if (identity.equals(String.valueOf(values.get(index).get(identityField)))) {
                values.set(index, value);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(value);
        summoner.put(field, values);
    }

    private static Document convertSummoner(QueryRecord row) {
        String puuid = required(row, "puuid");
        Document document = new Document("_id", puuid)
                .append("riotId", row.get("riot_id"))
                .append("region", row.get("region"))
                .append("level", row.getAsInt("level"))
                .append("icon", row.getAsInt("icon"))
                .append("riotSearch", normalize(row.get("riot_id")));
        if (row.get("user_id") != null && !row.get("user_id").isBlank()) document.put("userId", row.get("user_id"));
        if (row.getAsBoolean("tracking")) document.put("tracking", true);
        long lastUpdate = epochMillis(row, "last_update");
        if (lastUpdate != 0) document.put("lastUpdate", lastUpdate);
        return document;
    }

    private static Document convertEmbedded(String phase, QueryRecord row) {
        return switch (phase) {
            case "ranks" -> new Document("region", row.get("region"))
                    .append("queue", row.get("queue"))
                    .append("rank", row.get("rank"))
                    .append("lp", row.getAsInt("lp"))
                    .append("mmr", row.getAsInt("mmr"))
                    .append("wins", row.getAsInt("wins"))
                    .append("losses", row.getAsInt("losses"))
                    .append("lastUpdate", epochMillis(row, "last_update"));
            case "masteries" -> new Document("championId", row.getAsInt("champion_id"))
                    .append("level", row.getAsInt("champion_level"))
                    .append("points", row.getAsInt("champion_points"))
                    .append("lastPlayTime", epochMillis(row, "last_play_time"));
            default -> throw new IllegalArgumentException("Unknown embedded migration phase " + phase);
        };
    }

    private static int pageSize(String phase, int requested) {
        return "matches".equals(phase) ? Math.min(requested, MAX_MATCH_BATCH_SIZE) : Math.min(requested, MAX_BATCH_SIZE);
    }

    private static int asInt(long value) {
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Migration high-water mark exceeds MariaDB integer id range");
        return (int) Math.max(0, value);
    }

    private static long epochMillis(QueryRecord row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) return 0;
        try {
            return Timestamp.valueOf(value).getTime();
        } catch (IllegalArgumentException exception) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }

    private static Checkpoint readCheckpoint(String runId, String phase) {
        MongoRecord record = MongoDB.findRecord(CHECKPOINT_COLLECTION, checkpointId(runId, phase));
        if (record == null) return Checkpoint.empty();
        return new Checkpoint(record.getAsLong("highWaterMark"), record.getAsLong("processed"));
    }

    private static void writeCheckpoint(Options options, String phase, long highWaterMark, long processed, String status) {
        MongoDB.upsertDocument(CHECKPOINT_COLLECTION, new Document("_id", checkpointId(options.runId(), phase))
                .append("runId", options.runId()).append("version", MIGRATION_VERSION).append("phase", phase).append("status", status)
                .append("highWaterMark", highWaterMark).append("processed", processed)
                .append("batchSize", options.batchSize()).append("updatedAt", System.currentTimeMillis()));
    }

    private static String checkpointId(String runId, String phase) {
        return MIGRATION_VERSION + ":" + runId + ":" + phase;
    }

    private static String matchIdentity(Match match) {
        return match.gameId != null && match.gameId.indexOf('_') > 0
                ? match.gameId
                : match.leagueShard + "_" + match.gameId;
    }

    private static String required(QueryRecord row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing MariaDB field " + field);
        return value;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.replace("#", "").replace("-", "").replaceAll("\\s+", "");
    }

    private static IllegalStateException migrationFailure(String phase, String identity, RuntimeException exception) {
        return new IllegalStateException("Mongo migration conversion failed phase=" + phase + " id=" + identity, exception);
    }

    public record Options(boolean dryRun, int batchSize, String runId, boolean resume, long highWaterMark) {
        public Options {
            if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
            if (runId == null || runId.isBlank()) runId = "default";
        }

        public static Options defaults() {
            return new Options(false, DEFAULT_BATCH_SIZE, "default", true, 0);
        }
    }

    public static final class MigrationReport {
        private final boolean dryRun;
        private final Map<String, Integer> processed = new LinkedHashMap<>();
        private final List<String> identities = new ArrayList<>();

        private MigrationReport(boolean dryRun) {
            this.dryRun = dryRun;
        }

        private void accept(String phase, String identity) {
            processed.merge(phase, 1, Integer::sum);
            if (identities.size() < MAX_REPORT_IDENTITIES) identities.add(phase + ":" + identity);
        }

        public boolean dryRun() { return dryRun; }
        public Map<String, Integer> processed() { return Map.copyOf(processed); }
        public List<String> identities() { return List.copyOf(identities); }
    }

    private record Checkpoint(long highWaterMark, long processed) {
        private static Checkpoint empty() { return new Checkpoint(0, 0); }
    }
}
