package com.safjnest.sound;

import com.safjnest.discord.auth.DiscordApi;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SoundPlay;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;

import org.junit.Test;

public class SoundAnalyticsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant FROM = Instant.parse("2026-03-25T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");
    private static final Set<String> MEMBERSHIPS = new LinkedHashSet<>(List.of("1", "3"));

    @Test
    public void resolvesSourcesThroughTheCanonicalAudioType() {
        for (AudioType source : AudioType.values())
            assertSame(source, AudioType.getFromOrdinal(source.ordinal()));

        assertNull(AudioType.getFromOrdinal(-1));
        assertNull(AudioType.getFromOrdinal(AudioType.values().length));
        assertNull(AudioType.getFromOrdinal(Integer.MAX_VALUE));
    }

    @Test
    public void cursorBindsTheCallerScopeAndOriginalInterval() {
        Instant eventTime = FROM.plusSeconds(3600);
        String cursor = period("UTC").cursor("20", eventTime, "10", "2", null);
        SoundPeriod parsed = SoundPeriod.of("7d", "UTC", cursor, "10", "2", null);

        assertEquals(TO, parsed.to());
        assertEquals(FROM, parsed.from());
        assertEquals(eventTime, parsed.before());
        assertEquals("20", parsed.beforeId());
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "UTC", cursor, "11", "2", null));
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "UTC", cursor, "10", "3", null));
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "UTC", cursor, "10", "2", "1"));
        assertThrows(Exception.class, () -> SoundPeriod.of("30d", "UTC", cursor, "10", "2", null));
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "Europe/Rome", cursor, "10", "2", null));
    }

    @Test
    public void rejectsInvalidPeriodTimezoneAndCursor() {
        assertThrows(Exception.class, () -> SoundPeriod.of("1d", "UTC", null, "10", "2", null));
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "invalid-zone", null, "10", "2", null));
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "UTC", "!", "10", "2", null));
        assertThrows(Exception.class, () -> SoundPeriod.of("7d", "UTC", "a".repeat(2049), "10", "2", null));
    }

    @Test
    public void preservesCompleteResponsesAcrossScopesAndTimezones() throws java.lang.Exception {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("UTC:summary", "f79d1ab1df5da5c5a88b8922e1da85abed6238f2d34f6487b9c0dd3e097b46f6"),
            Map.entry("UTC:guild", "df9a1a0cca38d4b628c80a8a09a23f3d835297c169c64e2312027edf92a7c279"),
            Map.entry("UTC:sound", "1dbcbef974f71586102dd3a229f0f71aef40d51bf4ae316c6d13659f316670d3"),
            Map.entry("UTC:mine", "21819c84743e07dd94e70cf3069a6eaf3b5f966af3ee363c0f484a8df35cbe6c"),
            Map.entry("Europe/Rome:summary", "f79d1ab1df5da5c5a88b8922e1da85abed6238f2d34f6487b9c0dd3e097b46f6"),
            Map.entry("Europe/Rome:guild", "03d5de264822411170a43dac6326fbf52b0b2f29d425e698b35b0a093d480449"),
            Map.entry("Europe/Rome:sound", "84f74e469bf4b2eefd3411c44bddefa5d03205c757742b6ddf3063f4e5d179d3"),
            Map.entry("Europe/Rome:mine", "4401e07abc5a56411a56d6432275a14967b250a3c4d96baa0d54090765390787"),
            Map.entry("Asia/Kathmandu:summary", "f79d1ab1df5da5c5a88b8922e1da85abed6238f2d34f6487b9c0dd3e097b46f6"),
            Map.entry("Asia/Kathmandu:guild", "a47c77bee0e6782f906dd71fef88e72534ec5a80923692515ee154f82a50c82c"),
            Map.entry("Asia/Kathmandu:sound", "337352e3128c2f19edc603ab72bafa834752d0309d9d76bbed5002639ae91f84"),
            Map.entry("Asia/Kathmandu:mine", "68ce6b09568d7c24e4d6c8363482fb85066179d9125ae406ab10d07f5af74d11"),
            Map.entry("Australia/Lord_Howe:summary", "f79d1ab1df5da5c5a88b8922e1da85abed6238f2d34f6487b9c0dd3e097b46f6"),
            Map.entry("Australia/Lord_Howe:guild", "f318120742ff42b4353e35c105bcfe65fa85a144d28daeae0187bf5b81f6b97e"),
            Map.entry("Australia/Lord_Howe:sound", "c0ba1038ed959d147ce6509588229db534f0a9bcd2f4932f6f0c88d9917ad69b"),
            Map.entry("Australia/Lord_Howe:mine", "5edacff0f21b610d8c2a5419b9f3bcde59717a41827c0439f6b81eb8cc90eeba")
        );
        Map<String, String> actual = new LinkedHashMap<>();

        for (String zone : List.of("UTC", "Europe/Rome", "Asia/Kathmandu", "Australia/Lord_Howe")) {
            Fixture fixture = fixture(plays());
            SoundPeriod period = period(zone);

            actual.put(zone + ":summary", digest(fixture.analytics.getSummary(sounds(), "2", null, period)));
            actual.put(zone + ":guild", digest(fixture.analytics.getGuildAnalytics(sounds(), "2", "1", period, MEMBERSHIPS)));
            actual.put(zone + ":sound", digest(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period, MEMBERSHIPS)));
            actual.put(zone + ":mine", digest(fixture.analytics.getUserAnalytics(sounds(), "2", period, MEMBERSHIPS)));
            assertEquals(4, fixture.database.reads);
        }

        assertEquals(expected, actual);
    }

    @Test
    public void preservesPrivacyAndDeduplicatesListenersAcrossGuilds() {
        Fixture fixture = fixture(plays());
        JsonNode response = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period("UTC"), MEMBERSHIPS));

        assertEquals(6, response.get("totals").get("plays").asLong());
        assertEquals(3, response.get("totals").get("listeners").asInt());
        assertEquals(Set.of("1", "3"), fieldNames(response.get("guilds")));
        assertEquals(2, response.get("otherGuilds").get("plays").asLong());
        assertFalse(response.get("otherGuilds").has("users"));
        assertEquals(3, response.get("my").get("history").size());
        assertEquals(1, fixture.database.reads);
    }

    @Test
    public void cursorUsesNumericTieBreakingAndKeepsFullIntervalTotals() {
        List<SoundPlay> plays = new ArrayList<>();
        Instant time = Instant.parse("2026-03-28T12:00:00Z");

        for (int id = 1000; id < 1105; id++)
            plays.add(new SoundPlay(String.valueOf(id), "10", "2", id % 2 == 0 ? "9" : "1", time, 1));

        Collections.shuffle(plays, new Random(7));
        Fixture fixture = fixture(plays);
        JsonNode first = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period("UTC"), MEMBERSHIPS));
        JsonNode history = first.get("my").get("history");

        assertEquals(100, history.size());
        assertEquals("1104", history.get(0).get("id").asText());
        assertEquals("1005", history.get(99).get("id").asText());
        assertTrue(history.get(0).get("guildId").isNull());
        assertEquals(105, first.get("my").get("plays").asLong());

        String cursor = first.get("my").get("nextCursor").asText();
        SoundPeriod next = SoundPeriod.of("7d", "UTC", cursor, "10", "2", null);
        JsonNode second = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, next, MEMBERSHIPS));

        assertEquals(5, second.get("my").get("history").size());
        assertEquals("1004", second.get("my").get("history").get(0).get("id").asText());
        assertEquals(105, second.get("totals").get("plays").asLong());
        assertEquals(105, second.get("my").get("plays").asLong());
        assertTrue(second.get("my").get("nextCursor").isNull());
    }

    @Test
    public void excludesPreviousIntervalFromCurrentCountsAndHandlesAllTime() {
        Fixture fixture = fixture(plays());
        JsonNode summary = json(fixture.analytics.getSummary(sounds(), "2", null, period("UTC")));
        assertEquals(8, summary.get("plays").asLong());
        assertEquals(2, summary.get("previousPlays").asLong());
        assertEquals(300.0, summary.get("playsChangePercent").asDouble(), 0.0);

        SoundPeriod all = new SoundPeriod("all", ZoneId.of("UTC"), null, TO, null, null);
        JsonNode response = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, all, MEMBERSHIPS));
        assertTrue(response.get("from").isNull());
        assertTrue(response.get("totals").get("previousPlays").isNull());
        assertTrue(response.get("totals").get("playsChangePercent").isNull());
        assertEquals("2026-03-20", response.get("totals").get("playsPerDay").get(0).get("date").asText());
    }

    @Test
    public void anonymousWebPlaysOnlyAffectPlaybackTotals() {
        String time = "2026-03-28T12:00:00Z";
        Fixture fixture = fixture(List.of(
            play("1", "10", null, null, time, AudioType.WEB.ordinal()),
            play("2", "10", "2", null, time, AudioType.WEB.ordinal()),
            play("3", "10", "2", "1", time, AudioType.SOUND.ordinal())
        ));
        JsonNode analytics = json(fixture.analytics.getSoundAnalytics(sounds().getFirst(), "2", null, period("UTC"), MEMBERSHIPS));
        JsonNode totals = analytics.get("totals");

        assertEquals(3, totals.get("plays").asLong());
        assertEquals(1, totals.get("listeners").asLong());
        assertEquals(2.0, totals.get("averagePlaysPerListener").asDouble(), 0.0);
        assertEquals(2, analytics.get("my").get("plays").asLong());
        assertEquals(0, analytics.get("otherGuilds").get("plays").asLong());
        boolean foundWeb = false;
        for (JsonNode source : totals.get("sources")) {
            if ("WEB".equals(source.get("source").asText())) {
                assertEquals(2, source.get("plays").asLong());
                foundWeb = true;
            }
        }

        assertTrue(foundWeb);
    }

    @Test
    public void fillsDailySeriesAndHeatmapAcrossDstAndQuarterHourZones() {
        Fixture fixture = fixture(plays());
        JsonNode rome = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period("Europe/Rome"), MEMBERSHIPS));
        JsonNode heatmap = rome.get("totals").get("heatmap");
        assertEquals(168, heatmap.size());
        assertEquals(0, heatmap.get((7 - 1) * 24 + 2).get("plays").asLong());
        assertEquals(1, heatmap.get((7 - 1) * 24 + 3).get("plays").asLong());
        assertEquals(8, rome.get("totals").get("playsPerDay").size());

        JsonNode kathmandu = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period("Asia/Kathmandu"), MEMBERSHIPS));
        assertEquals(1, kathmandu.get("totals").get("heatmap").get((6 - 1) * 24 + 5).get("plays").asLong());
    }

    @Test
    public void replayDistributionUsesEachListenersCombinedPlayCount() {
        List<SoundPlay> plays = new ArrayList<>();
        int id = 1;
        for (int count : List.of(1, 2, 6, 21, 51)) {
            for (int index = 0; index < count; index++) {
                plays.add(new SoundPlay(String.valueOf(id++), "10", "user-" + count,
                    index % 2 == 0 ? "1" : "3", FROM.plusSeconds(3600), 0));
            }
        }

        Fixture fixture = fixture(plays);
        JsonNode result = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period("UTC"), MEMBERSHIPS));
        for (JsonNode bucket : result.get("totals").get("replayDistribution"))
            assertEquals(1, bucket.get("users").asLong());

        assertTrue(result.get("totals").get("replayDistribution").get(4).get("max").isNull());
    }

    @Test
    public void emptyCollectionsAndUnknownSourcesKeepTheirContract() {
        Fixture fixture = fixture(List.of());
        JsonNode result = json(fixture.analytics.getUserAnalytics(List.of(), "2", period("UTC"), MEMBERSHIPS));
        assertEquals(0, result.get("summary").get("plays").asLong());
        assertTrue(result.get("sounds").isEmpty());
        assertTrue(result.get("summary").get("playsChangePercent").isNull());

        fixture = fixture(plays());
        result = json(fixture.analytics.getSoundAnalytics(sounds().get(0), "2", null, period("UTC"), MEMBERSHIPS));
        boolean unknown = false;
        for (JsonNode source : result.get("totals").get("sources")) {
            if (source.get("source").asText().equals("UNKNOWN"))
                unknown = true;
        }
        assertTrue(unknown);
        assertEquals(List.of("soundId", "period", "timezone", "from", "to", "totals", "guilds", "otherGuilds", "my"),
            new ArrayList<>(fieldNames(result)));
    }

    /* -------------------------------------------------------------------------- */

    private Fixture fixture(List<SoundPlay> plays) {
        RecordingDatabase database = new RecordingDatabase(plays);
        DiscordApi access = new DiscordApi() {
            @Override
            public Map<String, Object> user(String id) {
                return Map.of("id", id, "name", "user " + id);
            }
        };

        return new Fixture(new SoundAnalytics(database, access), database);
    }

    private List<QueryRecord> sounds() {
        List<QueryRecord> sounds = new ArrayList<>();
        for (int id : List.of(10, 11, 12)) {
            QueryRecord row = new QueryRecord();
            row.put("id", String.valueOf(id));
            row.put("likes", id - 9);
            row.put("dislikes", id - 10);
            sounds.add(row);
        }
        return sounds;
    }

    private List<SoundPlay> plays() {
        return List.of(
            play("1", "10", "2", "1", "2026-03-28T00:14:59Z", 0),
            play("2", "10", "3", "1", "2026-03-28T23:30:00Z", 1),
            play("3", "10", "2", "3", "2026-03-29T00:30:00Z", 2),
            play("4", "10", "2", "3", "2026-03-29T01:30:00Z", 2),
            play("5", "10", "4", "9", "2026-03-29T03:30:00Z", 99),
            play("6", "10", "3", "9", "2026-03-29T03:45:00Z", 3),
            play("7", "10", "2", "1", "2026-03-20T12:00:00Z", 0),
            play("8", "11", "2", "1", "2026-03-28T01:00:00Z", 1),
            play("9", "11", "3", "3", "2026-03-29T00:00:00Z", 1),
            play("10", "11", "4", "9", "2026-03-20T13:00:00Z", 0)
        );
    }

    private SoundPlay play(String id, String sound, String user, String guild, String time, int source) {
        return new SoundPlay(id, sound, user, guild, Instant.parse(time), source);
    }

    private SoundPeriod period(String zone) {
        return new SoundPeriod("7d", ZoneId.of(zone), FROM, TO, null, null);
    }

    private JsonNode json(Object value) {
        return JSON.valueToTree(value);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String digest(Object value) throws java.lang.Exception {
        JsonNode tree = json(value);
        byte[] normalized = JSON.writeValueAsString(normalize(tree)).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
    }

    private Object normalize(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> values = new TreeMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), normalize(entry.getValue())));
            return values;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode element : node)
                values.add(normalize(element));
            return values;
        }
        return node;
    }

    private record Fixture(SoundAnalytics analytics, RecordingDatabase database) {}

    private static final class RecordingDatabase extends BotDB {
        private final List<SoundPlay> plays;
        private int reads;

        private RecordingDatabase(List<SoundPlay> plays) {
            super(() -> { throw new AssertionError("Unexpected external storage access"); });
            this.plays = plays;
        }

        @Override
        public void forEachSoundPlay(java.util.Collection<String> ids, String guildId, Instant from, Instant to,
                Consumer<SoundPlay> consumer) {
            reads++;
            for (SoundPlay play : plays) {
                if (ids.contains(play.soundId()) && (guildId == null || guildId.equals(play.guildId()))
                        && (from == null || !play.time().isBefore(from)) && play.time().isBefore(to))
                    consumer.accept(play);
            }
        }
    }
}
