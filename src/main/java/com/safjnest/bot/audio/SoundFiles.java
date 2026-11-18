package com.safjnest.bot.audio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import com.safjnest.bot.model.BotSettings.R2Settings;
import com.safjnest.database.R2;
import com.safjnest.sound.model.Sound;
import com.safjnest.utils.ValidationUtils;

public final class SoundFiles {

    private static final Path LEGACY_DIRECTORY = Path.of("rsc", "sounds");
    private static final String DEFAULT_PREFIX = "sounds/";
    private static final String UNAVAILABLE = "Sound file is unavailable.";

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

    public Path localPath(Sound sound) {
        return directory.resolve(fileName(sound.getId(), sound.getExtension()));
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

    public void upload(String id, String extension, Path source) {
        String fileName = fileName(id, extension);
        if (storage.isEnabled()) {
            String contentType = extension.equalsIgnoreCase("mp3") ? "audio/mpeg" : "audio/ogg";
            storage.upload(bucket, prefix + fileName, source, contentType);
            return;
        }

        try {
            publish(directory.resolve(fileName), partial -> Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
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
