package com.safjnest.bot.cache.managers;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.safjnest.App;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordApi;
import com.safjnest.sound.model.Sound;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;

public class SoundCacheLoadingTest {

    @Test
    public void loadsOnlyMissingSoundsAndTheirTags() {
        RecordingDatabase database = new RecordingDatabase();
        SoundCache cache = new SoundCache(database, new DiscordApi(), Ticker.systemTicker());
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String id = String.valueOf(index);
            ids.add(id);
            if (index < 10)
                cache.put(id, new Sound(row(id), List.of()));
        }
        Sound hit = cache.get("0");
        List<Sound> result = cache.load(ids);
        assertEquals(ids.subList(10, 20), database.sounds);
        assertEquals(ids.subList(10, 20), database.tags);
        assertSame(hit, result.get(0));
        assertEquals(20, result.size());
        for (int index = 0; index < 20; index++)
            assertEquals(ids.get(index), result.get(index).getId());
        database.sounds.clear();
        database.tags.clear();
        assertSame(result.get(19), cache.load(List.of("19")).get(0));
        assertTrue(database.sounds.isEmpty());
        assertTrue(database.tags.isEmpty());
    }

    @Test
    public void preservesOneMinuteWriteExpiration() {
        RecordingDatabase database = new RecordingDatabase();
        AtomicLong ticker = new AtomicLong();
        SoundCache cache = new SoundCache(database, new DiscordApi(), ticker::get);
        Sound first = cache.load(List.of("1")).get(0);
        ticker.set(TimeUnit.SECONDS.toNanos(50));
        assertSame(first, cache.load(List.of("1")).get(0));
        assertEquals(10_000, cache.expiresAfter("1"));
        ticker.set(TimeUnit.SECONDS.toNanos(61));
        assertNotSame(first, cache.load(List.of("1")).get(0));
    }

    @Test
    public void databaseFailureDoesNotBecomeAnEmptySound() {
        RecordingDatabase database = new RecordingDatabase();
        database.failure = new Exception(Type.UNAVAILABLE, "sound_storage_unavailable", "Unavailable");
        SoundCache cache = new SoundCache(database, new DiscordApi(), Ticker.systemTicker());
        assertSame(database.failure, assertThrows(Exception.class, () -> cache.load(List.of("1"))));
        assertEquals(0, cache.getSize());
        database.failure = null;
        assertEquals("1", cache.load(List.of("1")).get(0).getId());
    }

    @Test
    public void emptyRequestDoesNotQueryStorage() {
        RecordingDatabase database = new RecordingDatabase();
        SoundCache cache = new SoundCache(database, new DiscordApi(), Ticker.systemTicker());
        assertTrue(cache.load(List.of()).isEmpty());
        assertTrue(database.sounds.isEmpty());
        assertTrue(database.tags.isEmpty());
    }

    @Test
    public void responseUsesFreshMetadataAndKeepsViewerStateOutOfCachedSound() {
        RecordingDatabase database = new RecordingDatabase();
        DiscordApi access = new DiscordApi() {
            @Override
            public Map<String, Object> user(String id) {
                return Map.of("id", id, "name", "Uploader");
            }
        };
        SoundCache cache = new SoundCache(database, access, Ticker.systemTicker());
        cache.setCdnUrl("https://cdn.example.test");
        QueryRecord oldRow = row("1");
        oldRow.put("name", "old name");
        Sound cached = new Sound(oldRow, List.of());
        cache.put("1", cached);
        Sound ownerResponse = cache.sound(new SoundCache.Context("user", Map.of()), "1");
        Sound otherResponse = cache.sound(new SoundCache.Context("other", Map.of()), "1");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode owner = mapper.valueToTree(ownerResponse);
        JsonNode other = mapper.valueToTree(otherResponse);
        JsonNode shared = mapper.valueToTree(cached);
        assertEquals("sound 1", ownerResponse.getName());
        assertEquals("old name", cached.getName());
        assertTrue(owner.get("ownedByMe").asBoolean());
        assertFalse(other.get("ownedByMe").asBoolean());
        assertFalse(shared.get("ownedByMe").asBoolean());
        assertEquals("https://cdn.example.test/sounds/1.mp3", owner.get("url").asText());
        assertTrue(shared.get("url").isNull());
        assertEquals(List.of("1", "1"), database.sounds);
        assertSame(cached, cache.get("1"));
    }

    @Test
    public void searchReusesExistingRedisKeysAndReplacesMalformedSelections() {
        RecordingDatabase database = new RecordingDatabase();
        DiscordApi access = new DiscordApi() {
            @Override
            public Map<String, Object> user(String id) {
                return Map.of("id", id);
            }
        };
        Map<String, String> redis = new HashMap<>();
        Map<String, Integer> expiration = new HashMap<>();
        SoundCache.SelectionStore selections = new SoundCache.SelectionStore() {
            @Override
            public String get(String key) {
                return redis.get(key);
            }

            @Override
            public void put(String key, String value, int ttl) {
                redis.put(key, value);
                expiration.put(key, ttl);
            }
        };
        SoundCache cache = new SoundCache(database, access, selections, Ticker.systemTicker(), new UserCache(database));
        SoundCache.Context context = new SoundCache.Context("user", Map.of(
            "2", new DiscordApi.GuildAccess("Two", false), "10", new DiscordApi.GuildAccess("Ten", false)));

        String prefix = (App.isTesting() ? "beebot_test" : "beebot") + ":sound:query:";
        String key = prefix + "v1:original:e9289654e2111cf45d001590a9c18029eab5d2e12f1d3938e240feb4d80ac73e";
        redis.put(prefix + "generation", "original");
        redis.put(key, "[\"2\",\"1\"]");

        SoundCache.SoundSearch hit = cache.search(context, " Sample ", "", "plays", "desc", 3);
        assertEquals("2", hit.sounds().get(0).getId());
        assertEquals("1", hit.sounds().get(1).getId());
        assertEquals(0, database.selections);
        assertTrue(expiration.isEmpty());

        redis.put(key, "{\"invalid\":true}");
        SoundCache.SoundSearch fallback = cache.search(context, "sample", "", "plays", "desc", 3);
        assertEquals("1", fallback.sounds().get(0).getId());
        assertEquals(1, database.selections);
        assertEquals("[\"1\"]", redis.get(key));
        assertEquals(Integer.valueOf(60), expiration.get(key));

        redis.put(key, "[]");
        assertTrue(cache.search(context, "sample", "", "plays", "desc", 3).sounds().isEmpty());
        assertEquals(1, database.selections);
    }

    /* -------------------------------------------------------------------------- */

    private static QueryRecord row(String id) {
        QueryRecord row = new QueryRecord();
        row.put("id", id);
        row.put("name", "sound " + id);
        row.put("guild_id", "guild");
        row.put("user_id", "user");
        row.put("extension", "mp3");
        row.put("public", true);
        return row;
    }

    private static final class RecordingDatabase extends BotDB {
        private final List<String> sounds = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();
        private Exception failure;
        private int selections;

        private RecordingDatabase() {
            super(() -> { throw new AssertionError("Unexpected external database access"); });
        }

        @Override
        public List<String> selectSounds(SoundSelection selection) {
            selections++;

            return List.of("1");
        }

        @Override
        public List<QueryRecord> readSounds(Collection<String> ids) {
            if (failure != null)
                throw failure;
            sounds.addAll(ids);
            List<QueryRecord> result = new ArrayList<>();
            for (String id : ids)
                result.add(row(id));
            return result;
        }

        @Override
        public List<QueryRecord> readSoundTags(Collection<String> ids) {
            tags.addAll(ids);
            return List.of();
        }

        @Override
        public List<QueryRecord> readSoundEnrichment(Collection<String> ids, String userId) {
            return List.of();
        }

        @Override
        public <T> T withSoundReadSession(Supplier<T> operation) {
            return operation.get();
        }
    }
}
