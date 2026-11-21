package com.safjnest.bot.audio;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.config.ServiceSettings;
import com.safjnest.database.R2;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;

public class SoundFilesTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void inspectsMp3AndOpusLikeThePlayerAndRejectsInvalidFiles() throws IOException {
        SoundFiles files = SoundFiles.getInstance();
        assertEquals(2088, files.inspectDuration(Path.of("rsc/sounds/10.mp3"), "mp3"));
        assertEquals(29024, files.inspectDuration(Path.of("rsc/sounds/100.opus"), "opus"));
        assertThrows(IOException.class, () -> files.inspectDuration(Path.of("rsc/sounds/88.wav"), "mp3"));

        Path broken = temporary.newFile("broken.mp3").toPath();
        Files.writeString(broken, "not audio");
        assertThrows(IOException.class, () -> files.inspectDuration(broken, "mp3"));
    }

    @Test
    public void sharedUploadMeasuresBeforeWritingAndPublishesUsingTheGeneratedId() throws IOException {
        Path directory = temporary.newFolder().toPath();
        SoundFiles files = new SoundFiles(directory, new R2(null), null, "sounds/",
            (key, target) -> fail("A local upload should not download a file"));
        BotDB database = new BotDB() {
            @Override
            public String insertSound(String name, String description, String guildId, String userId, String extension, boolean isPublic,
                    long duration, List<String> tags, Function<Sound, String> store) {
                assertEquals(List.of("arcade"), tags);
                assertEquals(2088, duration);
                assertEquals("Ping", name);
                assertEquals("A ping", description);

                QueryRecord row = new QueryRecord();
                row.put("id", "42");
                row.put("guild_id", guildId);
                row.put("user_id", userId);
                row.put("extension", extension);
                row.put("name", name);
                row.put("duration_ms", duration);

                assertNull(store.apply(new Sound(row, List.of())));

                return "42";
            }
        };
        Path fixture = Path.of("rsc/sounds/10.mp3");

        try (java.io.InputStream source = Files.newInputStream(fixture)) {
            assertEquals("42", files.create(database, "Ping", "A ping", "1", "2", "mp3", true, List.of("arcade"), source));
        }

        assertEquals(-1, Files.mismatch(fixture, directory.resolve("42.mp3")));
    }

    @Test
    public void objectMetadataKeepsUnicodeTagsAndAllCatalogColumns() throws IOException {
        QueryRecord row = new QueryRecord();
        row.put("id", "10");
        row.put("user_id", "2");
        row.put("guild_id", "1");
        row.put("name", "L'été 🎵");
        row.put("description", "Recorded in été 🎵");
        row.put("extension", "mp3");
        row.put("public", true);
        row.put("time", java.sql.Timestamp.from(java.time.Instant.parse("2025-01-02T03:04:05Z")));
        row.put("duration_ms", 2088);
        row.put("plays", 15);
        row.put("likes", 2);
        row.put("dislikes", 1);
        List<Tag> tags = List.of(new Tag(11, "caffè"), new Tag());
        Map<String, String> metadata = SoundFiles.metadata(new Sound(row, tags));

        assertEquals("2088", metadata.get("duration-ms"));
        assertEquals("10", metadata.get("id"));
        assertEquals("2", metadata.get("user-id"));
        assertEquals("1", metadata.get("guild-id"));
        assertEquals("mp3", metadata.get("extension"));
        assertEquals("true", metadata.get("public"));
        assertEquals("2025-01-02T03:04:05Z", metadata.get("created-at"));
        assertEquals("15", metadata.get("plays"));
        assertEquals("2", metadata.get("likes"));
        assertEquals("1", metadata.get("dislikes"));
        assertEquals("L'été 🎵", new String(Base64.getUrlDecoder().decode(metadata.get("name-base64")), StandardCharsets.UTF_8));
        assertEquals("Recorded in été 🎵",
            new String(Base64.getUrlDecoder().decode(metadata.get("description-base64")), StandardCharsets.UTF_8));
        byte[] encoded = Base64.getUrlDecoder().decode(metadata.get("tags-gzip-base64"));
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(encoded))) {
            ObjectMapper json = new ObjectMapper();
            assertEquals(json.readTree("[{\"id\":\"11\",\"name\":\"caffè\"}]"), json.readTree(input));
        }
    }

    @Test
    public void concurrentRequestsShareOneCompletedDownload() throws java.lang.Exception {
        Path directory = temporary.newFolder().toPath();
        Path target = directory.resolve("41.opus");
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        String cdn = "https://cdn.example/";
        SoundFiles files = new SoundFiles(directory, new R2(URI.create(cdn)), null, "sounds/", (key, partial) -> {
            assertEquals("sounds/41/41.opus", key);
            requests.incrementAndGet();
            Files.writeString(partial, "a");
            started.countDown();

            try {
                if (!finish.await(5, TimeUnit.SECONDS))
                    throw new IOException("Timed out waiting for the second byte");

                Files.writeString(partial, "ab");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            }
        });

        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Path> first = worker.submit(() -> files.resolve("41", "opus"));
            Future<Path> second = worker.submit(() -> files.resolve("41", "opus"));

            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertFalse(Files.exists(target));
            } finally {
                finish.countDown();
            }

            assertEquals(target, first.get(5, TimeUnit.SECONDS));
            assertEquals(target, second.get(5, TimeUnit.SECONDS));
            assertEquals("ab", Files.readString(target));
            assertEquals(target, files.resolve("41", "opus"));
            assertEquals(1, requests.get());
            assertEquals(cdn + "sounds/41/41.opus", files.playbackUrl("41", "opus", "https://old.example"));
        }
    }

    @Test
    public void storedFlatAudioKeyDoesNotAffectBotDownloadsOrCdnUrls() throws IOException {
        Path directory = temporary.newFolder().toPath();
        R2 storage = new R2(URI.create("https://cdn.example/"));
        SoundFiles files = new SoundFiles(directory, storage, "beebot", "sounds/", (key, target) -> {
            assertEquals("sounds/1/1.mp3", key);
            Files.writeString(target, "audio");
        });
        QueryRecord row = new QueryRecord();
        row.put("id", "1");
        row.put("extension", "mp3");
        row.put("url", "sounds/1.mp3");

        assertEquals("audio", Files.readString(files.resolve(new Sound(row, List.of()))));
        assertEquals("https://cdn.example/sounds/1/1.mp3", files.playbackUrl("1", "mp3", null));
    }

    @Test
    public void audioAndCoversShareTheSoundDirectory() {
        SoundFiles files = new SoundFiles(temporary.getRoot().toPath(), new R2(URI.create("https://cdn.example/")),
            "beebot", "sounds/", (key, target) -> fail("Key generation should not download a file"));

        assertEquals("sounds/1/1.mp3", files.audioKey("1", "mp3"));

        assertTrue(files.isSoundImageKey("1", "sounds/1/123.jpg"));
        assertTrue(files.isSoundImageKey("1", "sounds/1/thumbnail-cover.png"));
        assertFalse(files.isSoundImageKey("1", "sounds/1/1.mp3"));
        assertFalse(files.isSoundImageKey("1", "sounds/11/123.jpg"));
        assertEquals("https://cdn.example/sounds/1/123.jpg", files.imageUrl("1", "sounds/1/123.jpg"));
        assertNull(files.imageUrl("1", "soundboard/thumbnail/2/cover.png"));
    }

    @Test
    public void failedDownloadLeavesNoFileAndCanBeRetried() throws IOException {
        Path directory = temporary.newFolder().toPath();
        AtomicInteger requests = new AtomicInteger();
        R2 storage = new R2(URI.create("https://cdn.example/"));
        SoundFiles files = new SoundFiles(directory, storage, null, "", (key, partial) -> {
            assertEquals("41/41.mp3", key);
            if (requests.incrementAndGet() == 1) {
                Files.writeString(partial, "partial audio");
                throw new IOException("CDN unavailable");
            }

            Files.writeString(partial, "audio");
        });

        assertThrows(IOException.class, () -> files.resolve("41", "mp3"));
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }

        assertEquals("audio", Files.readString(files.resolve("41", "mp3")));
        assertEquals(2, requests.get());
    }

    @Test
    public void jsonSettingsReadUploadCredentialsWithoutSerializingThem() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ServiceSettings settings = mapper.readValue("""
            {"r2":{"enabled":true,"cdnUrl":"https://cdn.example","accessKeyId":"test-key","secretAccessKey":"test-secret"}}
            """, ServiceSettings.class);

        assertTrue(settings.getR2().enabled());
        assertEquals("https://cdn.example", settings.getR2().cdnUrl());
        assertEquals("test-key", settings.getR2().accessKeyId());
        assertEquals("test-secret", settings.getR2().secretAccessKey());
        assertFalse(mapper.valueToTree(settings.getR2()).has("secretAccessKey"));
        assertFalse(settings.getR2().toString().contains("test-key"));
    }
}
