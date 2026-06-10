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
import com.mongodb.client.model.WriteModel;
import com.safjnest.lol.dto.SummonerDTO;
import com.safjnest.mongo.codec.SummonerDocumentCodec;

public final class SummonerMongoRepository implements MongoDtoCache<SummonerDTO> {

    private static final ReplaceOptions UPSERT_OPTIONS = new ReplaceOptions().upsert(true);
    private static final BulkWriteOptions BULK_OPTIONS = new BulkWriteOptions().ordered(false);

    private final MongoCollection<Document> collection;

    public SummonerMongoRepository() {
        this(MongoConnection.getDatabase());
    }

    public SummonerMongoRepository(MongoDatabase database) {
        this.collection = database.getCollection(MongoCollections.SUMMONERS);
    }

    @Override
    public SummonerDTO find(String puuid) {
        if (puuid == null || puuid.isBlank()) return null;
        return SummonerDocumentCodec.decode(collection.find(eq("_id", puuid)).first());
    }

    @Override
    public List<SummonerDTO> findAll(Collection<String> puuids) {
        if (puuids == null || puuids.isEmpty()) return List.of();

        List<SummonerDTO> result = new ArrayList<>();
        for (Document document : collection.find(in("_id", puuids))) {
            result.add(SummonerDocumentCodec.decode(document));
        }
        return result;
    }

    public SummonerDTO findByRiotId(String gameName, String tagLine, String region) {
        if (gameName == null || tagLine == null) return null;

        Document filter = new Document("gameName", gameName)
            .append("tagLine", tagLine);
        if (region != null && !region.isBlank()) filter.append("region", region);

        return SummonerDocumentCodec.decode(collection.find(filter).first());
    }

    public List<SummonerDTO> findTracked() {
        return decodeAll(collection.find(eq("tracking", true)));
    }

    public List<SummonerDTO> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return decodeAll(collection.find(eq("userId", userId)));
    }

    @Override
    public void upsert(SummonerDTO summoner) {
        if (summoner == null) return;

        collection.replaceOne(
            eq("_id", summoner.getPuuid()),
            SummonerDocumentCodec.encode(summoner),
            UPSERT_OPTIONS
        );
    }

    @Override
    public void upsertAll(Collection<SummonerDTO> summoners) {
        if (summoners == null || summoners.isEmpty()) return;

        List<WriteModel<Document>> writes = new ArrayList<>(summoners.size());
        for (SummonerDTO summoner : summoners) {
            if (summoner == null) continue;

            writes.add(new ReplaceOneModel<>(
                eq("_id", summoner.getPuuid()),
                SummonerDocumentCodec.encode(summoner),
                UPSERT_OPTIONS
            ));
        }
        if (!writes.isEmpty()) collection.bulkWrite(writes, BULK_OPTIONS);
    }

    @Override
    public boolean delete(String puuid) {
        if (puuid == null || puuid.isBlank()) return false;
        return collection.deleteOne(eq("_id", puuid)).getDeletedCount() > 0;
    }

    @Override
    public long deleteAll(Collection<String> puuids) {
        if (puuids == null || puuids.isEmpty()) return 0L;
        return collection.deleteMany(in("_id", puuids)).getDeletedCount();
    }

    @Override
    public long clear() {
        return collection.deleteMany(new Document()).getDeletedCount();
    }

    @Override
    public void ensureIndexes() {
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("gameName"),
                Indexes.ascending("tagLine"),
                Indexes.ascending("region")
            ),
            new IndexOptions().name("riot_id_region")
        );
        collection.createIndex(
            Indexes.ascending("userId"),
            new IndexOptions().name("user_id").sparse(true)
        );
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("tracking"),
                Indexes.ascending("region")
            ),
            new IndexOptions().name("tracking_region")
        );
        collection.createIndex(
            Indexes.descending("updatedAt"),
            new IndexOptions().name("updated_at")
        );
    }

    private List<SummonerDTO> decodeAll(Iterable<Document> documents) {
        List<SummonerDTO> result = new ArrayList<>();
        for (Document document : documents) {
            result.add(SummonerDocumentCodec.decode(document));
        }
        return result;
    }
}
