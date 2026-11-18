package com.safjnest.bot.audio;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.bot.model.BotSettings.JsonSettings;
import com.safjnest.database.R2;

public class SoundFilesTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void concurrentRequestsShareOneCompletedDownload() throws java.lang.Exception {
        Path directory = temporary.newFolder().toPath();
        Path target = directory.resolve("41.opus");
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        String cdn = "https://cdn.example/";
        SoundFiles files = new SoundFiles(directory, new R2(URI.create(cdn)), null, "sounds/", (key, partial) -> {
            assertEquals("sounds/41.opus", key);
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
            assertEquals(cdn + "sounds/41.opus", files.playbackUrl("41", "opus", "https://old.example"));
        }
    }

    @Test
    public void failedDownloadLeavesNoFileAndCanBeRetried() throws IOException {
        Path directory = temporary.newFolder().toPath();
        AtomicInteger requests = new AtomicInteger();
        R2 storage = new R2(URI.create("https://cdn.example/"));
        SoundFiles files = new SoundFiles(directory, storage, null, "", (key, partial) -> {
            assertEquals("41.mp3", key);
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
        JsonSettings settings = mapper.readValue("""
            {"r2":{"enabled":true,"cdnUrl":"https://cdn.example","accessKeyId":"test-key","secretAccessKey":"test-secret"}}
            """, JsonSettings.class);

        assertTrue(settings.getR2().enabled());
        assertEquals("https://cdn.example", settings.getR2().cdnUrl());
        assertEquals("test-key", settings.getR2().accessKeyId());
        assertEquals("test-secret", settings.getR2().secretAccessKey());
        assertFalse(mapper.valueToTree(settings.getR2()).has("secretAccessKey"));
        assertFalse(settings.getR2().toString().contains("test-key"));
    }
}
