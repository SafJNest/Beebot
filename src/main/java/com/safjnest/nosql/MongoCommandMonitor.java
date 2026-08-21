package com.safjnest.nosql;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import com.safjnest.lol.model.status.MongoCollectionPerformance;
import com.safjnest.lol.model.status.MongoHottestCollection;
import com.safjnest.lol.model.status.MongoPerformanceMetrics;
import com.safjnest.lol.model.status.MongoSlowOperation;

public final class MongoCommandMonitor implements CommandListener {

    public static final int RECENT_WINDOW_SECONDS = 10;
    public static final int SLOW_WINDOW_SECONDS = 300;
    static final int MAX_SLOWEST = 10;

    private static final Set<String> IGNORED_COMMANDS = Set.of(
            "ismaster", "hello", "endsessions", "buildinfo",
            "saslstart", "saslcontinue", "ping", "getnonce",
            "authenticate", "logout", "replsetgetstatus", "serverstatus");

    private static final MongoCommandMonitor INSTANCE = new MongoCommandMonitor();

    private final ConcurrentHashMap<Integer, PendingCommand> pending = new ConcurrentHashMap<>();
    private final Object bucketLock = new Object();
    private final Map<String, Long> currentBucket = new HashMap<>();
    private final Map<String, Long>[] recentBuckets = createRecentBuckets();
    private int recentBucketIndex;
    private MongoHottestCollection hottestNow;
    private MongoHottestCollection hottestRecent;

    private final Object slowLock = new Object();
    private final List<RecordedOperation> recentOperations = new ArrayList<>();

    private final AtomicLong clientInsert = new AtomicLong();
    private final AtomicLong clientQuery = new AtomicLong();
    private final AtomicLong clientUpdate = new AtomicLong();
    private final AtomicLong clientDelete = new AtomicLong();
    private final AtomicLong clientCommand = new AtomicLong();
    private final AtomicLong clientGetmore = new AtomicLong();

    private MongoCommandMonitor() {}

    @SuppressWarnings("unchecked")
    private static Map<String, Long>[] createRecentBuckets() {
        Map<String, Long>[] buckets = (Map<String, Long>[]) new Map[RECENT_WINDOW_SECONDS];
        for (int index = 0; index < buckets.length; index++) {
            buckets[index] = new HashMap<>();
        }
        return buckets;
    }

    public static CommandListener listener() {
        return INSTANCE;
    }

    public static ClientOpcounters clientOpcounters() {
        return INSTANCE.readClientOpcounters();
    }

    public static MongoPerformanceMetrics snapshotPerformance() {
        return INSTANCE.buildPerformanceSnapshot();
    }

    public static void tickSecond() {
        INSTANCE.closeBucket();
    }

    static void resetForTest() {
        INSTANCE.pending.clear();
        synchronized (INSTANCE.bucketLock) {
            INSTANCE.currentBucket.clear();
            for (int index = 0; index < INSTANCE.recentBuckets.length; index++) {
                INSTANCE.recentBuckets[index] = new HashMap<>();
            }
            INSTANCE.recentBucketIndex = 0;
            INSTANCE.hottestNow = null;
            INSTANCE.hottestRecent = null;
        }
        synchronized (INSTANCE.slowLock) {
            INSTANCE.recentOperations.clear();
        }
        INSTANCE.clientInsert.set(0);
        INSTANCE.clientQuery.set(0);
        INSTANCE.clientUpdate.set(0);
        INSTANCE.clientDelete.set(0);
        INSTANCE.clientCommand.set(0);
        INSTANCE.clientGetmore.set(0);
    }

    static void recordOperationForTest(String commandName, String collection, long durationMs) {
        recordOperationForTest(commandName, collection, durationMs, Map.of(commandName, collection));
    }

    static void recordOperationForTest(String commandName, String collection, long durationMs, Map<String, Object> query) {
        long at = System.currentTimeMillis();
        INSTANCE.recordClientOp(commandName);
        INSTANCE.recordBucket(collection);
        INSTANCE.recordOperation(
                new PendingCommand(commandName, collection, query, at - durationMs),
                durationMs,
                at);
    }

    static void ageOperationForTest(int index, long ageMs) {
        synchronized (INSTANCE.slowLock) {
            if (index < 0 || index >= INSTANCE.recentOperations.size()) return;
            RecordedOperation current = INSTANCE.recentOperations.get(index);
            INSTANCE.recentOperations.set(index, new RecordedOperation(
                    current.commandName,
                    current.collection,
                    current.query,
                    current.durationMs,
                    System.currentTimeMillis() - ageMs));
        }
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        String commandName = event.getCommandName();
        if (ignored(commandName)) return;
        String collection = extractCollection(commandName, event.getCommand());
        pending.put(event.getRequestId(), new PendingCommand(
                commandName,
                collection,
                queryPayload(event.getCommand()),
                System.currentTimeMillis()));
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        onFinished(event.getRequestId(), event.getElapsedTime(TimeUnit.MILLISECONDS));
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        onFinished(event.getRequestId(), event.getElapsedTime(TimeUnit.MILLISECONDS));
    }

