package com.safjnest.bot;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
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
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.cache.managers.UserCache;
import com.safjnest.bot.model.UserData;
import com.safjnest.bot.model.guild.MemberData;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.auth.DiscordApi;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.spring.controller.beebot.MeController;
import com.safjnest.spring.controller.beebot.SoundController;
import com.safjnest.spring.exception.ApiExceptionHandler;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
                "CREATE TABLE sound (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19), user_id VARCHAR(19), name VARCHAR(100), extension VARCHAR(10), public BOOLEAN, time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, plays INT DEFAULT 0, likes INT DEFAULT 0, dislikes INT DEFAULT 0)",
                "CREATE TABLE tag (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))",
                "CREATE TABLE tag_sounds (sound_id INT REFERENCES sound(id) ON DELETE CASCADE, tag_id INT REFERENCES tag(id))",
                "CREATE TABLE greeting (user_id VARCHAR(19), guild_id VARCHAR(19), sound_id INT REFERENCES sound(id) ON DELETE CASCADE, PRIMARY KEY(user_id, guild_id))",
                "CREATE TABLE sound_interactions (sound_id INT REFERENCES sound(id) ON DELETE CASCADE, user_id VARCHAR(19), value INT, PRIMARY KEY(sound_id, user_id))",
                "CREATE TABLE sound_history (id INT AUTO_INCREMENT PRIMARY KEY, sound_id INT REFERENCES sound(id) ON DELETE CASCADE, user_id VARCHAR(19), guild_id VARCHAR(19), time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, source INT)",
                "CREATE TABLE soundboard_sounds (id INT AUTO_INCREMENT PRIMARY KEY, sound_id INT REFERENCES sound(id) ON DELETE CASCADE)",
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
        Sound response = soundController.patch(AUTH, "10", json("{\"name\":\"api\"}"));

        assertEquals("api", sound.getName());
        assertEquals("api", response.getName());
        assertSame(sound, sound());
        assertEquals("kept", response.getJsonTags().get(0).getName());
        assertTrue(sound.setPublic(false));
        assertFalse(soundController.sound(AUTH, "10").isPublic());
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
        assertEquals(0, row("SELECT COUNT(*) AS n FROM sound_interactions").getAsInt("n"));
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
        assertEquals(1, row("SELECT COUNT(*) AS n FROM sound_history").getAsInt("n"));
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
        assertFalse(sound.delete());
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
        failedSql.set("FROM sound_history WHERE");
        assertEquals("sound_storage_unavailable", assertThrows(Exception.class, () -> sound.getPlays("2")).getCode());
        failedSql.set("FROM sound_interactions WHERE");
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
