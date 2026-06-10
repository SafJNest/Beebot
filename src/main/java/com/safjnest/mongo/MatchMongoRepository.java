package com.safjnest.mongo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
import com.safjnest.lol.dto.MatchDTO;
import com.safjnest.mongo.codec.MatchDocumentCodec;

public final class MatchMongoRepository implements MongoDtoCache<MatchDTO> {

    private static final ReplaceOptions UPSERT_OPTIONS = new ReplaceOptions().upsert(true);
    private static final BulkWriteOptions BULK_OPTIONS = new BulkWriteOptions().ordered(false);

    private final MongoCollection<Document> collection;

    public MatchMongoRepository() {
        this(MongoConnection.getDatabase());
    }

    public MatchMongoRepository(MongoDatabase database) {
        this.collection = database.getCollection(MongoCollections.MATCHES);
    }

    @Override
    public MatchDTO find(String gameId) {
        if (gameId == null || gameId.isBlank()) return null;
        return MatchDocumentCodec.decode(collection.find(eq("_id", gameId)).first());
    }

    @Override
    public List<MatchDTO> findAll(Collection<String> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) return List.of();
        return decodeAll(collection.find(in("_id", gameIds)));
    }

    public List<MatchDTO> findByPuuid(String puuid, int limit) {
        if (puuid == null || puuid.isBlank() || limit <= 0) return List.of();

        return decodeAll(
            collection.find(eq("participants.puuid", puuid))
                .sort(Sorts.descending("timeStart"))
                .limit(limit)
        );
    }

    @Override
    public void upsert(MatchDTO match) {
        if (match == null) return;

        collection.replaceOne(
            eq("_id", match.getGameId()),
            MatchDocumentCodec.encode(match),
            UPSERT_OPTIONS
        );
    }

    @Override
    public void upsertAll(Collection<MatchDTO> matches) {
        if (matches == null || matches.isEmpty()) return;

        List<WriteModel<Document>> writes = new ArrayList<>(matches.size());
        for (MatchDTO match : matches) {
            if (match == null) continue;

            writes.add(new ReplaceOneModel<>(
                eq("_id", match.getGameId()),
                MatchDocumentCodec.encode(match),
                UPSERT_OPTIONS
            ));
        }
        if (!writes.isEmpty()) collection.bulkWrite(writes, BULK_OPTIONS);
    }

    @Override
    public boolean delete(String gameId) {
        if (gameId == null || gameId.isBlank()) return false;
        return collection.deleteOne(eq("_id", gameId)).getDeletedCount() > 0;
    }

    @Override
    public long deleteAll(Collection<String> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) return 0L;
        return collection.deleteMany(in("_id", gameIds)).getDeletedCount();
    }

    @Override
    public long clear() {
        return collection.deleteMany(new Document()).getDeletedCount();
    }

    @Override
    public void ensureIndexes() {
        collection.createIndex(
            Indexes.descending("timeStart"),
            new IndexOptions().name("time_start")
        );
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("leagueShard"),
                Indexes.ascending("queue"),
                Indexes.descending("timeStart")
            ),
            new IndexOptions().name("shard_queue_time")
        );
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("participants.puuid"),
                Indexes.descending("timeStart")
            ),
            new IndexOptions().name("participant_time")
        );
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("patch"),
                Indexes.ascending("queue")
            ),
            new IndexOptions().name("patch_queue")
        );
    }

    private List<MatchDTO> decodeAll(Iterable<Document> documents) {
        List<MatchDTO> result = new ArrayList<>();
        for (Document document : documents) {
            result.add(MatchDocumentCodec.decode(document));
        }
        return result;
    }
}
