package com.safjnest.guild;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.model.guild.BlacklistData;
import com.safjnest.bot.model.guild.ChannelData;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.GuildData.AlertState;
import com.safjnest.bot.model.guild.GuildData.Result;
import com.safjnest.bot.model.guild.GuildData.Settings;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertSendType;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.bot.model.guild.alert.TwitchData;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.spring.controller.beebot.GuildController;
import com.safjnest.spring.exception.ApiExceptionHandler;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.RoleColors;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class GuildEditingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final AuthContext AUTH = new AuthContext(null, "hash", "encrypted", Long.MAX_VALUE,
        new AuthUser("2", "admin", null, null));

    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicBoolean failAlert = new AtomicBoolean();
    private final AtomicBoolean failDelete = new AtomicBoolean();

    private final List<String> subscriptions = new ArrayList<>();

    private String url;
    private BotDB database;
    private GuildCache cache;

    private Guild discord;
    private GuildController controller;

    @Before
    public void setup() throws java.lang.Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1";

        try (Connection connection = connection()) {
            for (String sql : List.of(
                "CREATE TABLE guild (guild_id VARCHAR(19) PRIMARY KEY, prefix VARCHAR(32) DEFAULT '!', name_tts VARCHAR(19), language_tts VARCHAR(19), exp_enabled BOOLEAN DEFAULT TRUE, threshold TINYINT DEFAULT 0, blacklist_channel VARCHAR(19), blacklist_enabled BOOLEAN DEFAULT FALSE, league_shard TINYINT DEFAULT 3)",
                "CREATE TABLE channel (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19) REFERENCES guild(guild_id), channel_id VARCHAR(19), exp_modifier DOUBLE DEFAULT 1, exp_enabled BOOLEAN DEFAULT TRUE, stats_enabled BOOLEAN DEFAULT TRUE, league_shard TINYINT DEFAULT 0)",
                "CREATE TABLE alert (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19) REFERENCES guild(guild_id), message VARCHAR(2000), private_message VARCHAR(2000), channel VARCHAR(19), enabled BOOLEAN, send_type INT, type INT)",
                "CREATE TABLE alert_twitch (id INT AUTO_INCREMENT PRIMARY KEY, alert_id INT REFERENCES alert(id), streamer_id VARCHAR(32), role_id VARCHAR(19))",
                "CREATE TABLE alert_reward (id INT AUTO_INCREMENT PRIMARY KEY, alert_id INT REFERENCES alert(id), level INT, temporary BOOLEAN)",
                "CREATE TABLE alert_role (id INT AUTO_INCREMENT PRIMARY KEY, alert_id INT REFERENCES alert(id), role_id VARCHAR(19))",
                "CREATE TABLE automated_action (id INT AUTO_INCREMENT PRIMARY KEY, guild_id VARCHAR(19) REFERENCES guild(guild_id), action INT, action_role VARCHAR(19), action_time INT, infractions INT, infractions_time INT)"))
                connection.createStatement().execute(sql);
        }

        database = new BotDB(this::connection) {
            @Override
            public SavedAlert saveAlert(AlertData edit) {
                if (failAlert.get())
                    throw new IllegalStateException("Simulated failure");

                return super.saveAlert(edit);
            }

            @Override
            public boolean deleteGuildAlert(String guildId, int id, String prefix) {
                if (failDelete.get())
                    throw new IllegalStateException("Simulated failure");

                return super.deleteGuildAlert(guildId, id, prefix);
            }
        };
        cache = new GuildCache(database, () -> "!");

        GuildChannel channel = proxy(GuildChannel.class, Map.of("getId", "10", "getName", "general", "getType", ChannelType.TEXT));
        Role role = proxy(Role.class, Map.of("getId", "20", "getName", "member", "getColors", new RoleColors(123, 0, 0),
            "getPosition", 1, "isManaged", false, "isPublicRole", false));
        Member self = proxy(SelfMember.class, Map.of("hasPermission", true, "canInteract", true));
        discord = proxy(Guild.class, Map.of("getId", "1", "getName", "Test guild", "getChannels", List.of(channel),
            "getRoles", List.of(role), "getSelfMember", self));

        controller = new GuildController(cache, id -> id.equals("1") ? discord : null,
            (context, id) -> {}, context -> List.of(), subscriptions::add);
    }

    @Test
    public void getReturnsDefaultsWithoutCreatingRows() {
        Settings settings = controller.getGuild("1", AUTH);
        AlertState leave = (AlertState) settings.settings().get("leave");

        assertFalse(leave.exists());
        assertFalse(leave.enabled());
        assertTrue(leave.problems().isEmpty());
        assertTrue(database.readGuildData(List.of("1")).isEmpty());
        assertTrue(database.readGuildAlerts("1").isEmpty());
        assertEquals("10", settings.channels().get(0).get("id"));
        assertEquals(true, settings.roles().get(0).get("assignable"));
    }

    @Test
    public void deferredChangesArePrivateAndUseOneWriteConnection() throws java.lang.Exception {
        post("{\"leave\":{\"message\":\"before\"}}");

        GuildData guild = guild();
        AlertData alert = guild.getAlert(AlertType.LEAVE);
        int before = connections.get();
        AlertData edit = alert.deferEdit();

        assertTrue(edit.setMessage("after"));
        assertTrue(edit.setPrivateMessage("private"));
        assertEquals(before, connections.get());
        assertEquals("before", alert.getMessage());
        assertEquals("before", database.readGuildAlerts("1").get(0).getMessage());

        before = connections.get();

        assertTrue(cache.withGuildData("1", current -> edit.update()));
        assertEquals(before + 1, connections.get());
        assertSame(alert, guild.getAlert(AlertType.LEAVE));
        assertEquals("after", alert.getMessage());
        assertEquals("private", alert.getPrivateMessage());
        assertEquals("after", database.readGuildAlerts("1").get(0).getMessage());
    }

    @Test
    public void immediateSetterAndApiShareTheSameInstance() throws java.lang.Exception {
        post("{\"leave\":{}}");

        AlertData alert = guild().getAlert(AlertType.LEAVE);

        assertTrue(cache.withGuildData("1", guild -> guild.getAlert(AlertType.LEAVE).setMessage("bot")));
        assertEquals("bot", ((AlertState) controller.getGuild("1", AUTH).settings().get("leave")).message());

        post("{\"leave\":{\"privateMessage\":\"api\"}}");

        assertSame(alert, guild().getAlert(AlertType.LEAVE));
        assertEquals("api", alert.getPrivateMessage());
        assertEquals("bot", alert.getMessage());
    }

    @Test
    public void staleDraftCannotOverwriteNewerChanges() throws java.lang.Exception {
        post("{\"leave\":{}}");

        AlertData alert = guild().getAlert(AlertType.LEAVE);
        AlertData first = alert.deferEdit();
        AlertData stale = alert.deferEdit();

        first.setMessage("newer");
        stale.setPrivateMessage("stale");

        assertTrue(first.update());

        int before = connections.get();

        assertFalse(stale.update());
        assertEquals(before, connections.get());
        assertEquals("newer", alert.getMessage());
        assertEquals("", alert.getPrivateMessage());
    }

    @Test
    public void sqlFailureRollsBackAllAlertFieldsAndRoles() throws java.lang.Exception {
        post("{\"welcome\":{\"message\":\"before\",\"roleIds\":[\"20\"]}}");

        AlertData alert = guild().getAlert(AlertType.WELCOME);
        AlertData edit = alert.deferEdit();

        edit.setMessage("after");
        edit.setRoles(List.of("123456789012345678901"));

        assertFalse(edit.update());
        assertEquals("before", alert.getMessage());
        assertArrayEquals(new String[] {"20"}, alert.getRolesAsArray());

        AlertData stored = database.readGuildAlerts("1").get(0);

        assertEquals("before", stored.getMessage());
        assertArrayEquals(new String[] {"20"}, stored.getRolesAsArray());
    }

    @Test
    public void failedCreationDoesNotPublishOrCreateGuildRows() {
        GuildData guild = guild();
        TwitchData edit = (TwitchData) guild.newAlert(AlertType.TWITCH, "100");
        edit.setMessage("live");
        edit.setStreamerRole("123456789012345678901");

        assertFalse(edit.update());
        assertNull(guild.getTwitchdata("100"));
        assertTrue(database.readGuildData(List.of("1")).isEmpty());
        assertTrue(database.readGuildAlerts("1").isEmpty());
    }

    @Test
    public void rolesCanBeReplacedDeduplicatedAndCleared() throws java.lang.Exception {
        post("{\"welcome\":{\"roleIds\":[\"20\",\"20\"]}}");

        AlertData alert = guild().getAlert(AlertType.WELCOME);

        assertArrayEquals(new String[] {"20"}, alert.getRolesAsArray());

        post("{\"welcome\":{\"roleIds\":[]}}");

        assertArrayEquals(new String[0], alert.getRolesAsArray());
        assertArrayEquals(new String[0], database.readGuildAlerts("1").get(0).getRolesAsArray());
    }

    @Test
    public void validationRejectsOneBlockAndOtherBlocksCommit() throws java.lang.Exception {
        post("{\"leave\":{\"message\":\"before\"}}");

        Map<String, Object> results = post("{\"leave\":{\"message\":\"after\",\"enabled\":\"yes\"},\"blacklist\":{\"threshold\":5}}");

        assertEquals("error", result(results, "leave").status());
        assertEquals("enabled", result(results, "leave").errors().get(0).field());
        assertEquals("before", guild().getAlert(AlertType.LEAVE).getMessage());
        assertEquals("ok", result(results, "blacklist").status());
        assertEquals(5, database.readGuildData(List.of("1")).get(0).getAsInt("threshold"));
    }

    @Test
    public void storageFailureLeavesCacheIntactAndOtherBlocksCommit() throws java.lang.Exception {
        post("{\"leave\":{\"message\":\"before\"}}");

        failAlert.set(true);
        Map<String, Object> results = post("{\"leave\":{\"message\":\"after\"},\"blacklist\":{\"threshold\":5}}");

        assertEquals("storage_error", result(results, "leave").errors().get(0).code());
        assertEquals("before", result(results, "leave").alert().message());
        assertEquals("ok", result(results, "blacklist").status());
    }

    @Test
    public void omissionsRetainFieldsAndExplicitNullClears() throws java.lang.Exception {
        post("{\"leave\":{\"message\":\"hello\",\"privateMessage\":\"private\",\"channelId\":\"10\"}}");

        AlertState alert = result(post("{\"leave\":{\"enabled\":false}}"), "leave").alert();

        assertEquals("hello", alert.message());
        assertEquals("private", alert.privateMessage());
        assertEquals("10", alert.channelId());

        alert = result(post("{\"leave\":{\"message\":null,\"privateMessage\":null,\"channelId\":null}}"), "leave").alert();

        assertEquals("", alert.message());
        assertNull(alert.privateMessage());
        assertNull(alert.channelId());
    }

    @Test
    public void collectionItemsCommitIndependently() throws java.lang.Exception {
        Map<String, Object> results = post("{\"twitch\":{\"100\":{\"message\":\"live\",\"roleId\":\"20\"},\"101\":{\"enabled\":\"yes\"}},\"reward\":{\"5\":{\"roleIds\":[\"20\"],\"temporary\":true},\"6\":{}}}");
        Map<?, ?> twitch = (Map<?, ?>) results.get("twitch");

        assertEquals("ok", ((Result) twitch.get("100")).status());
        assertEquals("error", ((Result) twitch.get("101")).status());
        assertEquals(List.of("100"), subscriptions);
        assertEquals(3, database.readGuildAlerts("1").size());

        post("{\"reward\":{\"5\":{\"message\":\"level up\"}}}");
        RewardData reward = guild().getAlert(AlertType.REWARD, 5);

        assertTrue(reward.isTemporary());
        assertEquals("level up", reward.getMessage());
        assertArrayEquals(new String[] {"20"}, reward.getRolesAsArray());
    }

    @Test
    public void otherModelsUsePrivateEditsAndKeepCanonicalIdentity() throws java.lang.Exception {
        GuildData guild = guild();
        BlacklistData blacklist = guild.getBlacklistData();
        BlacklistData edit = blacklist.deferEdit();

        edit.setThreshold(5);
        edit.setBlacklistEnabled(true);

        assertEquals(0, blacklist.getThreshold());
        assertTrue(edit.update());
        assertSame(blacklist, guild.getBlacklistData());
        assertEquals(5, blacklist.getThreshold());

        ChannelData channel = guild.getChannelData("10");
        ChannelData channelEdit = channel.deferEdit();

        channelEdit.setExperienceModifier(1.5);
        channelEdit.setCommand(false);

        assertEquals(1, channel.getExperienceModifier(), 0);
        assertTrue(channelEdit.update());
        assertSame(channel, guild.getChannelData("10"));

        post("{\"general\":{\"prefix\":\"?\",\"leagueShard\":\"EUW1\",\"voice\":\"John\"},\"channels\":{\"10\":{\"experienceEnabled\":false}}}");

        assertSame(guild, guild());
        assertSame(channel, guild.getChannelData("10"));
        assertEquals("?", guild.getPrefix());
        assertFalse(channel.isExpSystemEnabled());
        assertEquals(1.5, channel.getExperienceModifier(), 0);
        assertFalse(channel.getCommand());
        assertEquals(1, database.readGuildChannels(List.of("1")).size());
    }

    @Test
    public void unpersistedChannelLookupsStayOutOfApiCollections() {
        guild().getChannelData("10");

        assertTrue(((Map<?, ?>) controller.getGuild("1", AUTH).settings().get("channels")).isEmpty());
        assertTrue(database.readGuildData(List.of("1")).isEmpty());
    }

    @Test
    public void deletionPublishesOnlyAfterSuccessAndRejectsOldDrafts() throws java.lang.Exception {
        post("{\"leave\":{}}");

        GuildData guild = guild();
        AlertData alert = guild.getAlert(AlertType.LEAVE);
        AlertData edit = alert.deferEdit();

        failDelete.set(true);

        assertFalse(cache.withGuildData("1", current -> current.deleteAlert(alert)));
        assertSame(alert, guild.getAlert(AlertType.LEAVE));

        failDelete.set(false);

        assertTrue(cache.withGuildData("1", current -> current.deleteAlert(alert)));
        assertNull(guild.getAlert(AlertType.LEAVE));
        assertFalse(edit.update());
        assertTrue(database.readGuildAlerts("1").isEmpty());
    }

    @Test
    public void validityIncludesFinalExperienceRegardlessOfBlockOrder() throws java.lang.Exception {
        Result result = result(post("{\"levelUp\":{\"message\":\"hello\"},\"general\":{\"experienceEnabled\":false}}"), "levelUp");

        assertEquals("experience_disabled", result.alert().problems().get(0).code());
        assertEquals(result.alert(), controller.getGuild("1", AUTH).settings().get("levelUp"));

        for (AlertType type : AlertType.values()) {
            for (AlertSendType sendType : AlertSendType.values()) {
                AlertData alert = new AlertData(1, "hello", "", null, true, sendType, type, null);

                assertEquals(alert.isValid(), AlertState.from(alert, true).valid());
            }
        }
    }

    @Test
    public void twitchRetryUsesCommittedStateAndRunsOutsideGuildLock() throws java.lang.Exception {
        GuildData guild = guild();
        GuildController failing = new GuildController(cache, id -> discord, (context, id) -> {}, context -> List.of(), id -> {
            assertFalse(Thread.holdsLock(guild));
            assertEquals("live", guild.getTwitchdata(id).getMessage());

            throw new IllegalStateException("Unavailable Twitch");
        });
        Map<?, ?> results = (Map<?, ?>) failing.updateSettings("1", AUTH,
            JSON.readTree("{\"twitch\":{\"100\":{\"message\":\"live\"}}}")).get("twitch");
        Result result = (Result) results.get("100");

        assertEquals("saved_with_errors", result.status());
        assertTrue(result.alert().exists());

        post("{\"twitch\":{\"100\":{}}}");

        assertEquals(List.of("100"), subscriptions);
        assertEquals(1, database.readGuildAlerts("1").size());
    }

    @Test
    public void concurrentBotAndApiUpdatesDoNotLoseFieldsOrDuplicateAlerts() throws java.lang.Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> bot = executor.submit(() -> {
                start.await();

                return cache.withGuildData("1", guild -> {
                    AlertData alert = guild.createAlert(AlertType.LEAVE, null, edit -> {});
                    AlertData edit = alert.deferEdit();
                    edit.setMessage("bot");

                    return edit.update();
                });
            });
            Future<Boolean> api = executor.submit(() -> {
                start.await();

                return result(post("{\"leave\":{\"privateMessage\":\"api\"}}"), "leave").status().equals("ok");
            });

            start.countDown();

            assertTrue(bot.get(10, TimeUnit.SECONDS));
            assertTrue(api.get(10, TimeUnit.SECONDS));

            List<AlertData> stored = database.readGuildAlerts("1");

            assertEquals(1, stored.size());
            assertEquals("bot", stored.get(0).getMessage());
            assertEquals("api", stored.get(0).getPrivateMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void validatesReferencesRequestShapeAndMissingBot() throws java.lang.Exception {
        Map<String, Object> results = post("{\"leave\":{\"channelId\":\"99\"},\"welcome\":{\"roleIds\":[\"99\"]},\"warning\":{},\"boost\":{\"exists\":true}}");
        for (String key : results.keySet())
            assertEquals("error", result(results, key).status());

        assertTrue(database.readGuildAlerts("1").isEmpty());

        for (String body : List.of("null", "[]", "{}", "1")) {
            try {
                post(body);
                fail("Expected invalid request");
            } catch (Exception exception) {
                assertEquals(Type.INVALID_REQUEST, exception.getType());
            }
        }

        try {
            controller.getGuild("2", AUTH);
            fail("Expected missing bot");
        } catch (Exception exception) {
            assertEquals(Type.NOT_FOUND, exception.getType());
        }
    }

    @Test
    public void mvcPreservesPayloadAndChecksAuthorizationOnEveryRequest() throws java.lang.Exception {
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean allowed = new AtomicBoolean(true);
        GuildController secured = new GuildController(cache, id -> discord, (context, id) -> {
            checks.incrementAndGet();
            if (!allowed.get())
                throw new Exception(Type.FORBIDDEN, "guild_forbidden", "Guild access denied");
        }, context -> List.of(), subscriptions::add);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(secured)
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new AuthFilter((key, bearer, required) -> AUTH)).build();

        String response = mvc.perform(get("/api/beebot/guilds/1").servletPath("/api/beebot/guilds/1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> fields = new ArrayList<>();

        for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry : JSON.readTree(response).properties())
            fields.add(entry.getKey());

        assertEquals(List.of("id", "name", "iconUrl", "channels", "roles", "settings"), fields);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/beebot/guilds/1/settings").servletPath("/api/beebot/guilds/1/settings")
            .contentType(MediaType.APPLICATION_JSON).content("{\"leave\":{}}"))
            .andExpect(status().isOk());
        allowed.set(false);
        mvc.perform(get("/api/beebot/guilds/1").servletPath("/api/beebot/guilds/1"))
            .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/beebot/guilds/1/settings").servletPath("/api/beebot/guilds/1/settings")
            .contentType(MediaType.APPLICATION_JSON).content("{\"leave\":{}}"))
            .andExpect(status().isForbidden());

        assertEquals(4, checks.get());
    }

    @Test
    public void newAlertDraftCanBeSavedAndAddedWithoutReplacingItsInstance() {
        GuildData guild = guild();
        AlertData alert = guild.newAlert(AlertType.LEAVE, null);
        alert.setMessage("new");

        assertNull(guild.getAlert(AlertType.LEAVE));
        assertTrue(alert.update());

        guild.addAlert(alert);

        assertSame(alert, guild.getAlert(AlertType.LEAVE));
        assertTrue(alert.setPrivateMessage("immediate"));
        assertEquals("immediate", database.readGuildAlerts("1").get(0).getPrivateMessage());
    }

    @Test
    public void generalAndBlacklistSqlFailuresLeaveOriginalStateIntact() throws java.lang.Exception {
        post("{\"general\":{\"prefix\":\"!\"}}");

        GuildData guild = guild();
        GuildData edit = guild.deferEdit();

        edit.setPrefix("x".repeat(33));
        edit.setExpSystem(false);

        assertFalse(edit.update());
        assertEquals("!", guild.getPrefix());
        assertTrue(guild.isExperienceEnabled());
        assertEquals("!", database.readGuildData(List.of("1")).get(0).get("prefix"));

        BlacklistData blacklist = guild.getBlacklistData().deferEdit();

        blacklist.setThreshold(999);
        blacklist.setBlacklistEnabled(true);

        assertFalse(blacklist.update());
        assertEquals(0, guild.getThreshold());
        assertFalse(guild.blacklistEnabled());
        assertEquals(0, database.readGuildData(List.of("1")).get(0).getAsInt("threshold"));
    }

    @Test
    public void unavailableStorageReturns503InsteadOfEmptySettings() {
        GuildCache unavailable = new GuildCache(new BotDB(() -> null), () -> "!");
        GuildController failing = new GuildController(unavailable, id -> discord, (context, id) -> {}, context -> List.of(), id -> {});

        try {
            failing.getGuild("1", AUTH);
            fail("Expected unavailable storage");
        } catch (Exception exception) {
            assertEquals(Type.UNAVAILABLE, exception.getType());
        }
    }

    /* -------------------------------------------------------------------------- */

    private Map<String, Object> post(String body) throws java.lang.Exception {
        return controller.updateSettings("1", AUTH, JSON.readTree(body));
    }

    private Result result(Map<String, Object> results, String key) {
        return (Result) results.get(key);
    }

    private GuildData guild() {
        return cache.withGuildData("1", guild -> guild);
    }

    private Connection connection() {
        connections.incrementAndGet();

        try {
            return DriverManager.getConnection(url);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
            (instance, method, arguments) -> values.get(method.getName()));
    }
}