    // ============================================================================

    private void onFinished(int requestId, long durationMs) {
        PendingCommand command = pending.remove(requestId);
        if (command == null) return;
        recordClientOp(command.commandName);
        recordBucket(command.collection);
        recordOperation(command, durationMs, System.currentTimeMillis());
    }

    private void recordClientOp(String commandName) {
        switch (atlasCategory(commandName)) {
            case "insert" -> clientInsert.incrementAndGet();
            case "query" -> clientQuery.incrementAndGet();
            case "update" -> clientUpdate.incrementAndGet();
            case "delete" -> clientDelete.incrementAndGet();
            case "getmore" -> clientGetmore.incrementAndGet();
            default -> clientCommand.incrementAndGet();
        }
    }

    private void recordBucket(String collection) {
        if (collection == null || collection.isBlank()) return;
        synchronized (bucketLock) {
            currentBucket.merge(collection, 1L, Long::sum);
        }
    }

    private void closeBucket() {
        synchronized (bucketLock) {
            Map<String, Long> closed = Map.copyOf(currentBucket);
            currentBucket.clear();
            recentBuckets[recentBucketIndex] = new HashMap<>(closed);
            recentBucketIndex = (recentBucketIndex + 1) % RECENT_WINDOW_SECONDS;
            hottestNow = hottestFromBucket(closed);
            hottestRecent = hottestFromRecentBuckets();
        }
        pruneOperationHistory(System.currentTimeMillis());
    }

    private void recordOperation(PendingCommand command, long durationMs, long at) {
        synchronized (slowLock) {
            recentOperations.add(new RecordedOperation(
                    command.commandName,
                    command.collection,
                    command.query,
                    durationMs,
                    at));
        }
    }

    private void pruneOperationHistory(long now) {
        synchronized (slowLock) {
            long retentionCutoff = now - SLOW_WINDOW_SECONDS * 1000L;
            recentOperations.removeIf(operation -> operation.at < retentionCutoff);
        }
    }

    private static MongoHottestCollection hottestFromBucket(Map<String, Long> bucket) {
        String name = null;
        long ops = 0;
        for (Map.Entry<String, Long> entry : bucket.entrySet()) {
            if (entry.getValue() > ops) {
                ops = entry.getValue();
                name = entry.getKey();
            }
        }
        if (name == null || ops <= 0) return null;
        return new MongoHottestCollection(name, ops, ops);
    }

