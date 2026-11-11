package com.safjnest.guild;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.auth.AuthException;
import com.safjnest.model.guild.GuildSettings;
import com.safjnest.model.guild.GuildSettings.AlertState;
import com.safjnest.model.guild.GuildSettings.Result;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.sql.database.GuildSettingsDB;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.junit.Before;
import org.junit.Test;

public class GuildSettingsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private GuildSettingsDB database;
    private GuildService service;
    private String url;
    private final AtomicInteger invalidations = new AtomicInteger();
    private final List<String> subscriptions = new ArrayList<>();

    @Before
    public void setup() throws Exception {
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
        database = new GuildSettingsDB(this::connection);
        GuildChannel channel = proxy(GuildChannel.class, Map.of("getId", "10", "getName", "general", "getType", ChannelType.TEXT));
        Role role = proxy(Role.class, Map.of("getId", "20", "getName", "member", "getColorRaw", 0, "getPosition", 1, "isManaged", false, "isPublicRole", false));
        Member self = proxy(SelfMember.class, Map.of("hasPermission", true, "canInteract", true));
        Guild guild = proxy(Guild.class, Map.of("getId", "1", "getName", "Test guild", "getChannels", List.of(channel), "getRoles", List.of(role), "getSelfMember", self));
        service = new GuildService(database, id -> id.equals("1") ? guild : null,
            id -> invalidations.incrementAndGet(), subscriptions::add);
    }

    @Test
    public void returnsMissingAlertsWithoutCreatingRows() {
        GuildSettings guild = service.getGuild("1");
        AlertState leave = (AlertState) guild.settings().get("leave");
        assertFalse(leave.exists());
        assertFalse(leave.enabled());
        assertFalse(leave.valid());
        assertTrue(leave.problems().isEmpty());
        assertTrue(database.load("1").guild().isEmpty());
        assertEquals("10", guild.channels().get(0).get("id"));
        assertEquals("20", guild.roles().get(0).get("id"));
        assertEquals(true, guild.roles().get(0).get("assignable"));
    }

    @Test
    public void exposesChannelStructureAndRoleAssignability() {
        GuildChannel category = proxy(IPositionableChannel.class, Map.of("getId", "30", "getName", "Group", "getType", ChannelType.CATEGORY, "getPositionRaw", 2));
        GuildChannel channel = proxy(ICategorizableChannel.class, Map.of("getId", "31", "getName", "chat", "getType", ChannelType.TEXT, "getPositionRaw", 4, "getParentCategoryId", "30"));
        Role managed = proxy(Role.class, Map.of("getId", "21", "getName", "managed", "getColorRaw", 0, "getPosition", 2, "isManaged", true));
        Role everyone = proxy(Role.class, Map.of("getId", "1", "getName", "everyone", "getColorRaw", 0, "getPosition", 0, "isManaged", false, "isPublicRole", true));
        Role high = proxy(Role.class, Map.of("getId", "22", "getName", "high", "getColorRaw", 0, "getPosition", 3, "isManaged", false, "isPublicRole", false));
        Member self = proxy(SelfMember.class, Map.of("hasPermission", true, "canInteract", false));
        Guild guild = proxy(Guild.class, Map.of("getId", "1", "getName", "Test", "getChannels", List.of(category, channel), "getRoles", List.of(managed, everyone, high), "getSelfMember", self));
        GuildSettings result = new GuildService(database, id -> guild, id -> {}, id -> {}).getGuild("1");
        assertEquals("CATEGORY", result.channels().get(0).get("type"));
        assertNull(result.channels().get(0).get("parentId"));
        assertEquals(2, result.channels().get(0).get("position"));
        assertEquals("30", result.channels().get(1).get("parentId"));
        assertEquals(4, result.channels().get(1).get("position"));
        for (Map<String, Object> role : result.roles()) assertEquals(false, role.get("assignable"));
    }

    @Test
    public void savesIncompleteAlertAndReturnsSameStateFromGet() throws Exception {
        Result result = result(post("{\"leave\":{\"message\":\"Ciao!\"}}"), "leave");
        assertEquals("ok", result.status());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.alert().exists());
        assertTrue(result.alert().enabled());
        assertFalse(result.alert().valid());
        assertEquals("missing_channel", result.alert().problems().get(0).code());
        assertEquals(result.alert(), service.getGuild("1").settings().get("leave"));
    }

    @Test
    public void absentFieldsRemainAndExplicitNullClears() throws Exception {
        post("{\"leave\":{\"message\":\"it's hello\",\"privateMessage\":\"private\",\"channelId\":\"10\"}}");
        Result result = result(post("{\"leave\":{\"enabled\":false}}"), "leave");
        assertEquals("it's hello", result.alert().message());
        assertEquals("private", result.alert().privateMessage());
        assertEquals("10", result.alert().channelId());
        assertEquals("disabled", result.alert().problems().get(0).code());
        result = result(post("{\"leave\":{\"channelId\":null,\"message\":null,\"privateMessage\":null}}"), "leave");
        assertNull(result.alert().channelId());
        assertEquals("", result.alert().message());
        assertNull(result.alert().privateMessage());
        assertEquals(3, result.alert().problems().size());
    }

    @Test
    public void invalidFieldRejectsWholeBlockButOtherBlocksSave() throws Exception {
        post("{\"leave\":{\"message\":\"original\"}}");
        int before = invalidations.get();
        Map<String, Object> results = post("{\"leave\":{\"message\":\"changed\",\"enabled\":\"yes\"},\"blacklist\":{\"threshold\":5}}");
        Result leave = result(results, "leave");
        assertEquals("error", leave.status());
        assertEquals("enabled", leave.errors().get(0).field());
        assertEquals("original", leave.alert().message());
        assertEquals("ok", result(results, "blacklist").status());
        assertEquals(before + 1, invalidations.get());
        assertEquals(5, database.load("1").guild().getAsInt("threshold"));
    }

    @Test
    public void collectionsUpdateIndependentlyAndRetainOtherEntries() throws Exception {
        Map<String, Object> results = post("{\"twitch\":{\"100\":{\"message\":\"live\",\"roleId\":\"20\"},\"101\":{\"enabled\":\"yes\"}},\"reward\":{\"5\":{\"roleIds\":[\"20\"],\"temporary\":true},\"6\":{}}}");
        Map<?, ?> twitch = (Map<?, ?>) results.get("twitch");
        assertEquals("ok", ((Result) twitch.get("100")).status());
        assertEquals("error", ((Result) twitch.get("101")).status());
        assertEquals(List.of("100"), subscriptions);
        assertEquals(3, database.load("1").alerts().size());
        post("{\"reward\":{\"5\":{\"message\":\"level up\"}}}");
        Map<?, ?> reward = (Map<?, ?>) service.getGuild("1").settings().get("reward");
        assertEquals(2, reward.size());
        assertTrue(((AlertState) reward.get("5")).temporary());
        assertEquals(List.of("20"), ((AlertState) reward.get("5")).roleIds());
        assertTrue(((AlertState) reward.get("5")).valid());
    }

    @Test
    public void validityMatchesExistingRulesAndFinalGuildExperience() throws Exception {
        for (AlertType type : AlertType.values()) {
            for (AlertSendType sendType : AlertSendType.values()) {
                for (boolean enabled : List.of(true, false)) {
                    for (String message : List.of("", "hello")) {
                        for (String channel : new String[] {null, "10"}) {
                            AlertData alert = new AlertData(1, message, "", channel, enabled, sendType, type, new java.util.HashMap<>());
                            assertEquals(alert.isValid(), AlertState.from(alert, true).valid());
                            assertEquals(!AlertState.from(alert, true).valid(), !alert.getProblems(true).isEmpty());
                        }
                    }
                }
            }
        }
        Result level = result(post("{\"levelUp\":{\"message\":\"hello\"},\"general\":{\"experienceEnabled\":false}}"), "levelUp");
        assertEquals("experience_disabled", level.alert().problems().get(0).code());
        assertEquals(level.alert(), service.getGuild("1").settings().get("levelUp"));
        Result leave = result(post("{\"leave\":{\"privateMessage\":\"hello\",\"sendType\":\"PRIVATE\"}}"), "leave");
        assertTrue(leave.alert().valid());
    }

    @Test
    public void sqlFailureRollsBackParentAndChildren() {
        database.saveAlert("1", AlertType.WELCOME, null, Map.of("message", "before", "roleIds", List.of("20")));
        try {
            database.saveAlert("1", AlertType.WELCOME, null,
                Map.of("message", "after", "roleIds", List.of("123456789012345678901")));
            fail("Expected rollback");
        } catch (IllegalStateException expected) { }
        AlertData alert = database.load("1").alerts().get(0);
        assertEquals("before", alert.getMessage());
        assertArrayEquals(new String[] {"20"}, alert.getRolesAsArray());
        try {
            database.saveAlert("1", AlertType.TWITCH, "100",
                Map.of("roleId", "123456789012345678901"));
            fail("Expected rollback");
        } catch (IllegalStateException expected) { }
        assertEquals(1, database.load("1").alerts().size());
    }

    @Test
    public void updatesGeneralBlacklistAndChannelSettings() throws Exception {
        post("{\"general\":{\"prefix\":\"?\",\"leagueShard\":\"EUW1\",\"voice\":\"John\"},\"blacklist\":{\"enabled\":true,\"channelId\":\"10\"},\"channels\":{\"10\":{\"experienceModifier\":1.5,\"statisticsEnabled\":false}}}");
        Map<String, Object> settings = service.getGuild("1").settings();
        assertEquals("?", ((Map<?, ?>) settings.get("general")).get("prefix"));
        assertEquals("10", ((Map<?, ?>) settings.get("blacklist")).get("channelId"));
        Map<?, ?> channel = (Map<?, ?>) ((Map<?, ?>) settings.get("channels")).get("10");
        assertEquals(1.5, channel.get("experienceModifier"));
        assertEquals(false, channel.get("statisticsEnabled"));
        assertEquals(true, channel.get("experienceEnabled"));
        post("{\"channels\":{\"10\":{\"experienceEnabled\":false}}}");
        assertEquals(1, database.load("1").channels().size());
    }

    @Test
    public void validatesReferencesReadOnlyFieldsAndRequestShape() throws Exception {
        Map<String, Object> results = post("{\"leave\":{\"channelId\":\"99\"},\"welcome\":{\"roleIds\":[\"99\"]},\"warning\":{},\"boost\":{\"exists\":true}}");
        for (String key : results.keySet()) assertEquals("error", result(results, key).status());
        assertTrue(database.load("1").alerts().isEmpty());
        for (String body : List.of("null", "[]", "{}", "1")) {
            try { post(body); fail("Expected invalid request"); }
            catch (AuthException exception) { assertEquals(400, exception.getStatusCode().value()); }
        }
        try { service.getGuild("2"); fail("Expected absent bot"); }
        catch (AuthException exception) { assertEquals(404, exception.getStatusCode().value()); }
        try { service.getGuild("invalid"); fail("Expected invalid ID"); }
        catch (AuthException exception) { assertEquals(400, exception.getStatusCode().value()); }
    }

    @Test
    public void dependencyFailureDoesNotMasqueradeAsMissingConfiguration() {
        GuildSettingsDB unavailable = new GuildSettingsDB(() -> null);
        try { unavailable.load("1"); fail("Expected unavailable storage"); }
        catch (IllegalStateException expected) { }
    }

    @Test
    public void storageFailureReportsErrorAndKeepsOtherBlocksIndependent() throws Exception {
        post("{\"leave\":{\"message\":\"before\"}}");
        GuildSettingsDB failing = new GuildSettingsDB(this::connection) {
            @Override
            public AlertData saveAlert(String guildId, AlertType type, String key, Map<String, Object> values) {
                throw new IllegalStateException("Simulated failure");
            }
        };
        Guild guild = proxy(Guild.class, Map.of("getId", "1", "getChannels", List.of(), "getRoles", List.of()));
        GuildService failingService = new GuildService(failing, id -> guild,
            id -> invalidations.incrementAndGet(), subscriptions::add);
        int before = invalidations.get();
        Map<String, Object> results = failingService.updateSettings("1",
            JSON.readTree("{\"leave\":{\"message\":\"after\"},\"blacklist\":{\"threshold\":5}}"));
        assertEquals("storage_error", result(results, "leave").errors().get(0).code());
        assertEquals("before", result(results, "leave").alert().message());
        assertEquals("ok", result(results, "blacklist").status());
        assertEquals(before + 1, invalidations.get());
    }

    @Test
    public void twitchRegistrationFailureReportsCommittedStateAndCanBeRetried() throws Exception {
        Guild guild = proxy(Guild.class, Map.of("getId", "1", "getChannels", List.of(), "getRoles", List.of()));
        GuildService failingService = new GuildService(database, id -> guild,
            id -> invalidations.incrementAndGet(), id -> { throw new IllegalStateException("Unavailable Twitch"); });
        Map<?, ?> twitch = (Map<?, ?>) failingService.updateSettings("1",
            JSON.readTree("{\"twitch\":{\"100\":{\"message\":\"live\"}}}")).get("twitch");
        Result result = (Result) twitch.get("100");
        assertEquals("saved_with_errors", result.status());
        assertTrue(result.alert().exists());
        assertEquals("live", database.load("1").alerts().get(0).getMessage());
        post("{\"twitch\":{\"100\":{}}}");
        assertEquals(List.of("100"), subscriptions);
        assertEquals(1, database.load("1").alerts().size());
    }

    @Test
    public void concurrentPartialUpdatesDoNotOverwriteOmittedFieldsOrDuplicateAlerts() throws Exception {
        database.saveAlert("1", AlertType.LEAVE, null, Map.of());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                database.saveAlert("1", AlertType.LEAVE, null, Map.of("message", "public"));
                return true;
            });
            var second = executor.submit(() -> {
                start.await();
                database.saveAlert("1", AlertType.LEAVE, null, Map.of("privateMessage", "private"));
                return true;
            });
            start.countDown();
            assertTrue(first.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            List<AlertData> alerts = database.load("1").alerts();
            assertEquals(1, alerts.size());
            assertEquals("public", alerts.get(0).getMessage());
            assertEquals("private", alerts.get(0).getPrivateMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> post(String json) throws Exception {
        return service.updateSettings("1", JSON.readTree(json));
    }

    private Result result(Map<String, Object> results, String key) {
        return (Result) results.get(key);
    }

    private Connection connection() {
        try { return DriverManager.getConnection(url); }
        catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (instance, method, arguments) -> values.get(method.getName()));
    }
}
