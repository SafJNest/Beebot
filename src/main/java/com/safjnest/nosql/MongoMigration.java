package com.safjnest.nosql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.log.BotLogger;

public final class MongoMigration {

    private static final String CHECKPOINT_COLLECTION = "migration_runs";
    private static final String RANK_PROGRESS_SCHEMA_PHASE = "rank-progress-schema-v1";
    private static final String RANK_PROGRESS_HISTORY_PHASE = "rank-progress-history-v1";
    private static final int DEFAULT_BATCH_SIZE = 500_000;
    private static final int MAX_BATCH_SIZE = 500_000;
    private static final int MAX_MATCH_BATCH_SIZE = 50_000;
    private static final int MAX_REPORT_IDENTITIES = 100;

    private MongoMigration() {
    }

    public static MigrationReport migrateRankProgress() {
        return migrateRankProgress(Options.defaults());
    }

    public static MigrationReport migrateRankProgress(Options options) {
        Options effective = options == null ? Options.defaults() : options;
        MigrationReport report = new MigrationReport(effective.dryRun());
        try {
            migrateRankProgress(effective, report);
            return report;
        } finally {
            requestCollection();
        }
    }

    public static MigrationReport rebuildTrackedRankProgress() {
        MigrationReport report = new MigrationReport(false);
        int summoners = 0;
        BotLogger.info("[FixTracked] Starting Mongo RankProgress rebuild");
        try (com.mongodb.client.MongoCursor<Document> cursor = MongoDB.trackedSummonerCursor()) {
            while (cursor.hasNext()) {
                String puuid = cursor.next().getString("_id");
                if (puuid == null || puuid.isBlank()) continue;
                for (String region : MongoDB.findRankProgressRegions(puuid)) {
                    int updated = MongoDB.repairRankProgressHistory(
                            new MongoDB.RankProgressSubject(region, puuid), false);
                    for (int index = 0; index < updated; index++) report.accept("fix-tracked-matches", region + "|" + puuid);
                }
                report.accept("fix-tracked-summoners", puuid);
                summoners++;
                if (summoners % 100 == 0) {
                    BotLogger.info("[FixTracked] Processed summoners=" + summoners + " updates=" + report.processed());
                    requestCollection();
                }
            }
        } catch (RuntimeException exception) {
            BotLogger.error("[FixTracked] Failed summoners=" + summoners + " error="
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            throw exception;
        } finally {
            requestCollection();
        }
        BotLogger.info("[FixTracked] Completed summoners=" + summoners + " updates=" + report.processed());
        return report;
    }

    // ============================================================================

    private static void migrateRankProgress(Options options, MigrationReport report) {
        BotLogger.info("[RankProgress] Starting run=" + options.runId() + " resume=" + options.resume()
                + " dryRun=" + options.dryRun() + " batchSize=" + options.batchSize());
        try {
            migrateRankProgressSchema(options, report);
            migrateRankProgressHistory(options, report);
            BotLogger.info("[RankProgress] Completed run=" + options.runId() + " processed=" + report.processed());
        } catch (RuntimeException exception) {
            BotLogger.error("[RankProgress] Failed run=" + options.runId() + " error="
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            throw exception;
        }
    }

    private static void migrateRankProgressSchema(Options options, MigrationReport report) {
        RankProgressCheckpoint checkpoint = options.resume()
                ? readRankProgressCheckpoint(options.runId(), RANK_PROGRESS_SCHEMA_PHASE)
                : RankProgressCheckpoint.empty();
        if ("COMPLETED".equals(checkpoint.status())) {
            BotLogger.info("[RankProgress] Schema already completed run=" + options.runId());
            return;
        }

        String cursor = checkpoint.cursor();
        long processed = checkpoint.processed();
        BotLogger.info("[RankProgress] Schema start run=" + options.runId() + " cursor=" + cursor + " processed=" + processed);
        while (true) {
            MongoDB.RankProgressPage page = MongoDB.migrateRankProgressSchemaPage(cursor, Math.min(options.batchSize(), MAX_MATCH_BATCH_SIZE), options.dryRun());
            if (page.processed() == 0) break;
            processed += page.processed();
            cursor = page.cursor();
            for (int index = 0; index < page.updated(); index++) report.accept(RANK_PROGRESS_SCHEMA_PHASE, cursor);
            if (!options.dryRun()) writeRankProgressCheckpoint(options, RANK_PROGRESS_SCHEMA_PHASE, cursor, processed, "RUNNING");
            BotLogger.info("[RankProgress] Schema page run=" + options.runId() + " processed=" + processed
                    + " updated=" + page.updated() + " cursor=" + cursor);
            requestCollection();
        }
        if (!options.dryRun()) writeRankProgressCheckpoint(options, RANK_PROGRESS_SCHEMA_PHASE, cursor, processed, "COMPLETED");
        BotLogger.info("[RankProgress] Schema completed run=" + options.runId() + " processed=" + processed);
    }

    private static void migrateRankProgressHistory(Options options, MigrationReport report) {
        RankProgressCheckpoint checkpoint = options.resume()
                ? readRankProgressCheckpoint(options.runId(), RANK_PROGRESS_HISTORY_PHASE)
                : RankProgressCheckpoint.empty();
        if ("COMPLETED".equals(checkpoint.status())) {
            BotLogger.info("[RankProgress] History already completed run=" + options.runId());
            return;
        }

        String cursor = checkpoint.cursor();
        long processed = checkpoint.processed();
        int batchSize = Math.min(options.batchSize(), 5_000);
        BotLogger.info("[RankProgress] History start run=" + options.runId()
                + " cursor=" + cursor + " processed=" + processed);
        try (com.mongodb.client.MongoCursor<Document> subjects = MongoDB.rankProgressSubjectCursor(cursor)) {
            int inBatch = 0;
            while (subjects.hasNext()) {
                MongoDB.RankProgressSubject subject = MongoDB.RankProgressSubject.from(subjects.next());
                int updated = MongoDB.rebuildRankProgressHistory(subject, options.dryRun());
                processed++;
                cursor = subject.cursor();
                for (int index = 0; index < updated; index++) report.accept(RANK_PROGRESS_HISTORY_PHASE, cursor);
                if (++inBatch < batchSize) continue;
                if (!options.dryRun()) writeRankProgressCheckpoint(options, RANK_PROGRESS_HISTORY_PHASE, cursor, processed, "RUNNING");
                BotLogger.info("[RankProgress] History page run=" + options.runId() + " processed=" + processed
                        + " cursor=" + cursor);
                inBatch = 0;
                requestCollection();
            }
        }
        if (!options.dryRun()) writeRankProgressCheckpoint(options, RANK_PROGRESS_HISTORY_PHASE, cursor, processed, "COMPLETED");
        BotLogger.info("[RankProgress] History completed run=" + options.runId() + " processed=" + processed);
    }

    private static RankProgressCheckpoint readRankProgressCheckpoint(String runId, String phase) {
        QueryRecord record = MongoDB.findRecord(CHECKPOINT_COLLECTION, rankProgressCheckpointId(runId, phase));
        if (record == null) return RankProgressCheckpoint.empty();
        return new RankProgressCheckpoint(record.get("cursor"), record.getAsLong("processed"), record.get("status"));
    }

    private static void writeRankProgressCheckpoint(Options options, String phase, String cursor, long processed, String status) {
        MongoDB.upsertDocument(CHECKPOINT_COLLECTION, new Document("_id", rankProgressCheckpointId(options.runId(), phase))
                .append("runId", options.runId()).append("version", phase).append("phase", phase).append("status", status)
                .append("cursor", cursor).append("processed", processed)
                .append("batchSize", options.batchSize()).append("updatedAt", System.currentTimeMillis()));
    }

    private static String rankProgressCheckpointId(String runId, String phase) {
        return phase + ":" + runId;
    }

    private static void requestCollection() {
        System.gc();
    }

    public record Options(boolean dryRun, int batchSize, String runId, boolean resume) {
        public Options {
            if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
            if (runId == null || runId.isBlank()) runId = "default";
        }

        public static Options defaults() {
            return new Options(false, DEFAULT_BATCH_SIZE, "default", true);
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

    private record RankProgressCheckpoint(String cursor, long processed, String status) {
        private static RankProgressCheckpoint empty() { return new RankProgressCheckpoint(null, 0, null); }
    }
}
