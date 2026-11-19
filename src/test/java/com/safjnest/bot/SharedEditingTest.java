package com.safjnest.bot;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.cache.managers.UserCache;
import com.safjnest.bot.model.UserData;
import com.safjnest.bot.model.guild.MemberData;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.SoundDB;
import com.safjnest.database.sql.database.SoundDB.StorageKeys;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.auth.DiscordAuthService.SessionPolicy;
import com.safjnest.discord.auth.DiscordApi;
import com.safjnest.sound.SoundService;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.SoundCatalog;
import com.safjnest.sound.model.SoundComment;
import com.safjnest.sound.model.SoundVote;
import com.safjnest.sound.model.Tag;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.spring.controller.beebot.MeController;
import com.safjnest.spring.controller.beebot.SoundCommunityController;
import com.safjnest.spring.controller.beebot.SoundController;
import com.safjnest.spring.controller.beebot.SoundboardController;
import com.safjnest.spring.exception.ApiExceptionHandler;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class SharedEditingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AuthContext AUTH = new AuthContext(null, "hash", "encrypted", Long.MAX_VALUE,
        new AuthUser("2", "owner", null, null));

    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicInteger generations = new AtomicInteger();
    private final AtomicReference<String> failedSql = new AtomicReference<>();

    private String url;
    private BotDB database;
    private SoundCache sounds;
    private UserCache users;
    private GuildCache guilds;
    private SoundController soundController;
    private MeController meController;

    @Before
    public void setup() throws java.lang.Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1";

        try (Connection connection = connection()) {
            for (String sql : List.of(
                "CREATE TABLE guild (guild_id VARCHAR(19) PRIMARY KEY, prefix VARCHAR(32) DEFAULT '!', name_tts VARCHAR(19), language_tts VARCHAR(19), exp_enabled BOOLEAN DEFAULT TRUE, threshold INT DEFAULT 0, blacklist_channel VARCHAR(19), blacklist_enabled BOOLEAN DEFAULT FALSE, league_shard INT DEFAULT 3)",
                "CREATE TABLE channel (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19), channel_id VARCHAR(19), exp_modifier DOUBLE DEFAULT 1, exp_enabled BOOLEAN DEFAULT TRUE, stats_enabled BOOLEAN DEFAULT TRUE, league_shard INT DEFAULT 0)",
                "CREATE TABLE automated_action (id INT, guild_id VARCHAR(19), action INT, action_role VARCHAR(19), action_time BIGINT, infractions INT, infractions_time BIGINT)",
                "CREATE TABLE member (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19) REFERENCES guild(guild_id), user_id VARCHAR(19), experience INT DEFAULT 0, level INT DEFAULT 1, messages INT DEFAULT 0, update_time INT DEFAULT 60, UNIQUE(guild_id, user_id))",
                "CREATE TABLE alias (id INT AUTO_INCREMENT PRIMARY KEY, user_id VARCHAR(19), name VARCHAR(100), command VARCHAR(500))",
                "CREATE TABLE sound (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19), user_id VARCHAR(19), name VARCHAR(255), description VARCHAR(500), extension VARCHAR(10), public BOOLEAN, time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, duration_ms BIGINT, plays INT DEFAULT 0, likes INT DEFAULT 0, dislikes INT DEFAULT 0, url VARCHAR(255), thumbnail VARCHAR(255), comments INT DEFAULT 0)",
                "CREATE TABLE tag (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))",
                "CREATE TABLE tag_sounds (sound_id INT REFERENCES sound(id) ON DELETE CASCADE, tag_id INT REFERENCES tag(id))",
                "CREATE TABLE greeting (user_id VARCHAR(19), guild_id VARCHAR(19), sound_id INT REFERENCES sound(id) ON DELETE CASCADE, PRIMARY KEY(user_id, guild_id))",
                "CREATE TABLE sound_comment (id BIGINT AUTO_INCREMENT PRIMARY KEY, sound_id INT REFERENCES sound(id) ON DELETE CASCADE, parent_id BIGINT, user_id VARCHAR(19), body VARCHAR(2000), created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP, edited_at TIMESTAMP(6), deleted_at TIMESTAMP(6), pinned BOOLEAN DEFAULT FALSE, hearted BOOLEAN DEFAULT FALSE, likes INT DEFAULT 0, dislikes INT DEFAULT 0, replies INT DEFAULT 0)",
                "CREATE TABLE sound_vote (id BIGINT AUTO_INCREMENT PRIMARY KEY, sound_id INT REFERENCES sound(id) ON DELETE CASCADE, comment_id BIGINT REFERENCES sound_comment(id) ON DELETE CASCADE, user_id VARCHAR(19), value INT, UNIQUE(sound_id,user_id), UNIQUE(comment_id,user_id), CHECK ((sound_id IS NULL) <> (comment_id IS NULL)), CHECK (value IN (-1, 1)))",
                "CREATE TABLE sound_play (id INT AUTO_INCREMENT PRIMARY KEY, sound_id INT REFERENCES sound(id) ON DELETE CASCADE, user_id VARCHAR(19), guild_id VARCHAR(19), channel_id VARCHAR(19), playback_id VARCHAR(36), time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, source INT, UNIQUE(sound_id, playback_id))",
                "CREATE TABLE soundboard (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19), user_id VARCHAR(19), name VARCHAR(255), thumbnail VARCHAR(128))",
                "CREATE TABLE soundboard_sounds (id INT REFERENCES soundboard(id) ON DELETE CASCADE, sound_id INT REFERENCES sound(id) ON DELETE CASCADE, PRIMARY KEY (id, sound_id))",
                "INSERT INTO guild(guild_id) VALUES ('1'), ('3')",
                "INSERT INTO channel(guild_id, channel_id) VALUES ('1', '100'), ('3', '100')",
                "INSERT INTO sound(id, guild_id, user_id, name, extension, public) VALUES (10, '1', '2', 'first', 'mp3', TRUE), (11, '1', '2', 'second', 'opus', FALSE), (12, '3', '9', 'other', 'mp3', FALSE)"
            ))
                connection.createStatement().execute(sql);
        }

        database = new BotDB(() -> {
            connections.incrementAndGet();
            Connection connection = connection();

            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") && failedSql.get() != null
                            && args[0].toString().contains(failedSql.get()))
                        throw new SQLException("Injected storage failure");

                    try {
                        return method.invoke(connection, args);
                    } catch (java.lang.reflect.InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        });

        DiscordApi access = new DiscordApi() {
            @Override
            public Map<String, GuildAccess> guildAccess(String userId) {
                return Map.of("1", new GuildAccess("Guild", false), "3", new GuildAccess("Other", false));
            }

            @Override
            public Map<String, Object> user(String id) {
                return Map.of("id", id, "name", "Uploader");
            }
        };

        SoundCache.SelectionStore selections = new SoundCache.SelectionStore() {
            public String get(String key) {
                return null;
            }

            public void put(String key, String value, int ttl) {
                generations.incrementAndGet();
            }
        };

        users = new UserCache(database);
        sounds = new SoundCache(database, access, selections, com.github.benmanes.caffeine.cache.Ticker.systemTicker(), users);
        guilds = new GuildCache(database, () -> "!");
        soundController = new SoundController(sounds);
        meController = new MeController(null, sounds, users, database);
    }

    @Test
    public void soundUploadRequiresDatabaseInsertAndRollsBackWhenFileStorageFails() {
        AtomicInteger uploads = new AtomicInteger();
        failedSql.set("INSERT INTO sound");

        assertThrows(Exception.class, () -> database.insertSound("new sound", null, "1", "2", "mp3", true,
            2088, List.of("arcade"), sound -> {
                uploads.incrementAndGet();

                return null;
            }));
        assertEquals(0, uploads.get());

        failedSql.set(null);
        IllegalStateException uploadFailure = new IllegalStateException("R2 unavailable");
        assertSame(uploadFailure, assertThrows(IllegalStateException.class,
            () -> database.insertSound("new sound", null, "1", "2", "mp3", true, 2088, List.of("arcade"), sound -> {
                uploads.incrementAndGet();
                assertEquals(0, row("SELECT COUNT(*) AS n FROM sound WHERE name = 'new sound'").getAsInt("n"));
                throw uploadFailure;
            })));
        assertEquals(1, uploads.get());
        assertEquals(0, row("SELECT COUNT(*) AS n FROM sound WHERE name = 'new sound'").getAsInt("n"));
        assertEquals(0, row("SELECT COUNT(*) AS n FROM tag WHERE name = 'arcade'").getAsInt("n"));

        String id = database.insertSound("new sound", "First description", "1", "2", "mp3", true, 2088, List.of("arcade"), sound -> {
            uploads.incrementAndGet();
            assertEquals(Long.valueOf(2088), sound.getDurationMs());
            assertEquals("First description", sound.getDescription());
            assertEquals("arcade", sound.getTags().getFirst().getName());
            assertTrue(sound.getTags().getFirst().isStored());

            return "sounds/" + sound.getId() + "/" + sound.getId() + ".mp3";
        });
        assertEquals(2, uploads.get());
        assertEquals(id, row("SELECT id FROM sound WHERE name = 'new sound'").get("id"));
        assertEquals("sounds/" + id + "/" + id + ".mp3", row("SELECT url FROM sound WHERE id=" + id).get("url"));
        assertEquals(2088, row("SELECT duration_ms FROM sound WHERE name = 'new sound'").getAsLong("duration_ms"));
        assertTrue(JSON.valueToTree(soundController.sound(AUTH, "10")).get("durationMs").isNull());
        assertEquals(2088, JSON.valueToTree(soundController.sound(AUTH, id)).get("durationMs").asLong());
        assertEquals("First description", community().get(id, null).description());

        Exception duplicate = assertThrows(Exception.class, () -> database.insertSound("new sound", null, "1", "2", "mp3", true,
            2088, List.of("arcade"), sound -> {
                fail("Duplicate sound was uploaded");

                return null;
            }));
        assertEquals("duplicate_sound_name", duplicate.getCode());
    }

    @Test
    public void storageMigrationUsesFirstBoardCoverAndPersistsOnlySuccessfulTransfers() {
        execute("INSERT INTO soundboard(id, guild_id, user_id, name, thumbnail) VALUES "
            + "(1,'1','2','no cover',NULL),(2,'1','2','first cover','first.png'),(3,'1','2','second cover','second.png')");
        execute("INSERT INTO soundboard_sounds(id,sound_id) VALUES (1,10),(2,10),(3,10)");
        SoundDB storage = database.communitySounds();

        List<QueryRecord> plan = storage.storagePage("0", 20);
        assertEquals("first.png", plan.getFirst().get("board_thumbnail"));
        assertNull(plan.get(1).get("board_thumbnail"));
        assertThrows(IllegalStateException.class, () -> storage.migrateStorage("10", current -> {
            throw new IllegalStateException("R2 copy failed");
        }));
        assertNull(storage.storageRecord("10").get("url"));

        assertTrue(storage.migrateStorage("10", current -> {
            assertEquals("first.png", current.get("board_thumbnail"));

            return new StorageKeys("sounds/10/10.mp3", "sounds/10/thumbnail-first.png");
        }));
        assertEquals("sounds/10/10.mp3", storage.storageRecord("10").get("url"));
        assertEquals("sounds/10/thumbnail-first.png", storage.storageRecord("10").get("thumbnail"));
        assertFalse(storage.migrateStorage("10", current -> new StorageKeys(
            current.get("url"), current.get("thumbnail"))));
    }

    @Test
    public void failedMetadataCopyRollsBackEdits() {
        Sound edit = sound().deferEdit();
        edit.setName("new name");
        edit.setDescription("new description");
        edit.setTagNames(List.of("new tag"));

        IllegalStateException copyFailure = new IllegalStateException("R2 unavailable");
        assertSame(copyFailure, assertThrows(IllegalStateException.class, () -> database.saveSound(edit, "2", snapshot -> {
            assertEquals("new name", snapshot.getName());
            assertEquals("new description", snapshot.getDescription());
            assertEquals("new tag", snapshot.getTags().getFirst().getName());
            throw copyFailure;
        })));
        assertEquals("first", row("SELECT name FROM sound WHERE id = 10").get("name"));
        assertNull(row("SELECT description FROM sound WHERE id = 10").get("description"));
        assertEquals(0, row("SELECT COUNT(*) AS n FROM tag WHERE name = 'new tag'").getAsInt("n"));
    }

    @Test
    public void soundboardRoutesScopeReadsAndMutationsToTheGuildAndOwner() throws java.lang.Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SoundboardController(sounds, database))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        String path = "/api/beebot/guilds/1/soundboards";
        String createBody = "{\"name\":\"Arcade\",\"soundIds\":[\"10\",\"10\"]}";

        String created = mvc.perform(post(path).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)
            .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode detail = JSON.readTree(created);
        String id = detail.path("board").path("id").asText();
        assertEquals(1, detail.path("board").path("soundCount").asInt());
        assertEquals("10", detail.path("sounds").get(0).path("id").asText());

        mvc.perform(post(path).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH).contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/beebot/guilds/3/soundboards/" + id).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isNotFound());

        AuthContext other = new AuthContext(null, "other", "encrypted", Long.MAX_VALUE,
            new AuthUser("9", "other", null, null));
        mvc.perform(delete(path + "/" + id).requestAttr(AuthFilter.AUTH_CONTEXT, other)).andExpect(status().isForbidden());
        mvc.perform(put(path + "/" + id + "/sounds/12").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isNotFound());
        mvc.perform(put(path + "/" + id + "/sounds/11").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isNoContent());
        mvc.perform(delete(path + "/" + id + "/sounds/10").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isNoContent());
        mvc.perform(patch(path + "/" + id).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Arcade 2\"}")).andExpect(status().isOk());
        mvc.perform(delete(path + "/" + id).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)).andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + id).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)).andExpect(status().isNotFound());
    }

    @Test
    public void multipartUploadRejectsInvalidAudioAndUnauthorizedGuilds() throws java.lang.Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(soundController).setControllerAdvice(new ApiExceptionHandler()).build();
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata.json", "application/json",
            "{\"name\":\"New upload\",\"tags\":[\"test\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile audio = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/beebot/guilds/4/sounds").file(audio).file(metadata).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/beebot/guilds/1/sounds").file(audio).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/beebot/guilds/1/sounds").file(audio).file(metadata).requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isBadRequest());
        assertEquals(0, row("SELECT COUNT(*) AS n FROM sound WHERE name = 'New upload'").getAsInt("n"));
    }

    @Test
    public void soundLookupTreatsNamesLiterallyAndKeepsGuildScope() throws java.lang.Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE sound SET name = ? WHERE id IN (10, 12)")) {
            statement.setString(1, "L'été 100%");
            statement.executeUpdate();
        }

        assertEquals(List.of("10"), database.findSoundIds("L'été 100%", "1", "2"));
        assertEquals(List.of("10", "12"), database.findSoundIds("L'été 100%", "3", "9"));
        assertEquals(List.of(), database.findSoundIds("12", "1", "2"));
        assertEquals(List.of(), database.findSoundIds("999999", "1", "2"));
        assertEquals(List.of(), database.findSoundIds("9999999999999999999", "1", "2"));
        assertEquals(List.of("10"), database.searchSoundIds("%", null, "1", "2"));
    }

    @Test
    public void deferredSoundWritesOnceAndKeepsCanonicalIdentity() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        int before = connections.get();

        edit.setName("changed");
        edit.setPublic(false);
        edit.setTagNames(List.of("one", "two"));

        assertEquals(before, connections.get());
        assertEquals("first", sound.getName());
        assertTrue(sound.isPublic());
        assertTrue(edit.update());
        assertEquals(before + 1, connections.get());
        assertSame(sound, sound());
        assertEquals("changed", sound.getName());
        assertFalse(sound.isPublic());
        assertEquals(2, sound.getJsonTags().size());
        assertTrue(sound.getJsonTags().get(0).getId() > 0);
        assertEquals(1, generations.get());
    }

    @Test
    public void immediateSoundSetterPersistsAndDoesNotInvalidateObject() {
        Sound sound = sound();
        assertTrue(sound.setName("immediate"));
        assertSame(sound, sound());
        assertEquals("immediate", row("SELECT name FROM sound WHERE id = 10").get("name"));
    }

    @Test
    public void failedSoundSaveRollsBackMetadataAndTags() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setName("must rollback");
        edit.setTagNames(List.of("fail"));
        failedSql.set("INSERT INTO tag_sounds");

        assertFalse(edit.update());
        assertEquals("first", sound.getName());
        assertEquals("first", row("SELECT name FROM sound WHERE id = 10").get("name"));
        assertEquals(0, row("SELECT COUNT(*) AS n FROM tag").getAsInt("n"));
        assertEquals(0, generations.get());
    }

    @Test
    public void staleSoundDraftCannotOverwriteNewerChange() {
        Sound sound = sound();
        Sound first = sound.deferEdit();
        Sound stale = sound.deferEdit();
        first.setName("newer");
        stale.setPublic(false);

        assertTrue(first.update());
        assertFalse(stale.update());
        assertEquals("newer", sound.getName());
        assertTrue(sound.isPublic());
    }

    @Test
    public void failedPatchDoesNotPublishAnyField() throws java.lang.Exception {
        Sound sound = sound();
        assertThrows(Exception.class, () -> soundController.patch(AUTH, "10", json("{\"name\":\"valid\",\"tags\":null}")));
        assertEquals("first", sound.getName());
        assertEquals("first", row("SELECT name FROM sound WHERE id = 10").get("name"));
    }

    @Test
    public void botAndApiObserveTheSameSoundAndOmittedFieldsSurvive() throws java.lang.Exception {
        Sound sound = sound();
        assertTrue(sound.setTagNames(List.of("kept")));
        assertTrue(sound.setDescription(" Kept description 🎵 "));
        Sound response = soundController.patch(AUTH, "10", json("{\"name\":\"api\"}"));

        assertEquals("api", sound.getName());
        assertEquals("api", response.getName());
        assertSame(sound, sound());
        assertEquals("kept", response.getJsonTags().get(0).getName());
        assertEquals("Kept description 🎵", response.getDescription());
        assertEquals("Kept description 🎵", community().get("10", null).description());
        assertTrue(sound.setPublic(false));
        assertFalse(soundController.sound(AUTH, "10").isPublic());
    }

    @Test
    public void soundDescriptionPatchValidatesAndClearsTheSharedValue() throws java.lang.Exception {
        Sound sound = sound();
        Sound updated = soundController.patch(AUTH, "10", json("{\"description\":\"  A clip.  \"}"));
        assertEquals("A clip.", updated.getDescription());
        assertEquals("A clip.", sound.getDescription());
        assertEquals("A clip.", row("SELECT description FROM sound WHERE id=10").get("description"));

        assertThrows(Exception.class, () -> soundController.patch(AUTH, "10", json("{\"description\":42}")));
        assertThrows(Exception.class, () -> soundController.patch(AUTH, "10",
            json("{\"description\":\"" + "🎵".repeat(Sound.MAX_DESCRIPTION_LENGTH + 1) + "\"}")));
        assertEquals("A clip.", sound.getDescription());

        soundController.patch(AUTH, "10", json("{\"description\":null}"));
        assertNull(sound.getDescription());
        assertNull(community().get("10", null).description());
    }

    @Test
    public void duplicateAndOwnershipChecksStayInsideTheSave() throws java.lang.Exception {
        Sound sound = sound();
        assertEquals("duplicate_sound_name", assertThrows(Exception.class,
            () -> soundController.patch(AUTH, "10", json("{\"name\":\"second\"}"))).getCode());
        AuthContext other = new AuthContext(null, "hash", "encrypted", Long.MAX_VALUE,
            new AuthUser("9", "other", null, null));
        assertEquals(Type.FORBIDDEN, assertThrows(Exception.class,
            () -> soundController.patch(other, "10", json("{\"name\":\"forbidden\"}"))).getType());
        assertEquals("first", sound.getName());
    }

    @Test
    public void votesAreIdempotentAndDisablingLeavesOppositeVote() {
        Sound sound = sound();
        assertEquals(1, sound.vote("2", Set.of("1"), 1, true).likes());
        assertEquals(1, sound.vote("2", Set.of("1"), 1, true).likes());
        assertEquals(1, sound.vote("2", Set.of("1"), -1, true).dislikes());
        Sound.Vote vote = sound.vote("2", Set.of("1"), 1, false);
        assertEquals(0, vote.likes());
        assertEquals(1, vote.dislikes());
        assertTrue(vote.dislikedByMe());
        assertEquals(1, sound.getDislikes());
        assertTrue(sound.dislike("2", false));
        assertEquals(0, sound.getDislikes());
    }

    @Test
    public void failedVoteLeavesCountsAndInteractionUnchanged() {
        Sound sound = sound();
        failedSql.set("UPDATE sound SET likes");
        assertFalse(sound.like("2"));
        assertEquals(0, sound.getLikes());
        assertEquals(0, row("SELECT COUNT(*) AS n FROM sound_vote").getAsInt("n"));
    }

    @Test
    public void voteRevalidatesCurrentVisibility() {
        Sound sound = sound();
        execute("UPDATE sound SET public = FALSE WHERE id = 10");
        assertEquals("sound_not_found", assertThrows(Exception.class,
            () -> sound.vote("9", Set.of(), 1, true)).getCode());
    }

    @Test
    public void playsAndHistoryCommitTogether() {
        Sound sound = sound();
        assertTrue(sound.increaseUserPlays("2", "1"));
        assertEquals(1, sound.getGlobalPlays());
        failedSql.set("UPDATE sound SET plays");
        assertFalse(sound.increaseUserPlays("2", "1"));
        assertEquals(1, sound.getGlobalPlays());
        assertEquals(1, row("SELECT COUNT(*) AS n FROM sound_play").getAsInt("n"));
    }

    @Test
    public void publicCatalogScopesPrivateMatchesAndPaginatesWithDefaultSort() {
        SoundService service = community();
        execute("INSERT INTO sound(id, guild_id, user_id, name, extension, public, plays, duration_ms) VALUES "
            + "(13, '1', '9', 'first arcade', 'mp3', TRUE, 5, 2000), (14, '1', '9', 'arcade first', 'mp3', TRUE, 5, 3000)");
        execute("INSERT INTO tag(id,name) VALUES(1,'arcade')");
        execute("INSERT INTO tag_sounds(sound_id,tag_id) VALUES(10,1),(13,1)");

        assertNull(service.get("10", null).viewer());
        assertEquals("sound_not_found", assertThrows(Exception.class, () -> service.get("11", "9")).getCode());
        assertTrue(service.get("11", "2").viewer().ownedByMe());
        assertEquals(List.of("14", "13", "10"), service.search(null, null).items().stream().map(SoundCatalog.Card::id).toList());
        assertEquals(List.of("11", "10"), service.search(null, "2").mine().stream().map(SoundCatalog.Card::id).toList());
        assertEquals(List.of("11"), service.search(filter("second", null, null, null), "2").mine().stream()
            .map(SoundCatalog.Card::id).toList());
        assertEquals(List.of("10"), service.search(filter("arcade", null, null, null), "2").mine().stream()
            .map(SoundCatalog.Card::id).toList());

        SoundCatalog.Results first = service.search(filter("first", null, 1, null), "2");
        assertEquals("10", first.items().getFirst().id());
        SoundCatalog.Results next = service.search(filter("FIRST", null, 1, first.nextCursor()), "2");
        assertEquals("13", next.items().getFirst().id());
        assertTrue(next.mine().isEmpty());
        assertThrows(Exception.class, () -> service.search(filter("second", null, 1, first.nextCursor()), null));
        assertEquals(2, service.search(filter(null, "arcade", null, null), null).items().size());
        assertEquals("arcade", service.suggestions("ar", null).tags().getFirst().name());
        assertEquals("14", service.suggestions("ar", null).sounds().getFirst().id());
        assertEquals(1, service.creator("2").soundCount());
        assertEquals(3, service.explore(null).latest().size());
    }

    @Test
    public void publicCatalogSearchMatchesNamesAndTagsWithMetricSorts() throws java.lang.Exception {
        MockMvc mvc = communityMvc();
        String path = "/api/beebot/public/sounds";

        execute("UPDATE sound SET name='Brizz', plays=9 WHERE id=10");
        execute("INSERT INTO sound(id, guild_id, user_id, name, extension, public, likes, dislikes) VALUES "
            + "(13, '1', '9', 'reaction', 'mp3', TRUE, 7, 1), (14, '1', '9', 'clip', 'mp3', TRUE, 1, 7)");
        execute("INSERT INTO tag(id,name) VALUES (1,'Some Brizz clips'), (2,'BRIZZ20%')");
        execute("INSERT INTO tag_sounds(sound_id,tag_id) VALUES (11,1), (12,1), (13,1), (13,2), (14,1)");

        JsonNode likes = communityJson(mvc, get(path).param("q", "BRIZZ").param("sort", "mostLiked")
            .header("X-API-Key", "application").header("Authorization", "Bearer owner"), 200);
        assertEquals(3, likes.get("items").size());
        assertEquals("13", likes.at("/items/0/id").asText());
        assertEquals("11", likes.at("/mine/0/id").asText());

        JsonNode dislikes = communityJson(mvc, get(path).param("q", "brizz").param("sort", "mostDisliked")
            .header("X-API-Key", "application"), 200);
        assertEquals("14", dislikes.at("/items/0/id").asText());

        JsonNode first = communityJson(mvc, get(path).param("q", "brizz").param("sort", "popular").param("limit", "1")
            .header("X-API-Key", "application"), 200);
        assertEquals("10", first.at("/items/0/id").asText());
        JsonNode next = communityJson(mvc, get(path).param("q", "brizz").param("sort", "popular")
            .param("cursor", first.get("nextCursor").asText()).header("X-API-Key", "application"), 200);
        assertEquals(2, next.get("items").size());

        JsonNode literal = communityJson(mvc, get(path).param("q", "%").header("X-API-Key", "application"), 200);
        assertEquals(1, literal.get("items").size());
        assertEquals("13", literal.at("/items/0/id").asText());
    }

    @Test
    public void commentsPaginateEachParentAndKeepThePinnedRowOnce() {
        SoundService service = community();
        String pinned = service.addComment("10", "9", null, "Pinned").comment().id();
        String root = service.addComment("10", "9", null, "Root").comment().id();
        String reply = service.addComment("10", "2", root, "Reply").comment().id();
        String nested = service.addComment("10", "9", reply, "Nested").comment().id();
        for (int index = 0; index < 20; index++)
            service.addComment("10", "9", null, "Root #" + index);

        assertEquals(Type.FORBIDDEN, assertThrows(Exception.class,
            () -> service.editComment("10", root, "9", new SoundComment.Edit(null, true, null))).getType());
        service.editComment("10", pinned, "2", new SoundComment.Edit(null, true, null));
        service.vote("10", root, "2", SoundVote.LIKE);

        SoundCatalog.Page<SoundComment> first = service.comments("10", null, null, null, null, null);
        assertEquals(20, first.items().size());
        assertEquals(pinned, first.items().getFirst().id());
        assertEquals(root, first.items().get(1).id());
        assertEquals(1, first.items().get(1).replyCount());

        SoundCatalog.Page<SoundComment> next = service.comments("10", "2", null, null, null, first.nextCursor());
        assertEquals(2, next.items().size());
        assertNull(next.nextCursor());
        assertFalse(next.items().stream().anyMatch(comment -> pinned.equals(comment.id())));
        assertEquals(reply, service.comments("10", null, root, null, null, null).items().getFirst().id());
        assertEquals(nested, service.comments("10", null, reply, null, null, null).items().getFirst().id());
        assertThrows(Exception.class, () -> service.comments("10", null, root, null, null, first.nextCursor()));
        assertThrows(Exception.class, () -> service.comments("11", "2", root, null, null, null));

        SoundCatalog.Page<SoundComment> pinOnly = service.comments("10", null, null, SoundComment.Sort.OLDEST, 1, null);
        assertEquals(pinned, pinOnly.items().getFirst().id());
        assertEquals(root, service.comments("10", null, null, SoundComment.Sort.OLDEST, 1, pinOnly.nextCursor()).items().getFirst().id());
        assertEquals(pinned, service.editComment("10", root, "2", new SoundComment.Edit(null, true, true)).previousPinnedId());
        assertEquals(root, service.comments("10", null, null, SoundComment.Sort.MOST_DISLIKED, null, null).items().getFirst().id());
    }

    @Test
    public void commentDeletionPrunesEmptyBranchesAndVotesUseIndependentTargets() {
        SoundService service = community();
        String root = service.addComment("10", "9", null, "Root").comment().id();
        String reply = service.addComment("10", "2", root, "Reply").comment().id();
        execute("INSERT INTO sound(id, guild_id, user_id, name, extension, public) VALUES (" + root + ", '1', '2', 'same-id', 'mp3', TRUE)");

        assertEquals(SoundVote.LIKE, service.vote(root, null, "2", SoundVote.LIKE).vote());
        assertEquals(SoundVote.DISLIKE, service.vote("10", root, "2", SoundVote.DISLIKE).vote());
        assertEquals(2, row("SELECT COUNT(*) AS n FROM sound_vote").getAsInt("n"));
        assertEquals(SoundVote.LIKE, service.get(root, "2").viewer().vote());
        assertEquals(SoundVote.DISLIKE, service.comments("10", "2", null, null, null, null).items().getFirst().viewer().vote());
        assertEquals(Type.FORBIDDEN, assertThrows(Exception.class, () -> service.deleteComment("10", root, "2")).getType());

        SoundComment.Removal first = service.deleteComment("10", root, "9");
        assertTrue(first.comment().deleted());
        assertNull(first.comment().author());
        assertNull(first.comment().text());
        assertEquals(1, first.commentCount());
        assertEquals(1, row("SELECT COUNT(*) AS n FROM sound_vote").getAsInt("n"));
        assertEquals(0, service.deleteComment("10", reply, "2").commentCount());
        assertTrue(service.comments("10", null, null, null, null, null).items().isEmpty());
        assertNull(service.vote(root, null, "2", null).vote());
        assertEquals(0, row("SELECT COUNT(*) AS n FROM sound_vote").getAsInt("n"));
    }

    @Test
    public void webPlayRetryIsIdempotentAndAnonymousPlaysHaveNoListener() {
        SoundService service = community();
        UUID playback = UUID.randomUUID();
        assertEquals(1, service.recordWebPlay("10", null, playback));
        assertEquals(1, service.recordWebPlay("10", null, playback));
        assertEquals(2, service.recordWebPlay("10", "2", UUID.randomUUID()));
        assertEquals("sound_not_found", assertThrows(Exception.class,
            () -> service.recordWebPlay("11", "9", UUID.randomUUID())).getCode());
        assertEquals(3, database.recordSoundPlay("10", "2", "1", "100", AudioType.SOUND.ordinal()));

        List<BotDB.SoundPlay> events = new ArrayList<>();
        database.forEachSoundPlay(List.of("10"), null, null, Instant.now().plusSeconds(5), events::add);
        assertEquals(3, events.size());
        assertEquals(2, events.stream().filter(play -> play.source() == AudioType.WEB.ordinal()).count());
        assertEquals(1, events.stream().filter(play -> play.userId() == null && play.guildId() == null).count());
        assertEquals(1, events.stream().filter(play -> "100".equals(play.channelId())).count());
        assertEquals(1, service.get("10", null).listeners());
        assertEquals(3, service.explore(null).trending().getFirst().plays());
    }

    @Test
    public void communityCatalogHttpRoutesSeparateAnonymousAndPersonalizedResponses() throws java.lang.Exception {
        MockMvc mvc = communityMvc();
        String publicApi = "/api/beebot/public";

        mvc.perform(get(publicApi + "/sounds")).andExpect(status().isUnauthorized());
        JsonNode guest = communityJson(mvc, get(publicApi + "/sounds").header("X-API-Key", "application"), 200);
        assertEquals("10", guest.at("/items/0/id").asText());
        assertFalse(guest.at("/items/0").has("viewer"));
        assertTrue(guest.get("mine").isEmpty());

        JsonNode owner = communityJson(mvc, get(publicApi + "/sounds").param("q", "second")
            .param("sort", "mostLiked").param("limit", "1")
            .header("X-API-Key", "application").header("Authorization", "Bearer owner"), 200);
        assertTrue(owner.get("items").isEmpty());
        assertEquals("11", owner.at("/mine/0/id").asText());
        assertTrue(owner.at("/mine/0/viewer/ownedByMe").asBoolean());

        JsonNode card = communityJson(mvc, get(publicApi + "/sounds/10").header("X-API-Key", "application"), 200);
        assertTrue(card.get("public").asBoolean());
        assertFalse(card.has("isPublic"));
        assertEquals(0, card.get("commentCount").asInt());
        mvc.perform(get(publicApi + "/sounds/11").header("X-API-Key", "application"))
            .andExpect(status().isNotFound());
        mvc.perform(get(publicApi + "/sounds/11").header("X-API-Key", "application").header("Authorization", "Bearer owner"))
            .andExpect(status().isOk());
        mvc.perform(get(publicApi + "/sounds/10").header("X-API-Key", "application").header("Authorization", "Bearer expired"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(publicApi + "/sounds").param("sort", "MOST_LIKED").header("X-API-Key", "application"))
            .andExpect(status().isBadRequest());

        JsonNode explore = communityJson(mvc, get(publicApi + "/sounds/explore").header("X-API-Key", "application"), 200);
        assertEquals("10", explore.at("/latest/0/id").asText());
        JsonNode suggestions = communityJson(mvc, get(publicApi + "/sounds/suggestions").param("q", "fir")
            .header("X-API-Key", "application"), 200);
        assertEquals("10", suggestions.at("/sounds/0/id").asText());
        mvc.perform(get(publicApi + "/sounds/suggestions").header("X-API-Key", "application"))
            .andExpect(status().isBadRequest());

        JsonNode creator = communityJson(mvc, get(publicApi + "/users/2").header("X-API-Key", "application"), 200);
        assertEquals(1, creator.get("soundCount").asInt());
    }

    @Test
    public void communityCommentAndVoteHttpRoutesValidateIdentityAndPayloads() throws java.lang.Exception {
        MockMvc mvc = communityMvc();
        String soundPath = "/api/beebot/sounds/10";
        String comments = soundPath + "/comments";
        String publicComments = "/api/beebot/public/sounds/10/comments";
        String key = "application";
        String owner = "Bearer owner";
        String other = "Bearer other";

        mvc.perform(post(comments).header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Hello\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(comments).header("X-API-Key", key).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hello\",\"authorId\":\"9\"}"))
            .andExpect(status().isBadRequest());

        JsonNode created = communityJson(mvc, post(comments).header("X-API-Key", key).header("Authorization", owner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hello\"}"), 201);
        String root = created.at("/comment/id").asText();
        assertEquals("2", created.at("/comment/author/id").asText());
        assertEquals(1, created.get("commentCount").asInt());

        JsonNode reply = communityJson(mvc, post(comments).header("X-API-Key", key).header("Authorization", other)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Reply\",\"parentId\":\"" + root + "\"}"), 201);
        assertEquals(root, reply.at("/comment/parentId").asText());

        mvc.perform(patch(comments + "/" + root).header("X-API-Key", key).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}")).andExpect(status().isForbidden());
        mvc.perform(patch(comments + "/" + root).header("X-API-Key", key).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":null}")).andExpect(status().isBadRequest());
        JsonNode pinned = communityJson(mvc, patch(comments + "/" + root)
            .header("X-API-Key", key).header("Authorization", owner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Updated\",\"pinned\":true,\"hearted\":true}"), 200);
        assertTrue(pinned.at("/comment/pinned").asBoolean());
        assertEquals("Updated", pinned.at("/comment/text").asText());

        JsonNode roots = communityJson(mvc, get(publicComments).param("sort", "mostDisliked").header("X-API-Key", key), 200);
        assertEquals(root, roots.at("/items/0/id").asText());
        assertFalse(roots.at("/items/0").has("viewer"));
        JsonNode thread = communityJson(mvc, get(publicComments).param("parentId", root).header("X-API-Key", key), 200);
        assertEquals(reply.at("/comment/id"), thread.at("/items/0/id"));

        JsonNode vote = communityJson(mvc, put(comments + "/" + root + "/vote").header("X-API-Key", key)
            .header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"DISLIKE\"}"), 200);
        assertEquals(root, vote.get("commentId").asText());
        assertEquals("DISLIKE", vote.get("vote").asText());
        assertFalse(vote.has("dislikes"));

        JsonNode soundVote = communityJson(mvc, put(soundPath + "/vote").header("X-API-Key", key)
            .header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"LIKE\"}"), 200);
        assertEquals(1, soundVote.get("likes").asInt());
        assertTrue(soundVote.get("commentId").isNull());
        JsonNode cleared = communityJson(mvc, put(soundPath + "/vote").header("X-API-Key", key)
            .header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"vote\":null}"), 200);
        assertTrue(cleared.get("vote").isNull());
        assertEquals(0, cleared.get("likes").asInt());

        JsonNode removed = communityJson(mvc, delete(comments + "/" + root).header("X-API-Key", key)
            .header("Authorization", owner), 200);
        assertTrue(removed.at("/comment/deleted").asBoolean());
        assertEquals(1, removed.get("commentCount").asInt());
    }

    @Test
    public void communityPlayHttpAcceptsGuestsAndKeepsRetriesIdempotent() throws java.lang.Exception {
        MockMvc mvc = communityMvc();
        String path = "/api/beebot/public/sounds/10/plays";
        String body = "{\"playbackId\":\"" + UUID.randomUUID() + "\"}";

        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post(path).header("X-API-Key", "application").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isNoContent());
        }
        assertEquals(1, row("SELECT plays FROM sound WHERE id=10").getAsInt("plays"));

        mvc.perform(post(path).header("X-API-Key", "application").contentType(MediaType.APPLICATION_JSON)
                .content("{\"playbackId\":\"1-1-1-1-1\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/beebot/public/sounds/11/plays").header("X-API-Key", "application")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
    }

    @Test
    public void communityImageHttpChecksOwnershipMultipartAndSize() throws java.lang.Exception {
        MockMvc mvc = communityMvc();
        String path = "/api/beebot/sounds/10/image";
        MockMultipartFile invalid = new MockMultipartFile("file", "bad.png", "image/png", new byte[]{1, 2});
        MockMultipartFile oversized = new MockMultipartFile("file", "big.png", "image/png", new byte[1024 * 1024 + 1]);

        mvc.perform(put(path).header("X-API-Key", "application").header("Authorization", "Bearer owner")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(HttpMethod.PUT, path).header("X-API-Key", "application")
                .header("Authorization", "Bearer owner")).andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, path).file(invalid).header("X-API-Key", "application")
                .header("Authorization", "Bearer other")).andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, path).file(invalid).header("X-API-Key", "application")
                .header("Authorization", "Bearer owner")).andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, path).file(oversized).header("X-API-Key", "application")
                .header("Authorization", "Bearer owner")).andExpect(status().isPayloadTooLarge());
        mvc.perform(delete(path).header("X-API-Key", "application").header("Authorization", "Bearer owner"))
            .andExpect(status().isNoContent());
    }

    private MockMvc communityMvc() {
        return MockMvcBuilders.standaloneSetup(new SoundCommunityController(community()), soundController)
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new AuthFilter((key, bearer, policy) -> {
                if (!"application".equals(key))
                    throw new Exception(Type.UNAUTHENTICATED, "invalid_api_key", "Application key required");
                if ("Bearer owner".equals(bearer))
                    return AUTH;
                if ("Bearer other".equals(bearer))
                    return new AuthContext(null, "other", null, Long.MAX_VALUE, new AuthUser("9", "other", null, null));
                if (bearer == null && policy == SessionPolicy.OPTIONAL)
                    return new AuthContext(null, null, null, 0, null);

                throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "User session required");
            })).build();
    }

    private JsonNode communityJson(MockMvc mvc, RequestBuilder request, int expectedStatus) throws java.lang.Exception {
        return JSON.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
            .andExpect(result -> assertEquals("no-store", result.getResponse().getHeader("Cache-Control")))
            .andReturn().getResponse().getContentAsString());
    }

    private SoundService community() {
        return new SoundService(database, sounds, new DiscordApi(null, () -> null), Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }

    private SoundCatalog.Search filter(String q, String tag, Integer limit, String cursor) {
        return new SoundCatalog.Search(q, tag, null, null, null, null, limit, cursor);
    }

    @Test
    public void removedSoundRejectsExistingDraft() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setName("deleted");
        assertTrue(sound.delete("2", Set.of()));
        assertFalse(edit.update());
        assertFalse(sound.setName("deleted"));
    }

    @Test
    public void lazyAndFullGreetLoadsShareTheSameUser() {
        execute("INSERT INTO greeting VALUES ('2', '0', 10), ('2', '1', 11)");
        UserData user = users.getOrLoad("2");
        assertEquals("11", user.getGreet("1"));
        assertEquals(Map.of("0", "10", "1", "11"), user.getAllGreets());
        assertTrue(user.unsetGreet("1"));
        assertEquals("10", user.getGreet("1"));
        assertEquals(Map.of("0", "10"), user.getAllGreets());
        assertTrue(user.unsetGreet("0"));
        assertNull(user.getGreet("1"));
        assertSame(user, users.getOrLoad("2"));
    }

    @Test
    public void soundListsKeepTheirJsonFieldsAndNullableGuildNames() throws java.lang.Exception {
        try (Connection connection = connection(); java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE sound SET guild_id = '44' WHERE id = 11");
            statement.executeUpdate("INSERT INTO tag(id, name) VALUES (5, 'sample')");
            statement.executeUpdate("INSERT INTO tag_sounds(sound_id, tag_id) VALUES (10, 5), (11, 5)");
        }

        MockMvc mvc = MockMvcBuilders.standaloneSetup(soundController).build();
        JsonNode guild = JSON.readTree(mvc.perform(get("/api/beebot/guilds/1/sounds").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(List.of("guildId", "counts", "tags", "summary", "sounds"),
            guild.properties().stream().map(Map.Entry::getKey).toList());
        assertEquals(json("{\"all\":1,\"mine\":1,\"others\":0}"), guild.get("counts"));

        JsonNode personal = JSON.readTree(mvc.perform(get("/api/beebot/sounds/me").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(List.of("counts", "tags", "summary", "guilds"),
            personal.properties().stream().map(Map.Entry::getKey).toList());
        assertEquals(json("{\"all\":2}"), personal.get("counts"));
        assertEquals(json("[{\"id\":\"5\",\"name\":\"sample\",\"soundCount\":2}]"), personal.get("tags"));
        assertTrue(personal.at("/guilds/44/name").isNull());
        assertEquals("11", personal.at("/guilds/44/sounds/0/id").asText());

        JsonNode tags = JSON.readTree(mvc.perform(get("/api/beebot/tags").param("q", "sam").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(json("{\"tags\":[{\"id\":\"5\",\"name\":\"sample\"}]}"), tags);
    }

    @Test
    public void bulkGreetsHaveIndependentOutcomesAndUpdateCurrentUser() throws java.lang.Exception {
        UserData user = users.getOrLoad("2");
        MeController.GreetSettings response = meController.settings(AUTH,
            json("{\"greets\":{\"0\":\"11\",\"1\":\"12\",\"3\":\"10\"}}"));
        JsonNode result = JSON.valueToTree(response).get("greets");
        assertEquals("ok", result.get("0").get("status").asText());
        assertEquals("error", result.get("1").get("status").asText());
        assertEquals("ok", result.get("3").get("status").asText());
        assertEquals("11", user.getGlobalGreet());
        assertEquals("10", user.getGuildGreet("3"));
        assertSame(user, users.getOrLoad("2"));
        assertEquals(2, row("SELECT COUNT(*) AS n FROM greeting").getAsInt("n"));
    }

    @Test
    public void greetFailurePreservesPreviouslyLoadedValue() {
        UserData user = users.getOrLoad("2");
        assertTrue(user.setGreet("0", "10"));
        failedSql.set("INSERT INTO greeting");
        assertFalse(user.setGreet("0", "11"));
        assertEquals("10", user.getGlobalGreet());
        assertEquals("10", row("SELECT sound_id FROM greeting WHERE user_id = '2' AND guild_id = '0'").get("sound_id"));
    }

    @Test
    public void failedFullGreetLoadDoesNotPoisonCompleteness() {
        UserData user = users.getOrLoad("2");
        failedSql.set("FROM greeting");
        assertThrows(Exception.class, user::getAllGreets);
        failedSql.set(null);
        execute("INSERT INTO greeting VALUES ('2', '3', 10)");
        assertEquals(Map.of("3", "10"), user.getAllGreets());
    }

    @Test
    public void aliasDeleteUsesTheStoredIdAndPublishesOnlyAfterCommit() {
        UserData user = users.getOrLoad("2");
        assertTrue(user.addAlias("hello", "help"));
        user.getAliases().clear();
        assertTrue(user.getAliases().containsKey("hello"));
        failedSql.set("DELETE FROM alias");
        assertFalse(user.deleteAlias("hello"));
        assertTrue(user.getAliases().containsKey("hello"));
        failedSql.set(null);
        assertTrue(user.deleteAlias("hello"));
        assertTrue(user.getAliases().isEmpty());
        assertEquals(0, row("SELECT COUNT(*) AS n FROM alias").getAsInt("n"));
    }

    @Test
    public void concurrentExperienceConsumesCooldownOnce() throws java.lang.Exception {
        MemberData member = guilds.withGuildData("1", guild -> guild.getMemberData("2"));
        List<MemberData.Experience> results = concurrently(20, () -> guilds.receiveMemberExperience("1", "2", "100"));
        int saved = 0;
        for (MemberData.Experience result : results) {
            if (result.saved())
                saved++;
        }

        assertEquals(1, saved);
        assertEquals(1, member.getMessages());
        assertTrue(member.getExperience() > 0);
        assertSame(member, guilds.withGuildData("1", guild -> guild.getMemberData("2")));
        assertEquals(1, row("SELECT messages FROM member WHERE guild_id = '1' AND user_id = '2'").getAsInt("messages"));
    }

    @Test
    public void failedExperienceDoesNotConsumeCooldownOrCreateMember() {
        MemberData member = new MemberData("2", "1", "!", database);
        LocalDateTime before = member.getLastMessageTime();
        failedSql.set("UPDATE member");
        assertFalse(member.receiveExperience(1).saved());
        assertEquals(before, member.getLastMessageTime());
        assertEquals("0", member.getId());
        assertEquals(0, row("SELECT COUNT(*) AS n FROM member").getAsInt("n"));
        failedSql.set(null);
        assertTrue(member.receiveExperience(1).saved());
    }

    @Test
    public void memberIdentityIncludesGuildAndReadDoesNotCreateRows() {
        MemberData first = guilds.withGuildData("1", guild -> guild.getMemberData("2"));
        MemberData other = guilds.withGuildData("3", guild -> guild.getMemberData("2"));
        assertNotSame(first, other);
        assertEquals(0, row("SELECT COUNT(*) AS n FROM member").getAsInt("n"));
        assertTrue(first.receiveExperience(1).saved());
        assertEquals(0, other.getMessages());
    }

    @Test
    public void historyStreamingAndMetadataShareOneReadSession() {
        assertTrue(sound().increaseUserPlays("2", "1"));
        int before = connections.get();
        List<BotDB.SoundPlay> plays = new ArrayList<>();
        database.withSoundReadSession(() -> {
            database.readSounds(List.of("10"));
            database.readSoundTags(List.of("10"));
            database.readSoundUserPlays("10", "2");
            database.readSoundInteraction("10", "2");
            database.forEachSoundPlay(List.of("10"), null, null, Instant.now().plusSeconds(5), plays::add);
            return true;
        });
        assertEquals(before + 1, connections.get());
        assertEquals(1, plays.size());
        assertEquals("2", plays.get(0).userId());
    }

    @Test
    public void httpRoutesKeepContractsAndMalformedJsonHandling() throws java.lang.Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(soundController, meController)
            .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(patch("/api/beebot/sounds/10").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"http\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/beebot/me/settings").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"greets\":{\"0\":\"10\"}}"))
            .andExpect(status().isOk());
        String malformed = mvc.perform(post("/api/beebot/me/settings").requestAttr(AuthFilter.AUTH_CONTEXT, AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertEquals("invalid_request", JSON.readTree(malformed).get("code").asText());
        assertEquals("http", sound().getName());
        assertEquals("10", users.getOrLoad("2").getGlobalGreet());
    }

    @Test
    public void apiPatchPreservesFreshOmittedFields() throws java.lang.Exception {
        Sound sound = sound();
        execute("UPDATE sound SET name = 'external' WHERE id = 10");

        soundController.patch(AUTH, "10", json("{\"public\":false}"));

        assertEquals("external", sound.getName());
        assertEquals("external", row("SELECT name FROM sound WHERE id = 10").get("name"));
    }

    @Test
    public void concurrentVotesKeepAllCounts() throws java.lang.Exception {
        Sound sound = sound();
        AtomicInteger userIds = new AtomicInteger(100);
        concurrently(20, () -> sounds.with("10", current -> current.vote(
            String.valueOf(userIds.incrementAndGet()), Set.of("1"), 1, true)));

        assertEquals(20, sound.getLikes());
        assertEquals(20, row("SELECT likes FROM sound WHERE id = 10").getAsInt("likes"));
    }

    @Test
    public void deletionRequiresUploaderOrAdministratorAndKeepsStateOnFailure() {
        Sound sound = sound();
        assertEquals(Type.FORBIDDEN, assertThrows(Exception.class,
            () -> sound.delete("9", Set.of())).getType());
        failedSql.set("DELETE FROM sound WHERE");
        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class,
            () -> sound.delete("2", Set.of())).getType());
        failedSql.set(null);
        assertTrue(sound.delete("9", Set.of("1")));
        assertFalse(sound.setName("deleted"));
    }

    @Test
    public void sqlFailureForOneGreetDoesNotUndoOtherScopes() throws java.lang.Exception {
        UserData user = users.getOrLoad("2");
        assertTrue(user.setGreet("0", "10"));
        assertTrue(user.setGreet("1", "10"));
        failedSql.set("INSERT INTO greeting");

        JsonNode response = JSON.valueToTree(meController.settings(AUTH,
            json("{\"greets\":{\"0\":null,\"1\":\"11\",\"3\":null}}")));

        assertEquals("ok", response.get("greets").get("0").get("status").asText());
        assertEquals("error", response.get("greets").get("1").get("status").asText());
        assertEquals("ok", response.get("greets").get("3").get("status").asText());
        assertNull(user.getGlobalGreet());
        assertEquals("10", user.getGuildGreet("1"));
    }

    @Test
    public void playbackUsesSharedGreetAndRechecksVisibility() {
        UserData user = users.getOrLoad("2");
        assertTrue(user.setGreet("0", "10"));
        assertEquals("10", sounds.resolveGreet("2", "1").getId());
        assertTrue(user.setGreet("1", "11"));
        assertEquals("11", sounds.resolveGreet("2", "1").getId());
        execute("UPDATE sound SET guild_id = '3' WHERE id = 11");
        assertNull(sounds.resolveGreet("2", "1"));
        assertTrue(user.unsetGreet("1"));
        assertEquals("10", sounds.resolveGreet("2", "1").getId());
    }

    @Test
    public void fullGreetResponseOmitsDeletedAndInvisibleSounds() {
        execute("INSERT INTO greeting VALUES ('2', '0', 10), ('2', '1', 11), ('2', '3', 12)");
        SoundCache.Context context = new SoundCache.Context("2", Map.of());
        Map<String, Sound> greets = sounds.greets(context);
        assertEquals(Set.of("0", "1"), greets.keySet());
        execute("DELETE FROM sound WHERE id = 10");
        assertEquals(Set.of("1"), sounds.greets(context).keySet());
    }

    @Test
    public void levelUpResultRequiresSuccessfulPersistence() {
        MemberData member = new MemberData("2", "1", "!", database);
        assertTrue(member.setExpData(250, 1));
        failedSql.set("UPDATE member");
        MemberData.Experience failed = member.receiveExperience(1);
        assertFalse(failed.saved());
        assertFalse(failed.leveledUp());
        assertEquals(1, member.getLevel());
        failedSql.set(null);
        MemberData.Experience saved = member.receiveExperience(1);
        assertTrue(saved.saved());
        assertTrue(saved.leveledUp());
        assertEquals(2, saved.level());
        assertEquals(2, member.getMessages());
    }

    @Test
    public void userBulkHydratesOnlyMissingUsersWithoutPerUserQueries() {
        List<String> fetched = new ArrayList<>();
        BotDB loader = new BotDB(() -> {
            throw new AssertionError("Unexpected per-user query");
        }) {
            @Override
            public List<QueryRecord> readUserAliases(java.util.Collection<String> ids) {
                fetched.addAll(ids);

                return List.of();
            }
        };
        UserCache cache = new UserCache(loader);
        List<String> ids = new ArrayList<>();

        for (int index = 0; index < 20; index++) {
            String id = String.valueOf(index);
            ids.add(id);
            if (index < 10)
                cache.put(id, UserData.fromRows(id, List.of(), loader));
        }

        UserData hit = cache.get("0");
        List<UserData> loaded = cache.getOrLoadAll(ids);

        assertEquals(ids.subList(10, 20), fetched);
        assertEquals(20, loaded.size());
        assertSame(hit, loaded.get(0));
        assertSame(loaded.get(19), cache.getOrLoad("19"));
        assertEquals(10, fetched.size());
    }

    @Test
    public void draftSurvivesVotesCountersAndReorderedTagHydration() throws java.lang.Exception {
        Sound sound = sound();
        assertTrue(sound.setTagNames(List.of("zebra", "alpha")));
        Sound edit = sound.deferEdit();
        edit.setName("pending");

        soundController.like(AUTH, "10", json("{\"enabled\":true}"));
        execute("UPDATE sound SET plays = 7 WHERE id = 10");
        sounds.withVisibleSound(sounds.context("2"), "10", current -> true);

        assertTrue(edit.update());
        assertEquals("pending", sound.getName());
        assertEquals(1, sound.getLikes());
        assertEquals(7, sound.getGlobalPlays());
        assertEquals(2, sound.getJsonTags().size());
    }

    @Test
    public void rejectedPatchDoesNotInvalidatePendingMetadataEdit() throws java.lang.Exception {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setName("pending");

        assertThrows(Exception.class, () -> soundController.patch(AUTH, "10", json("{\"name\":\"rejected\",\"tags\":null}")));

        assertTrue(edit.update());
        assertEquals("pending", sound.getName());
    }

    @Test
    public void failedPatchDoesNotInvalidateAnotherPendingEdit() throws java.lang.Exception {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setName("pending");
        failedSql.set("UPDATE sound SET name");

        assertThrows(Exception.class, () -> soundController.patch(AUTH, "10", json("{\"name\":\"failed\"}")));
        failedSql.set(null);

        assertTrue(edit.update());
        assertEquals("pending", sound.getName());
    }

    @Test
    public void refreshOfChangedNameRejectsOldDraft() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setPublic(false);
        execute("UPDATE sound SET name = 'external' WHERE id = 10");

        sounds.withVisibleSound(sounds.context("2"), "10", current -> true);

        assertFalse(edit.update());
        assertEquals("external", sound.getName());
        assertTrue(sound.isPublic());
    }

    @Test
    public void refreshOfChangedVisibilityRejectsOldDraft() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setName("stale");
        execute("UPDATE sound SET public = FALSE WHERE id = 10");

        sounds.withVisibleSound(sounds.context("2"), "10", current -> true);

        assertFalse(edit.update());
        assertFalse(sound.isPublic());
        assertEquals("first", sound.getName());
    }

    @Test
    public void refreshOfChangedTagsRejectsOldDraft() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setName("stale");
        execute("INSERT INTO tag(id, name) VALUES (20, 'external')");
        execute("INSERT INTO tag_sounds VALUES (10, 20)");

        sounds.withVisibleSound(sounds.context("2"), "10", current -> true);

        assertFalse(edit.update());
        assertEquals(List.of(new Tag(20, "external")), sound.getJsonTags());
    }

    @Test
    public void newTagsBecomeStoredOnlyAfterSuccessfulCommit() {
        Sound sound = sound();
        Sound edit = sound.deferEdit();
        edit.setTagNames(List.of("new", "new"));

        assertEquals(1, edit.getJsonTags().size());
        assertTrue(edit.getJsonTags().get(0).isNew());
        assertFalse(edit.getJsonTags().get(0).isStored());
        assertEquals(Tag.MAX_TAG_SOUND, edit.getTags().size());
        assertTrue(edit.getTags().get(1).isEmpty());
        assertTrue(sound.getJsonTags().isEmpty());

        failedSql.set("INSERT INTO tag_sounds");
        assertFalse(edit.update());
        assertTrue(sound.getJsonTags().isEmpty());
        assertTrue(edit.getJsonTags().get(0).isNew());
        failedSql.set(null);

        assertTrue(edit.update());
        assertTrue(sound.getJsonTags().get(0).isStored());
        assertFalse(sound.getJsonTags().get(0).isNew());
        assertEquals(Tag.MAX_TAG_SOUND, sound.getTags().size());
        sound.getTags().clear();
        assertEquals(Tag.MAX_TAG_SOUND, sound.getTags().size());
    }

    @Test
    public void tagDeletionKeepsFiveSlotsAndRestoresPreviousMappingAfterRollback() {
        Sound sound = sound();
        assertTrue(sound.setTagNames(List.of("kept")));
        Tag kept = sound.getJsonTags().get(0);
        Sound edit = sound.deferEdit();
        edit.setTagNames(List.of("replacement"));
        failedSql.set("INSERT INTO tag_sounds");

        assertFalse(edit.update());
        assertEquals(List.of(kept), sound.getJsonTags());
        assertEquals(1, row("SELECT COUNT(*) AS n FROM tag_sounds WHERE sound_id = 10").getAsInt("n"));
        failedSql.set(null);

        assertTrue(sound.setTagNames(List.of()));
        assertTrue(sound.getJsonTags().isEmpty());
        assertEquals(Tag.MAX_TAG_SOUND, sound.getTags().size());

        for (Tag tag : sound.getTags())
            assertTrue(tag.isEmpty());

        assertEquals(0, row("SELECT COUNT(*) AS n FROM tag_sounds WHERE sound_id = 10").getAsInt("n"));
    }

    @Test
    public void namedPlayAndVoteReadsPreserveMissingValuesAndParameterBinding() {
        Sound sound = sound();
        assertArrayEquals(new int[] {0, 0}, sound.getPlays("2"));
        assertFalse(sound.hasLiked("2"));
        assertFalse(sound.hasDisliked("2"));
        assertTrue(sound.increaseUserPlays("2", "1"));
        assertTrue(sound.increaseUserPlays("9", "1"));
        assertTrue(sound.like("2"));

        assertArrayEquals(new int[] {2, 1}, sound.getPlays("2"));
        assertTrue(sound.hasLiked("2"));
        assertFalse(sound.hasDisliked("2"));
        assertEquals(0, database.readSoundUserPlays("10", "2' OR '1'='1").getAsInt("times"));
        assertTrue(database.readSoundInteraction("10", "2' OR '1'='1").isEmpty());
    }

    @Test
    public void namedPlayAndVoteReadsPropagateStorageFailures() {
        Sound sound = sound();
        failedSql.set("FROM sound_play WHERE");
        assertEquals("sound_storage_unavailable", assertThrows(Exception.class, () -> sound.getPlays("2")).getCode());
        failedSql.set("FROM sound_vote WHERE");
        assertEquals("sound_storage_unavailable", assertThrows(Exception.class, () -> sound.hasLiked("2")).getCode());
    }

    /* -------------------------------------------------------------------------- */

    private Sound sound() {
        return sounds.getOrLoad("10");
    }

    private JsonNode json(String value) throws java.lang.Exception {
        return JSON.readTree(value);
    }

    private Connection connection() {
        try {
            Connection connection = DriverManager.getConnection(url);
            connection.createStatement().execute("SET TIME ZONE 'UTC'");

            return connection;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void execute(String sql) {
        try (Connection connection = connection()) {
            connection.createStatement().execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private QueryRecord row(String sql) {
        try (Connection connection = connection(); java.sql.ResultSet rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            QueryRecord result = new QueryRecord();
            for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++)
                result.put(rows.getMetaData().getColumnLabel(index).toLowerCase(), rows.getObject(index));
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> List<T> concurrently(int count, java.util.concurrent.Callable<T> operation) throws java.lang.Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return operation.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures)
                results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
