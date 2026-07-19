package com.safjnest.mongo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.KryoUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class MongoMigration {

    private static final String CHECKPOINT_COLLECTION = "lol_migration_runs";
    private static final List<String> PHASES = List.of("summoners", "matches", "profile_statistics");

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
        QueryResult rows = switch (phase) {
            case "summoners" -> LeagueDB.get().query("SELECT id, puuid, riot_id, region, level, icon, user_id, tracking FROM summoner ORDER BY id ASC");
            case "matches" -> LeagueDB.get().query("SELECT id, game_id, region FROM `match` ORDER BY id ASC");
            case "profile_statistics" -> LeagueDB.get().query("SELECT `key`, summoner_id, time_start, time_end, data FROM profile_statistics ORDER BY `key` ASC");
            default -> throw new IllegalArgumentException("Unknown migration phase " + phase);
        };

        int batchCount = 0;
        long highWaterMark = checkpoint.highWaterMark();
        String checksum = checkpoint.checksum();
        int processed = 0;
        for (QueryRecord row : rows) {
            String identity = identity(phase, row);
            long rowHighWaterMark = highWaterMark(phase, row, processed);
            if (rowHighWaterMark <= checkpoint.highWaterMark()) continue;
            if (options.highWaterMark() > 0 && rowHighWaterMark > options.highWaterMark()) break;
            try {
                Document document = convert(phase, row);
                checksum = checksum(checksum, document);
                if (!options.dryRun()) MongoDB.upsertDocument(collection(phase), document);
                highWaterMark = rowHighWaterMark;
                processed++;
                batchCount++;
                report.accept(phase, identity);
                if (batchCount >= options.batchSize()) {
                    writeCheckpoint(options, phase, highWaterMark, checksum, processed);
                    batchCount = 0;
                }
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Mongo migration conversion failed phase=" + phase + " id=" + identity, exception);
            }
        }
        if (!options.dryRun()) writeCheckpoint(options, phase, highWaterMark, checksum, processed);
    }

    private static Document convert(String phase, QueryRecord row) {
        return switch (phase) {
            case "summoners" -> new Document("_id", required(row, "puuid"))
                    .append("legacySummonerId", row.getAsInt("id"))
                    .append("puuid", required(row, "puuid"))
                    .append("riotId", row.get("riot_id"))
                    .append("region", row.get("region"))
                    .append("level", row.getAsInt("level"))
                    .append("icon", row.getAsInt("icon"))
                    .append("userId", row.get("user_id"))
                    .append("tracking", row.getAsInt("tracking") != 0)
                    .append("riotSearch", normalize(row.get("riot_id")));
            case "matches" -> convertMatch(row);
            case "profile_statistics" -> convertProfileStatistics(row);
            default -> throw new IllegalArgumentException("Unknown migration phase " + phase);
        };
    }

    private static Document convertMatch(QueryRecord row) {
        String region = required(row, "region");
        String gameId = required(row, "game_id");
        Match match = LeagueDB.getMatch(LeagueShard.valueOf(region), gameId);
        if (match == null) throw new IllegalStateException("MariaDB match is not readable");
        return MongoDB.toDocument(match);
    }

    private static Document convertProfileStatistics(QueryRecord row) {
        String data = required(row, "data");
        ProfileStatistics statistics = KryoUtils.decode(data, ProfileStatistics.class);
        if (statistics == null) throw new IllegalStateException("Profile statistics payload is not decodable");
        statistics.timeStart = row.getAsTimestamp("time_start") == null ? row.getAsLong("time_start") : row.getAsTimestamp("time_start").getTime();
        statistics.timeEnd = row.getAsTimestamp("time_end") == null ? row.getAsLong("time_end") : row.getAsTimestamp("time_end").getTime();
        String key = required(row, "key");
        int separator = key.indexOf('|');
        if (separator < 1) throw new IllegalStateException("Profile statistics key must contain legacy summoner id and season start");
        Summoner summoner = MongoDB.findSummonerByLegacyId(Integer.parseInt(key.substring(0, separator)));
        if (summoner == null) throw new IllegalStateException("Profile statistics summoner is not migrated");
        return new Document("_id", summoner.puuid() + ":" + key.substring(separator + 1))
                .append("puuid", summoner.puuid()).append("seasonStart", Long.parseLong(key.substring(separator + 1)))
                .append("timeStart", statistics.timeStart).append("timeEnd", statistics.timeEnd)
                .append("legacyPayload", data).append("statistics", MongoDB.toDocument(statistics));
    }

    private static Checkpoint readCheckpoint(String runId, String phase) {
        MongoRecord record = MongoDB.findRecord(CHECKPOINT_COLLECTION, runId + ":" + phase);
        if (record == null) return Checkpoint.empty();
        return new Checkpoint(record.getAsLong("highWaterMark"), record.getAsString("checksum"));
    }

    private static void writeCheckpoint(Options options, String phase, long highWaterMark, String checksum, int processed) {
        MongoDB.upsertDocument(CHECKPOINT_COLLECTION, new Document("_id", options.runId() + ":" + phase)
                .append("runId", options.runId()).append("phase", phase).append("status", "RUNNING")
                .append("highWaterMark", highWaterMark).append("checksum", checksum).append("processed", processed)
                .append("updatedAt", System.currentTimeMillis()));
    }

    private static String collection(String phase) {
        return switch (phase) {
            case "summoners" -> "lol_summoners";
            case "matches" -> "lol_matches";
            case "profile_statistics" -> "lol_profile_statistics";
            default -> throw new IllegalArgumentException("Unknown migration phase " + phase);
        };
    }

    private static long highWaterMark(String phase, QueryRecord row, int ordinal) {
        return "summoners".equals(phase) || "matches".equals(phase) ? row.getAsLong("id") : ordinal + 1L;
    }

    private static String identity(String phase, QueryRecord row) {
        return switch (phase) {
            case "summoners" -> row.get("puuid");
            case "matches" -> row.get("region") + "_" + row.get("game_id");
            case "profile_statistics" -> row.get("key");
            default -> phase;
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

    public record Options(boolean dryRun, int batchSize, String runId, boolean resume, long highWaterMark) {
        public Options {
            if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
            if (runId == null || runId.isBlank()) runId = "default";
        }

        public static Options defaults() {
            return new Options(false, 250, "default", true, 0);
        }
    }

    public static final class MigrationReport {
        private final boolean dryRun;
        private final Map<String, Integer> processed = new LinkedHashMap<>();
        private final List<String> identities = new ArrayList<>();

        private MigrationReport(boolean dryRun) { this.dryRun = dryRun; }

        private void accept(String phase, String identity) {
            processed.merge(phase, 1, Integer::sum);
            identities.add(phase + ":" + identity);
        }

        public boolean dryRun() { return dryRun; }
        public Map<String, Integer> processed() { return Map.copyOf(processed); }
        public List<String> identities() { return List.copyOf(identities); }
    }

    private record Checkpoint(long highWaterMark, String checksum) {
        private static Checkpoint empty() { return new Checkpoint(0, ""); }
    }
}
