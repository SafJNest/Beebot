package com.safjnest.bot.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.safjnest.bot.model.BotSettings.R2Settings;
import com.safjnest.database.R2;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.ValidationUtils;

import net.dv8tion.jda.api.entities.Message.Attachment;

public final class SoundboardThumbnails {

    private static final Logger LOG = LoggerFactory.getLogger(SoundboardThumbnails.class);
    private static final int MAX_ATTACHMENT_BYTES = 1024 * 1024;
    private static final int MIGRATION_BATCH_SIZE = 50;
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] JPEG_HEADER = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] GIF_87_HEADER = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF_89_HEADER = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private final BotDB database;
    private final Upload uploader;

    SoundboardThumbnails(BotDB database, Upload uploader) {
        this.database = database;
        this.uploader = uploader;
    }

    public record Image(byte[] bytes, String extension, String contentType) {}
    public record Migration(int migrated, int skipped, List<Integer> failedIds, int pending) {}

    public static SoundboardThumbnails configured() {
        R2Settings settings = SettingsLoader.getSettings().getJsonSettings().getR2();
        R2 storage = R2.getInstance();
        if (settings == null || !storage.canUpload())
            throw new IllegalStateException("R2 upload credentials are required for soundboard thumbnails");

        return new SoundboardThumbnails(BotDB.get(), (key, image) ->
            storage.upload(settings.soundBucket(), key, image.bytes(), image.contentType()));
    }

    public static Image download(Attachment attachment) throws IOException, InterruptedException {
        if (attachment.getSize() == 0 || attachment.getSize() > MAX_ATTACHMENT_BYTES)
            throw new IllegalArgumentException("Invalid thumbnail size");

        try (InputStream source = attachment.getProxy().download().get(30, TimeUnit.SECONDS)) {
            byte[] bytes = source.readNBytes(MAX_ATTACHMENT_BYTES + 1);
            if (bytes.length > MAX_ATTACHMENT_BYTES)
                throw new IllegalArgumentException("Invalid thumbnail size");

            return identify(bytes);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Could not download soundboard thumbnail", exception);
        }
    }

    public String upload(String soundboardId, Image image) {
        if (!ValidationUtils.isDecimalId(soundboardId))
            throw new IllegalArgumentException("Invalid soundboard ID");

        String key = "soundboard/thumbnail/" + soundboardId + "/" + UUID.randomUUID() + "." + image.extension();
        uploader.put(key, image);

        return key;
    }

    public Migration migrate() {
        if (database.countPendingSoundboardThumbnails() == 0)
            return new Migration(0, 0, List.of(), 0);

        List<Integer> failed = new ArrayList<>();
        int migrated = 0;
        int skipped = 0;
        int afterId = 0;

        while (true) {
            List<Integer> ids = database.pendingSoundboardThumbnailIds(afterId, MIGRATION_BATCH_SIZE);
            if (ids.isEmpty())
                break;

            for (int id : ids) {
                try {
                    byte[] legacy = database.getPendingSoundboardThumbnail(id);
                    if (legacy == null) {
                        skipped++;
                        continue;
                    }

                    String key = upload(Integer.toString(id), identify(legacy));
                    if (database.migrateSoundboardThumbnail(id, legacy, key))
                        migrated++;
                    else
                        skipped++;
                } catch (RuntimeException exception) {
                    failed.add(id);
                    LOG.warn("Could not migrate thumbnail for soundboard {}", id, exception);
                }
            }

            afterId = ids.getLast();
        }

        return new Migration(migrated, skipped, List.copyOf(failed), database.countPendingSoundboardThumbnails());
    }

    /* -------------------------------------------------------------------------- */

    private static Image identify(byte[] bytes) {
        if (hasHeader(bytes, PNG_HEADER))
            return new Image(bytes, "png", "image/png");
        if (hasHeader(bytes, JPEG_HEADER))
            return new Image(bytes, "jpg", "image/jpeg");
        if (hasHeader(bytes, GIF_87_HEADER) || hasHeader(bytes, GIF_89_HEADER))
            return new Image(bytes, "gif", "image/gif");

        throw new IllegalArgumentException("Thumbnail must be PNG, JPEG or GIF");
    }

    private static boolean hasHeader(byte[] bytes, byte[] header) {
        return bytes.length > header.length && Arrays.equals(bytes, 0, header.length, header, 0, header.length);
    }

    @FunctionalInterface
    interface Upload {
        void put(String key, Image image);
    }
}