    private MongoHottestCollection hottestFromRecentBuckets() {
        Map<String, Long> totals = new HashMap<>();
        synchronized (bucketLock) {
            for (Map<String, Long> bucket : recentBuckets) {
                for (Map.Entry<String, Long> entry : bucket.entrySet()) {
                    totals.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
        }
        String name = null;
        long ops = 0;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            if (entry.getValue() > ops) {
                ops = entry.getValue();
                name = entry.getKey();
            }
        }
        if (name == null || ops <= 0) return null;
        return new MongoHottestCollection(name, ops / (double) RECENT_WINDOW_SECONDS, ops);
    }

    private MongoPerformanceMetrics buildPerformanceSnapshot() {
        long now = System.currentTimeMillis();
        pruneOperationHistory(now);
        long slowCutoff = now - RECENT_WINDOW_SECONDS * 1000L;
        long statsCutoff = now - SLOW_WINDOW_SECONDS * 1000L;
        List<RecordedOperation> slowWindow = new ArrayList<>();
        List<RecordedOperation> statsWindow = new ArrayList<>();
        synchronized (slowLock) {
            for (RecordedOperation operation : recentOperations) {
                if (operation.at >= statsCutoff) statsWindow.add(operation);
                if (operation.at >= slowCutoff) slowWindow.add(operation);
            }
        }
        return new MongoPerformanceMetrics(
                readHottestNow(),
                readHottestRecent(),
                RECENT_WINDOW_SECONDS,
                SLOW_WINDOW_SECONDS,
                avgMsByCommand(statsWindow),
                collections(statsWindow),
                slowest(slowWindow));
    }

    private MongoHottestCollection readHottestNow() {
        synchronized (bucketLock) {
            return hottestNow;
        }
    }

    private MongoHottestCollection readHottestRecent() {
        synchronized (bucketLock) {
            return hottestRecent;
        }
    }

    private static Map<String, Double> avgMsByCommand(List<RecordedOperation> window) {
        Map<String, CommandStats> stats = new HashMap<>();
        for (RecordedOperation operation : window) {
            CommandStats commandStats = stats.computeIfAbsent(operation.commandName, ignored -> new CommandStats());
            commandStats.totalMs += operation.durationMs;
            commandStats.count++;
        }
        Map<String, Double> avgMsByCommand = new LinkedHashMap<>();
        for (Map.Entry<String, CommandStats> entry : stats.entrySet()) {
            if (entry.getValue().count <= 0) continue;
            avgMsByCommand.put(entry.getKey(), roundMs(entry.getValue().totalMs / (double) entry.getValue().count));
        }
        return Map.copyOf(avgMsByCommand);
    }

    private static List<MongoCollectionPerformance> collections(List<RecordedOperation> window) {
        Map<String, CollectionStats> stats = new HashMap<>();
        for (RecordedOperation operation : window) {
            if (operation.collection == null || operation.collection.isBlank()) continue;
            CollectionStats collectionStats = stats.computeIfAbsent(operation.collection, ignored -> new CollectionStats());
            collectionStats.count++;
            collectionStats.totalMs += operation.durationMs;
            if (operation.durationMs > collectionStats.maxMs) collectionStats.maxMs = operation.durationMs;
        }
        List<MongoCollectionPerformance> collections = new ArrayList<>();
        for (Map.Entry<String, CollectionStats> entry : stats.entrySet()) {
            CollectionStats collectionStats = entry.getValue();
            if (collectionStats.count <= 0) continue;
            collections.add(new MongoCollectionPerformance(
                    entry.getKey(),
                    collectionStats.count,
                    roundMs(collectionStats.totalMs / (double) collectionStats.count),
                    collectionStats.maxMs));
        }
        collections.sort(Comparator.comparingLong(MongoCollectionPerformance::count).reversed());
        return List.copyOf(collections);
    }

    private static List<MongoSlowOperation> slowest(List<RecordedOperation> window) {
        List<MongoSlowOperation> slowest = new ArrayList<>();
        for (RecordedOperation operation : window) {
            slowest.add(new MongoSlowOperation(
                    operation.commandName,
                    operation.collection,
                    operation.durationMs,
                    operation.at,
                    operation.query));
        }
        slowest.sort(Comparator.comparingLong(MongoSlowOperation::durationMs).reversed());
        if (slowest.size() > MAX_SLOWEST) slowest = new ArrayList<>(slowest.subList(0, MAX_SLOWEST));
        return List.copyOf(slowest);
    }

    private ClientOpcounters readClientOpcounters() {
        return new ClientOpcounters(
                clientInsert.get(),
                clientQuery.get(),
                clientUpdate.get(),
                clientDelete.get(),
                clientCommand.get(),
                clientGetmore.get());
    }

    private static double roundMs(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    static String atlasCategory(String commandName) {
        if (commandName == null) return "command";
        return switch (commandName.toLowerCase()) {
            case "insert", "insertmany" -> "insert";
            case "find", "count", "distinct", "countdocuments" -> "query";
            case "update", "updatemany", "findandmodify", "replaceone" -> "update";
            case "delete", "deletemany", "remove" -> "delete";
            case "getmore" -> "getmore";
            default -> "command";
        };
    }

    static boolean ignored(String commandName) {
        if (commandName == null) return true;
        return IGNORED_COMMANDS.contains(commandName.toLowerCase());
    }

    static String extractCollection(String commandName, BsonDocument command) {
        if (command == null || commandName == null) return null;
        String lower = commandName.toLowerCase();
        if ("getmore".equals(lower)) {
            BsonValue cursor = command.get("collection");
            return cursor == null || !cursor.isString() ? null : stripNamespace(cursor.asString().getValue());
        }
        BsonValue value = command.get(lower);
        if (value == null) value = command.get(commandName);
        if (value == null || !value.isString()) return null;
        return stripNamespace(value.asString().getValue());
    }

    private static String stripNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) return null;
        int dot = namespace.lastIndexOf('.');
        return dot >= 0 ? namespace.substring(dot + 1) : namespace;
    }

    private static Map<String, Object> queryPayload(BsonDocument command) {
        if (command == null || command.isEmpty()) return Map.of();
        Document document = Document.parse(command.toJson());
        document.remove("$db");
        document.remove("lsid");
        document.remove("$clusterTime");
        document.remove("$readPreference");
        document.remove("$audit");
        return document.isEmpty() ? Map.of() : Map.copyOf(document);
    }

    private record PendingCommand(String commandName, String collection, Map<String, Object> query, long startedAt) {
    }

    private record RecordedOperation(
            String commandName,
            String collection,
            Map<String, Object> query,
            long durationMs,
            long at) {
    }

    private static final class CommandStats {
        long count;
        long totalMs;
    }

    private static final class CollectionStats {
        long count;
        long totalMs;
        long maxMs;
    }

    public record ClientOpcounters(
            long insert,
            long query,
            long update,
            long delete,
            long command,
            long getmore
    ) {
    }
}
