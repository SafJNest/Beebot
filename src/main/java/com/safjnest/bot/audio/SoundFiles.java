package com.safjnest.bot.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.bot.model.BotSettings.R2Settings;
import com.safjnest.database.R2;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.ValidationUtils;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.source.local.LocalSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public final class SoundFiles {

    private static final Logger LOG = LoggerFactory.getLogger(SoundFiles.class);
    private static final Path LEGACY_DIRECTORY = Path.of("rsc", "sounds");
    private static final String DEFAULT_PREFIX = "sounds/";
    private static final String UNAVAILABLE = "Sound file is unavailable.";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private static volatile SoundFiles instance = new SoundFiles(LEGACY_DIRECTORY, R2.getInstance(), null, DEFAULT_PREFIX);

    private final Path directory;
    private final R2 storage;
    private final Download download;
    private final String bucket;
    private final String prefix;
    private final Object[] downloadLocks = new Object[64];

    private SoundFiles(Path directory, R2 storage, String bucket, String prefix) {
        this(directory, storage, bucket, prefix, storage::download);
    }

    SoundFiles(Path directory, R2 storage, String bucket, String prefix, Download download) {
        this.directory = directory;
        this.storage = storage;
        this.download = download;
        this.bucket = bucket;
        this.prefix = prefix;

        Arrays.setAll(downloadLocks, index -> new Object());
    }

    public static void configure(R2Settings settings) {
        if (settings == null || !settings.enabled())
            return;

        String bucket = settings.soundBucket();
        if (bucket != null && !bucket.isBlank() && !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"))
            throw new IllegalArgumentException("settings.r2.soundBucket must be a valid R2 bucket name");

        String prefix = settings.soundPrefix() == null ? DEFAULT_PREFIX : settings.soundPrefix().strip();
        if (!prefix.isEmpty() && !prefix.endsWith("/"))
            prefix += "/";
        if (!prefix.isEmpty() && !prefix.matches("(?:[a-zA-Z0-9_-]+/)+"))
            throw new IllegalArgumentException("settings.r2.soundPrefix must be a relative object prefix");

        String configuredDirectory = settings.soundDirectory();
        Path directory = configuredDirectory == null || configuredDirectory.isBlank()
            ? Path.of("rsc", "cache", "sounds") : Path.of(configuredDirectory);
        instance = new SoundFiles(directory, R2.getInstance(), bucket, prefix);
    }

    public static SoundFiles getInstance() {
        return instance;
    }

    public Path resolve(Sound sound) throws IOException {
        return resolve(sound.getId(), sound.getExtension());
    }

    public Path resolve(String id, String extension) throws IOException {
        String fileName = fileName(id, extension);
        Path target = directory.resolve(fileName);
        if (isReady(target))
            return target;
        if (!storage.isEnabled())
            throw new IOException(UNAVAILABLE);

        Object lock = downloadLocks[Math.floorMod(fileName.hashCode(), downloadLocks.length)];
        synchronized (lock) {
            if (isReady(target))
                return target;

            publish(target, partial -> download.write(prefix + fileName, partial));

            return target;
        }
    }

    public long inspectDuration(Path source, String extension) throws IOException {
        String format = extension.toLowerCase(Locale.ROOT);
        String container = switch (format) {
            case "mp3" -> format;
            case "opus" -> "ogg";
            default -> throw new IOException("Unsupported sound format: " + extension);
        };

        if (format.equals("opus")) {
            try (InputStream input = Files.newInputStream(source); OpusFile opus = new OpusFile(new OggFile(input))) {
                if (opus.getInfo() == null)
                    throw new IOException("Missing Opus header");
            }
        }

        try (LocalSeekableInputStream input = new LocalSeekableInputStream(source.toFile())) {
            AudioReference reference = new AudioReference(source.toString(), null);
            MediaContainerDetectionResult detected = new MediaContainerDetection(MediaContainerRegistry.DEFAULT_REGISTRY,
                reference, input, MediaContainerHints.from(null, format)).detectContainer();
            if (detected == null || !detected.isSupportedFile() || !container.equals(detected.getContainerDescriptor().probe.getName()))
                throw new IOException("Invalid " + format + " sound");

            AudioTrackInfo info = detected.getTrackInfo();
            if (info.isStream || info.length <= 0 || info.length == Units.DURATION_MS_UNKNOWN)
                throw new IOException("Sound duration is unavailable");

            return info.length;
        }
    }

    public static String uploadExtension(String filename) {
        int separator = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!extension.equals("mp3") && !extension.equals("opus"))
            throw Exception.invalidRequest("Sound file must be MP3 or Opus");

        return extension;
    }

    public String create(BotDB database, String name, String guildId, String userId, String extension,
            boolean isPublic, List<String> tags, InputStream input) {
        Path staged = null;

        try {
            staged = Files.createTempFile("beebot-upload-", "." + extension);
            if (Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING) == 0)
                throw Exception.invalidRequest("Sound file is empty");

            long duration;
            try {
                duration = inspectDuration(staged, extension);
            } catch (IOException | IllegalArgumentException failure) {
                throw new Exception(Type.INVALID_REQUEST, "invalid_sound_file", "Invalid audio or unavailable duration", failure, null);
            }

            Path source = staged;
            return database.insertSound(name, guildId, userId, extension, isPublic, duration, tags,
                sound -> upload(sound, source));
        } catch (Exception failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new Exception(Type.UNAVAILABLE, "sound_storage_unavailable", "Sound storage is unavailable", failure, null);
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException failure) {
                    LOG.warn("Could not remove sound upload staging file", failure);
                }
            }
        }
    }

    public void upload(Sound sound, Path source) {
        String extension = sound.getExtension();
        String fileName = fileName(sound.getId(), extension);
        if (storage.isEnabled()) {
            String contentType = extension.equalsIgnoreCase("mp3") ? "audio/mpeg" : "audio/ogg";
            storage.upload(bucket, prefix + fileName, source, contentType, metadata(sound));
            return;
        }

        try {
            publish(directory.resolve(fileName), partial -> Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void syncMetadata(Sound sound) {
        if (!storage.isEnabled())
            return;

        try {
            storage.updateMetadata(bucket, prefix + fileName(sound.getId(), sound.getExtension()), metadata(sound));
        } catch (RuntimeException failure) {
            throw new Exception(Type.UNAVAILABLE, "sound_storage_unavailable", "Sound metadata storage is unavailable", failure, null);
        }
    }

    public String playbackUrl(String id, String extension, String legacyCdn) {
        if (extension == null || !extension.matches("[a-zA-Z0-9]+") || !ValidationUtils.isDecimalId(id))
            return null;

        String fileName = fileName(id, extension);
        if (storage.isEnabled())
            return storage.url(prefix + fileName);
        if (legacyCdn == null || legacyCdn.isBlank())
            return null;

        return legacyCdn.replaceAll("/+$", "") + "/sounds/" + fileName;
    }

    /* -------------------------------------------------------------------------- */

    static Map<String, String> metadata(Sound sound) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("id", sound.getId());
        values.put("guild-id", sound.getGuildId());
        values.put("user-id", sound.getUserId());
        values.put("name-base64", BASE64.encodeToString(sound.getName().getBytes(StandardCharsets.UTF_8)));
        values.put("extension", sound.getExtension());
        values.put("public", Boolean.toString(sound.isPublic()));
        if (sound.getCreatedAt() != null)
            values.put("created-at", sound.getCreatedAt());
        if (sound.getDurationMs() != null)
            values.put("duration-ms", sound.getDurationMs().toString());

        values.put("plays", Integer.toString(sound.getGlobalPlays()));
        values.put("likes", Integer.toString(sound.getLikes()));
        values.put("dislikes", Integer.toString(sound.getDislikes()));

        List<Tag> tags = new ArrayList<>();
        for (Tag tag : sound.getTags()) {
            if (tag.isStored())
                tags.add(tag);
        }
        tags.sort(Comparator.comparingInt(Tag::getId));
        values.put("tags-gzip-base64", encodeTags(tags));

        return values;
    }

    private static String encodeTags(List<Tag> tags) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream compressed = new GZIPOutputStream(buffer)) {
            compressed.write(JSON.writeValueAsBytes(tags));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return BASE64.encodeToString(buffer.toByteArray());
    }

    private static String fileName(String id, String extension) {
        if (!ValidationUtils.isDecimalId(id) || extension == null || !extension.matches("[a-zA-Z0-9]+"))
            throw new IllegalArgumentException("Invalid sound file name");

        return id + "." + extension;
    }

    private static boolean isReady(Path file) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) > 0;
    }

    private static void publish(Path target, FileWriter writer) throws IOException {
        Files.createDirectories(target.getParent());
        Path partial = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");

        try {
            writer.write(partial);
            if (!isReady(partial))
                throw new IOException(UNAVAILABLE);

            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    @FunctionalInterface
    interface Download {
        void write(String key, Path target) throws IOException;
    }

    @FunctionalInterface
    private interface FileWriter {
        void write(Path path) throws IOException;
    }
}
