package com.safjnest.nosql;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.log.BotLogger;

public final class MongoMigration {

    private static final String CHECKPOINT_COLLECTION = "migration_runs";
    private static final String MIGRATION_VERSION = "raw-v6-match-schema";
    private static final String RANK_PROGRESS_SCHEMA_PHASE = "rank-progress-schema-v1";
    private static final String RANK_PROGRESS_HISTORY_PHASE = "rank-progress-history-v1";
    private static final int DEFAULT_BATCH_SIZE = 500_000;
    private static final int MAX_BATCH_SIZE = 500_000;
    private static final int MAX_MATCH_BATCH_SIZE = 50_000;
    private static final int EMBEDDED_BATCH_SIZE = 10_000;
    private static final int SUMMONER_WRITE_BATCH_SIZE = 20_000;
    private static final int MATCH_READ_BATCH_SIZE = 1_000;
    private static final int GC_INTERVAL_BATCHES = 10;
    private static final int MAX_REPORT_IDENTITIES = 100;
    private static final List<String> PHASES = List.of("summoners", "matches");

    private MongoMigration() {
    }

    public static MigrationReport migrateAll() {
        return migrateAll(Options.defaults());
    }

    public static MigrationReport migrateAll(Options options) {
        Options effective = options == null ? Options.defaults() : options;
        MigrationReport report = new MigrationReport(effective.dryRun());
        try {
            for (String phase : PHASES) migratePhase(phase, effective, report);
            migrateRankProgress(effective, report);
            return report;
        } finally {
            requestCollection();
        }
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

    public static MigrationReport migrateTrackedRankProgress() {
        MigrationReport report = new MigrationReport(false);
        int summoners = 0;
        BotLogger.info("[TrackedRankProgress] Starting MariaDB recovery");
        try (com.mongodb.client.MongoCursor<Document> cursor = MongoDB.trackedSummonerCursor()) {
            while (cursor.hasNext()) {
                String puuid = cursor.next().getString("_id");
                if (puuid == null || puuid.isBlank()) continue;
                recoverTrackedRankProgress(puuid, report);
                report.accept("tracked-summoners", puuid);
                summoners++;
                if (summoners % 100 == 0) {
                    BotLogger.info("[TrackedRankProgress] Processed summoners=" + summoners
                            + " updates=" + report.processed());
                    requestCollection();
                }
            }
        } catch (RuntimeException exception) {
            BotLogger.error("[TrackedRankProgress] Failed summoners=" + summoners + " error="
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            throw exception;
        } finally {
            requestCollection();
        }
        BotLogger.info("[TrackedRankProgress] Completed summoners=" + summoners + " updates=" + report.processed());
        return report;
    }

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

    private static void recoverTrackedRankProgress(String puuid, MigrationReport report) {
        List<Match> sourceMatches = LeagueDB.getMatchesByPuuid(puuid);
        Map<String, Match> matchesByIdentity = new LinkedHashMap<>();
        Set<String> regions = new HashSet<>();
        try {
            for (Match match : sourceMatches) {
                String identity = matchIdentity(match);
                matchesByIdentity.put(identity, match);
                if (match.leagueShard != null) regions.add(match.leagueShard.name());
            }

            List<String> identities = new ArrayList<>(matchesByIdentity.keySet());
            Set<String> existingMatches = MongoDB.findExistingIds("match", identities);
            Set<String> existingEvents = MongoDB.findExistingIds("match_events", identities);
            List<Integer> missingEventIds = new ArrayList<>();
            Set<String> missingEventIdentities = new HashSet<>();
            try {
                for (Map.Entry<String, Match> entry : matchesByIdentity.entrySet()) {
                    String identity = entry.getKey();
                    Match match = entry.getValue();
                    if (!existingMatches.contains(identity) && MongoDB.createRawMatch(identity, match)) {
                        report.accept("tracked-matches", identity);
                    }
                    if (!existingEvents.contains(identity)) {
                        missingEventIds.add(match.id);
                        missingEventIdentities.add(identity);
                    }
                    Participant participant = participant(match, puuid);
                    if (participant != null && MongoDB.restoreUntrackedParticipantRankProgress(identity, puuid, participant.rankProgress)) {
                        report.accept("tracked-rank-progress", identity);
                    }
                }
                migrateMissingEvents(Options.defaults(), missingEventIds, missingEventIdentities);
            } finally {
                existingMatches.clear();
                existingEvents.clear();
                identities.clear();
                missingEventIds.clear();
                missingEventIdentities.clear();
            }

            for (String region : regions) {
                int updated = MongoDB.rebuildRankProgressHistory(new MongoDB.RankProgressSubject(region, puuid), false);
                for (int index = 0; index < updated; index++) report.accept("tracked-rank-history", region + "|" + puuid);
            }
        } finally {
            matchesByIdentity.clear();
            regions.clear();
            sourceMatches.clear();
        }
    }

    private static void migratePhase(String phase, Options options, MigrationReport report) {
        Checkpoint checkpoint = options.resume() ? readCheckpoint(options.runId(), phase) : Checkpoint.empty();
        if ("summoners".equals(phase)) {
            migrateSummoners(options, report, checkpoint);
            return;
        }
        migrateMatches(options, report, checkpoint);
    }

    private static Participant participant(Match match, String puuid) {
        if (match == null || match.participants == null) return null;
        for (Participant participant : match.participants) {
            if (participant != null && puuid.equals(participant.puuid)) return participant;
        }
        return null;
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
            MongoDB.RankProgressPage page = MongoDB.migrateRankProgressSchemaPage(cursor, pageSize("matches", options.batchSize()), options.dryRun());
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
        int batchSize = Math.min(pageSize("matches", options.batchSize()), 5_000);
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

    private static void migrateMatches(Options options, MigrationReport report, Checkpoint checkpoint) {
        long highWaterMark = checkpoint.highWaterMark();
        long processed = checkpoint.processed();
        boolean completed = true;

        while (true) {
            List<QueryRecord> keys = queryMatchKeyPage(highWaterMark, pageSize("matches", options.batchSize()));
            if (keys.isEmpty()) break;
            Map<Integer, String> identities = new LinkedHashMap<>();
            long batchHighWaterMark = highWaterMark;
            boolean stopped = false;
            try {
                for (QueryRecord row : keys) {
                    long rowHighWaterMark = row.getAsLong("id");
                    if (rowHighWaterMark <= highWaterMark) continue;
                    if (options.highWaterMark() > 0 && rowHighWaterMark > options.highWaterMark()) {
                        completed = false;
                        stopped = true;
                        break;
                    }
                    identities.put((int) rowHighWaterMark, matchIdentity(row));
                    batchHighWaterMark = rowHighWaterMark;
                }
                keys.clear();

                if (identities.isEmpty()) {
                    if (stopped) break;
                    continue;
                }

                List<String> identityValues = new ArrayList<>(identities.values());
                Set<String> existingMatches = MongoDB.findExistingIds("match", identityValues);
                Set<String> existingEvents = MongoDB.findExistingIds("match_events", identityValues);
                List<Integer> missingMatchIds = new ArrayList<>();
                List<Integer> missingEventIds = new ArrayList<>();
                Set<String> missingEventIdentities = new HashSet<>();
                for (Map.Entry<Integer, String> entry : identities.entrySet()) {
                    String identity = entry.getValue();
                    if (!existingMatches.contains(identity)) missingMatchIds.add(entry.getKey());
                    if (!existingEvents.contains(identity)) {
                        missingEventIds.add(entry.getKey());
                        missingEventIdentities.add(identity);
                    }
                }

                if (!options.dryRun()) MongoDB.normalizeMatchDocuments(identityValues);
                migrateMissingMatches(options, missingMatchIds);
                migrateMissingEvents(options, missingEventIds, missingEventIdentities);
                existingMatches.clear();
                existingEvents.clear();
                identityValues.clear();
                missingMatchIds.clear();
                missingEventIds.clear();
                missingEventIdentities.clear();
                processed += identities.size();
                highWaterMark = batchHighWaterMark;
                for (String identity : identities.values()) report.accept("matches", identity);
                if (!options.dryRun()) writeCheckpoint(options, "matches", highWaterMark, processed, stopped ? "PAUSED" : "RUNNING");
                if (stopped) break;
            } finally {
                keys.clear();
                identities.clear();
                requestCollection();
            }
        }

        if (!options.dryRun()) writeCheckpoint(options, "matches", highWaterMark, processed, completed ? "COMPLETED" : "PAUSED");
    }

    private static void migrateMissingMatches(Options options, List<Integer> missingMatchIds) {
        int batchesSinceCollection = 0;
        for (int start = 0; start < missingMatchIds.size(); start += MATCH_READ_BATCH_SIZE) {
            int end = Math.min(missingMatchIds.size(), start + MATCH_READ_BATCH_SIZE);
            List<Match> matches = LeagueDB.get().getMatchesByIds(missingMatchIds.subList(start, end));
            try {
                for (Match match : matches) {
                    String identity = matchIdentity(match);
                    try {
                        if (!options.dryRun()) {
                            MongoDB.upsertMatchDocument(identity, match);
                        }
                    } catch (RuntimeException exception) {
                        throw migrationFailure("matches", identity, exception);
                    } finally {
                        releaseMatch(match);
                    }
                }
            } finally {
                matches.clear();
            }
            if (++batchesSinceCollection >= GC_INTERVAL_BATCHES) {
                requestCollection();
                batchesSinceCollection = 0;
            }
        }
    }

    private static void migrateMissingEvents(Options options, List<Integer> missingEventIds, Set<String> missingEventIdentities) {
        int batchesSinceCollection = 0;
        for (int start = 0; start < missingEventIds.size(); start += MATCH_READ_BATCH_SIZE) {
            int end = Math.min(missingEventIds.size(), start + MATCH_READ_BATCH_SIZE);
            List<QueryRecord> rows = queryMatchEventsByIds(missingEventIds.subList(start, end));
            try {
                for (QueryRecord row : rows) {
                    String identity = matchIdentity(row);
                    if (!missingEventIdentities.contains(identity)) continue;
                    try {
                        if (!options.dryRun()) upsertMatchEvents(identity, row.get("events"));
                    } catch (RuntimeException exception) {
                        throw migrationFailure("match_events", identity, exception);
                    }
                }
            } finally {
                rows.clear();
            }
            if (++batchesSinceCollection >= GC_INTERVAL_BATCHES) {
                requestCollection();
                batchesSinceCollection = 0;
            }
        }
    }

    private static void migrateSummoners(Options options, MigrationReport report, Checkpoint checkpoint) {
        long highWaterMark = checkpoint.highWaterMark();
        long processed = checkpoint.processed();
        boolean completed = true;

        while (true) {
            List<QueryRecord> rows = querySummonerKeyPage(highWaterMark, options.batchSize());
            if (rows.isEmpty()) break;
            List<Long> sourceIds = new ArrayList<>();
            List<String> sourcePuuids = new ArrayList<>();
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
                    sourceIds.add(rowHighWaterMark);
                    sourcePuuids.add(required(row, "puuid"));
                    batchHighWaterMark = rowHighWaterMark;
                }

                rows.clear();

                if (sourceIds.isEmpty()) {
                    if (stopped) break;
                    continue;
                }

                Set<String> existing = MongoDB.findExistingIds("summoner", sourcePuuids);
                List<Long> missingIds = new ArrayList<>();
                for (int index = 0; index < sourceIds.size(); index++) {
                    if (!existing.contains(sourcePuuids.get(index))) missingIds.add(sourceIds.get(index));
                }
                existing.clear();

                migrateMissingSummoners(options, missingIds, report);
                processed += sourceIds.size();
                highWaterMark = batchHighWaterMark;
                if (!options.dryRun()) writeCheckpoint(options, "summoners", highWaterMark, processed, stopped ? "PAUSED" : "RUNNING");
                if (stopped) break;
            } finally {
                rows.clear();
                sourceIds.clear();
                sourcePuuids.clear();
                requestCollection();
            }
        }

        if (!options.dryRun()) writeCheckpoint(options, "summoners", highWaterMark, processed, completed ? "COMPLETED" : "PAUSED");
    }

    private static void migrateMissingSummoners(Options options, List<Long> missingIds, MigrationReport report) {
        int batchesSinceCollection = 0;
        for (int start = 0; start < missingIds.size(); start += SUMMONER_WRITE_BATCH_SIZE) {
            int end = Math.min(missingIds.size(), start + SUMMONER_WRITE_BATCH_SIZE);
            List<Long> batchIds = missingIds.subList(start, end);
            List<QueryRecord> rows = querySummonerRowsByIds(batchIds);
            Map<String, Document> documents = new LinkedHashMap<>();
            try {
                for (QueryRecord row : rows) {
                    String puuid = required(row, "puuid");
                    documents.put(puuid, convertSummoner(row));
                }
                loadEmbeddedRows("ranks", batchIds, documents);
                loadEmbeddedRows("masteries", batchIds, documents);
                if (!options.dryRun()) MongoDB.bulkUpsertDocuments("summoner", documents.values(), SUMMONER_WRITE_BATCH_SIZE);
                for (String puuid : documents.keySet()) report.accept("summoners", puuid);
            } finally {
                rows.clear();
                documents.clear();
            }
            if (++batchesSinceCollection >= GC_INTERVAL_BATCHES) {
                requestCollection();
                batchesSinceCollection = 0;
            }
        }
    }

    private static List<QueryRecord> querySummonerKeyPage(long highWaterMark, int pageSize) {
        String query = "SELECT id, puuid FROM summoner WHERE id > "
                + highWaterMark + " ORDER BY id ASC LIMIT " + pageSize;
        return LeagueDB.get().query(query);
    }

    private static List<QueryRecord> queryMatchKeyPage(long highWaterMark, int pageSize) {
        String query = "SELECT id, game_id, region FROM `match` WHERE id > "
                + highWaterMark + " ORDER BY id ASC LIMIT " + pageSize;
        return LeagueDB.get().query(query);
    }

    private static List<QueryRecord> queryMatchEventsByIds(List<Integer> ids) {
        String query = "SELECT id, game_id, region, events FROM `match` WHERE id IN " + sqlIds(ids) + " ORDER BY id ASC";
        return LeagueDB.get().query(query);
    }

    private static List<QueryRecord> querySummonerRowsByIds(List<Long> ids) {
        String query = "SELECT id, puuid, riot_id, region, level, icon, user_id, tracking, last_update FROM summoner WHERE id IN "
                + sqlIds(ids) + " ORDER BY id ASC";
        return LeagueDB.get().query(query);
    }

    private static List<QueryRecord> queryEmbeddedPage(String phase, List<Long> summonerIds, long afterId) {
        String query = switch (phase) {
            case "ranks" -> "SELECT r.id, r.summoner_id, s.puuid, r.region, r.queue, r.`rank`, r.lp, r.mmr, r.wins, r.losses, r.last_update "
                    + "FROM `rank` r JOIN summoner s ON s.id = r.summoner_id WHERE r.id > " + afterId
                    + " AND s.id IN " + sqlIds(summonerIds)
                    + " ORDER BY r.id ASC LIMIT " + EMBEDDED_BATCH_SIZE;
            case "masteries" -> "SELECT m.id, m.summoner_id, s.puuid, m.champion_id, m.champion_level, m.champion_points, m.last_play_time "
                    + "FROM masteries m JOIN summoner s ON s.id = m.summoner_id WHERE m.id > " + afterId
                    + " AND s.id IN " + sqlIds(summonerIds)
                    + " ORDER BY m.id ASC LIMIT " + EMBEDDED_BATCH_SIZE;
            default -> throw new IllegalArgumentException("Unknown embedded migration phase " + phase);
        };
        return LeagueDB.get().query(query);
    }

    private static String sqlIds(List<? extends Number> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Migration id batch cannot be empty");
        StringBuilder result = new StringBuilder("(");
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) result.append(",");
            result.append(ids.get(index));
        }
        return result.append(")").toString();
    }

    private static void loadEmbeddedRows(String phase, List<Long> summonerIds, Map<String, Document> summoners) {
        long afterId = 0;
        int batchesSinceCollection = 0;
        while (true) {
            List<QueryRecord> rows = queryEmbeddedPage(phase, summonerIds, afterId);
            if (rows.isEmpty()) return;
            try {
                for (QueryRecord row : rows) {
                    long rowId = row.getAsLong("id");
                    if (rowId <= afterId) throw new IllegalStateException("Embedded migration page did not advance phase=" + phase + " id=" + rowId);
                    afterId = rowId;
                    String puuid = required(row, "puuid");
                    Document summoner = summoners.get(puuid);
                    if (summoner == null) throw new IllegalStateException("Embedded row has no summoner in batch phase=" + phase + " puuid=" + puuid);
                    appendEmbedded(summoner, phase, convertEmbedded(phase, row));
                }
            } finally {
                rows.clear();
            }
            if (++batchesSinceCollection >= GC_INTERVAL_BATCHES) {
                requestCollection();
                batchesSinceCollection = 0;
            }
        }
    }

    private static void appendEmbedded(Document summoner, String phase, Document value) {
        String field = "ranks".equals(phase) ? "ranks" : "masteries";
        String identityField = "ranks".equals(phase) ? "queue" : "championId";
        @SuppressWarnings("unchecked")
        List<Document> values = (List<Document>) summoner.get(field);
        if (values == null) {
            values = new ArrayList<>();
            summoner.put(field, values);
        }
        String identity = String.valueOf(value.get(identityField));
        for (int index = 0; index < values.size(); index++) {
            if (identity.equals(String.valueOf(values.get(index).get(identityField)))) {
                values.set(index, value);
                return;
            }
        }
        values.add(value);
    }

    private static void releaseMatch(Match match) {
        match.events = null;
        match.eventData = null;
        match.participants = null;
        match.bans = null;
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
        QueryRecord record = MongoDB.findRecord(CHECKPOINT_COLLECTION, checkpointId(runId, phase));
        if (record == null) return Checkpoint.empty();
        return new Checkpoint(record.getAsLong("highWaterMark"), record.getAsLong("processed"));
    }

    private static void writeCheckpoint(Options options, String phase, long highWaterMark, long processed, String status) {
        MongoDB.upsertDocument(CHECKPOINT_COLLECTION, new Document("_id", checkpointId(options.runId(), phase))
                .append("runId", options.runId()).append("version", MIGRATION_VERSION).append("phase", phase).append("status", status)
                .append("highWaterMark", highWaterMark).append("processed", processed)
                .append("batchSize", options.batchSize()).append("updatedAt", System.currentTimeMillis()));
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

    private static String checkpointId(String runId, String phase) {
        return MIGRATION_VERSION + ":" + runId + ":" + phase;
    }

    private static String rankProgressCheckpointId(String runId, String phase) {
        return phase + ":" + runId;
    }

    private static String matchIdentity(Match match) {
        if (match.gameId == null || match.gameId.isBlank()) throw new IllegalArgumentException("Match.gameId is required");
        if (match.gameId.indexOf('_') > 0) return match.gameId;
        if (match.leagueShard == null) throw new IllegalArgumentException("Match.leagueShard is required for numeric game ID " + match.gameId);
        return match.leagueShard.name() + "_" + match.gameId;
    }

    private static String matchIdentity(QueryRecord row) {
        return matchIdentity(required(row, "region"), required(row, "game_id"));
    }

    private static String matchIdentity(String region, String gameId) {
        return gameId.indexOf('_') > 0 ? gameId : region + "_" + gameId;
    }

    private static void upsertMatchEvents(String identity, String json) {
        if (!MongoDB.upsertMatchEventsJson(identity, json)) throw new IllegalStateException("Mongo match event upsert failed id=" + identity);
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

    private static void requestCollection() {
        System.gc();
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

    private record RankProgressCheckpoint(String cursor, long processed, String status) {
        private static RankProgressCheckpoint empty() { return new RankProgressCheckpoint(null, 0, null); }
    }
}
