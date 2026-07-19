package com.safjnest.mongo;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final int DEFAULT_BATCH_SIZE = 50_000;
    private static final int MAX_BATCH_SIZE = 50_000;
    private static final int MAX_MATCH_BATCH_SIZE = 5_000;
    private static final int MAX_REPORT_IDENTITIES = 100;
    private static final List<String> PHASES = List.of("summoners", "matches", "ranks", "masteries");

    private MongoMigration() {
    }

    public static MigrationReport migrateAll() {
        return migrateAll(Options.defaults());
    }

    public static MigrationReport migrateAll(Options options) {
        Options effective = options == null ? Options.defaults() : options;
        MigrationReport report = new MigrationReport(effective.dryRun());
        for (String phase : PHASES) migratePhase(phase, effective, report);
        return report;
    }

    private static void migratePhase(String phase, Options options, MigrationReport report) {
        Checkpoint checkpoint = options.resume() ? readCheckpoint(options.runId(), phase) : Checkpoint.empty();
        long highWaterMark = checkpoint.highWaterMark();
        long processed = checkpoint.processed();
        String checksum = checkpoint.checksum();
        boolean completed = true;

        while (true) {
            int pageSize = pageSize(phase, options.batchSize());
            if ("matches".equals(phase)) {
                List<Match> matches = LeagueDB.get().getMatchesAfterId(asInt(highWaterMark), pageSize);
                if (matches.isEmpty()) break;
                boolean stopped = false;
                for (Match match : matches) {
                    if (match.id <= highWaterMark) continue;
                    if (options.highWaterMark() > 0 && match.id > options.highWaterMark()) {
                        completed = false;
                        stopped = true;
                        break;
                    }
                    String identity = matchIdentity(match);
                    try {
                        Document document = MongoDB.toDocument(match);
                        checksum = checksum(checksum, document);
                        checksum = checksum(checksum, new Document("_id", identity)
                                .append("events", match.eventData != null ? match.eventData : match.events == null ? Map.of() : match.events.toMap()));
                        if (!options.dryRun()) MongoDB.upsertMatch(identity, match);
                        highWaterMark = match.id;
                        processed++;
                        report.accept(phase, identity);
                    } catch (RuntimeException exception) {
                        throw migrationFailure(phase, identity, exception);
                    }
                }
                if (!options.dryRun()) writeCheckpoint(options, phase, highWaterMark, checksum, processed, stopped ? "PAUSED" : "RUNNING");
                if (stopped) break;
                continue;
            }

            QueryResult rows = queryPage(phase, highWaterMark, pageSize);
            if (rows.isEmpty()) break;
            Map<String, List<Document>> embedded = new LinkedHashMap<>();
            boolean stopped = false;
            for (QueryRecord row : rows) {
                long rowHighWaterMark = row.getAsLong("id");
                if (rowHighWaterMark <= highWaterMark) continue;
                if (options.highWaterMark() > 0 && rowHighWaterMark > options.highWaterMark()) {
                    completed = false;
                    stopped = true;
                    break;
                }
                String identity = identity(phase, row);
                try {
                    if ("summoners".equals(phase)) {
                        Document document = convertSummoner(row);
                        checksum = checksum(checksum, document);
                        if (!options.dryRun()) MongoDB.upsertDocument(collection(phase), document);
                    } else {
                        String puuid = required(row, "puuid");
                        embedded.computeIfAbsent(puuid, ignored -> new ArrayList<>()).add(convertEmbedded(phase, row));
                        checksum = checksum(checksum, embedded.get(puuid).get(embedded.get(puuid).size() - 1));
                    }
                    highWaterMark = rowHighWaterMark;
                    processed++;
                    report.accept(phase, identity);
                } catch (RuntimeException exception) {
                    throw migrationFailure(phase, identity, exception);
                }
            }
            if (!options.dryRun()) {
                mergeEmbedded(phase, embedded);
                writeCheckpoint(options, phase, highWaterMark, checksum, processed, stopped ? "PAUSED" : "RUNNING");
            }
            if (stopped) break;
        }

        if (!options.dryRun()) writeCheckpoint(options, phase, highWaterMark, checksum, processed, completed ? "COMPLETED" : "PAUSED");
    }

    private static QueryResult queryPage(String phase, long highWaterMark, int pageSize) {
        String query = switch (phase) {
            case "summoners" -> "SELECT id, puuid, riot_id, region, level, icon, user_id, tracking, last_update FROM summoner WHERE id > "
                    + highWaterMark + " ORDER BY id ASC LIMIT " + pageSize;
            case "ranks" -> "SELECT r.id, r.summoner_id, s.puuid, r.region, r.queue, r.`rank`, r.lp, r.mmr, r.wins, r.losses, r.last_update "
                    + "FROM `rank` r JOIN summoner s ON s.id = r.summoner_id WHERE r.id > " + highWaterMark
                    + " ORDER BY r.id ASC LIMIT " + pageSize;
            case "masteries" -> "SELECT m.id, m.summoner_id, s.puuid, m.champion_id, m.champion_level, m.champion_points, m.last_play_time "
                    + "FROM masteries m JOIN summoner s ON s.id = m.summoner_id WHERE m.id > " + highWaterMark
                    + " ORDER BY m.id ASC LIMIT " + pageSize;
            default -> throw new IllegalArgumentException("Unknown migration query phase " + phase);
        };
        QueryResult result = LeagueDB.get().query(query);
        if (!result.isSuccess()) throw new IllegalStateException("MariaDB migration query failed phase=" + phase);
        return result;
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
            case "ranks" -> new Document("legacyRankId", row.getAsInt("id"))
                    .append("region", row.get("region"))
                    .append("queue", row.get("queue"))
                    .append("rank", row.get("rank"))
                    .append("lp", row.getAsInt("lp"))
                    .append("mmr", row.getAsInt("mmr"))
                    .append("wins", row.getAsInt("wins"))
                    .append("losses", row.getAsInt("losses"))
                    .append("lastUpdate", epochMillis(row, "last_update"));
            case "masteries" -> new Document("legacyMasteryId", row.getAsInt("id"))
                    .append("championId", row.getAsInt("champion_id"))
                    .append("level", row.getAsInt("champion_level"))
                    .append("points", row.getAsInt("champion_points"))
                    .append("lastPlayTime", epochMillis(row, "last_play_time"));
            default -> throw new IllegalArgumentException("Unknown embedded migration phase " + phase);
        };
    }

    private static void mergeEmbedded(String phase, Map<String, List<Document>> values) {
        if (values.isEmpty()) return;
        String field = "ranks".equals(phase) ? "ranks" : "masteries";
        String identityField = "ranks".equals(phase) ? "queue" : "championId";
        for (Map.Entry<String, List<Document>> entry : values.entrySet()) {
            if (!MongoDB.mergeSummonerEmbedded(entry.getKey(), field, identityField, entry.getValue())) {
                throw new IllegalStateException("Summoner document is missing before embedded phase=" + phase + " puuid=" + entry.getKey());
            }
        }
    }

    private static int pageSize(String phase, int requested) {
        return "matches".equals(phase) ? Math.min(requested, MAX_MATCH_BATCH_SIZE) : requested;
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
        MongoRecord record = MongoDB.findRecord(CHECKPOINT_COLLECTION, runId + ":" + phase);
        if (record == null) return Checkpoint.empty();
        return new Checkpoint(record.getAsLong("highWaterMark"), record.getAsString("checksum"), record.getAsLong("processed"));
    }

    private static void writeCheckpoint(Options options, String phase, long highWaterMark, String checksum, long processed, String status) {
        MongoDB.upsertDocument(CHECKPOINT_COLLECTION, new Document("_id", options.runId() + ":" + phase)
                .append("runId", options.runId()).append("phase", phase).append("status", status)
                .append("highWaterMark", highWaterMark).append("checksum", checksum).append("processed", processed)
                .append("batchSize", options.batchSize()).append("updatedAt", System.currentTimeMillis()));
    }

    private static String collection(String phase) {
        return switch (phase) {
            case "summoners" -> "summoner";
            case "matches" -> "match";
            default -> throw new IllegalArgumentException("Phase " + phase + " has no top-level collection");
        };
    }

    private static String matchIdentity(Match match) {
        return match.gameId != null && match.gameId.indexOf('_') > 0
                ? match.gameId
                : match.leagueShard + "_" + match.gameId;
    }

    private static String identity(String phase, QueryRecord row) {
        return switch (phase) {
            case "summoners" -> row.get("puuid");
            case "ranks" -> row.get("puuid") + ":" + row.get("queue");
            case "masteries" -> row.get("puuid") + ":" + row.get("champion_id");
            default -> phase + ":" + row.get("id");
        };
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

    private static String checksum(String previous, Document document) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((previous == null ? "" : previous).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest(document.toJson().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
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

    private record Checkpoint(long highWaterMark, String checksum, long processed) {
        private static Checkpoint empty() { return new Checkpoint(0, "", 0); }
    }
}
