package com.safjnest.bot.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.safjnest.config.R2Settings;
import com.safjnest.database.R2;
import com.safjnest.config.Settings;
import com.safjnest.utils.ValidationUtils;

import net.dv8tion.jda.api.entities.Message.Attachment;

public final class SoundboardThumbnails {

    public static final int MAX_ATTACHMENT_BYTES = 1024 * 1024;
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] JPEG_HEADER = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] GIF_87_HEADER = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF_89_HEADER = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private final Upload uploader;

    SoundboardThumbnails(Upload uploader) {
        this.uploader = uploader;
    }

    public record Image(byte[] bytes, String extension, String contentType) {}

    public static SoundboardThumbnails configured() {
        R2Settings settings = Settings.get().services().getR2();
        R2 storage = R2.getInstance();
        if (settings == null || !storage.canUpload())
            throw new IllegalStateException("R2 upload credentials are required for soundboard thumbnails");

        return new SoundboardThumbnails((key, image) ->
            storage.upload(settings.soundBucket(), key, image.bytes(), image.contentType()));
    }

    public static Image download(Attachment attachment) throws IOException, InterruptedException {
        if (attachment.getSize() == 0 || attachment.getSize() > MAX_ATTACHMENT_BYTES)
            throw new IllegalArgumentException("Invalid thumbnail size");

        try (InputStream source = attachment.getProxy().download().get(30, TimeUnit.SECONDS)) {
            return read(source);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Could not download soundboard thumbnail", exception);
        }
    }

    public static Image read(InputStream source) throws IOException {
        byte[] bytes = source.readNBytes(MAX_ATTACHMENT_BYTES + 1);
        if (bytes.length > MAX_ATTACHMENT_BYTES)
            throw new IllegalArgumentException("Invalid thumbnail size");

        return identify(bytes);
    }

    public String upload(String soundboardId, Image image) {
        if (!ValidationUtils.isDecimalId(soundboardId))
            throw new IllegalArgumentException("Invalid soundboard ID");

        String key = "soundboard/thumbnail/" + soundboardId + "/" + UUID.randomUUID() + "." + image.extension();
        uploader.put(key, image);

        return key;
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
