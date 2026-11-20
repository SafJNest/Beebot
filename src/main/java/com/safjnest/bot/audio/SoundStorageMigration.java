package com.safjnest.bot.audio;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.SoundDB;
import com.safjnest.database.sql.database.SoundDB.StorageKeys;

public final class SoundStorageMigration {

    private static final Logger LOG = LoggerFactory.getLogger(SoundStorageMigration.class);
    private static final int BATCH_SIZE = 100;

    private final SoundFiles files;
    private final SoundDB database;

    public SoundStorageMigration() {
        this.files = SoundFiles.getInstance();
        this.database = BotDB.get().communitySounds();
    }

    public Report run(String soundId, Mode mode) {
        if (mode != Mode.PREVIEW)
            files.requireBucket();

        Totals totals = new Totals();
        if (soundId != null) {
            process(database.storageRecord(soundId), mode, totals);

            return totals.report();
        }

        String after = "0";

        while (true) {
            List<QueryRecord> rows = database.storagePage(after, BATCH_SIZE);
            if (rows.isEmpty())
                return totals.report();

            for (QueryRecord row : rows)
                process(row, mode, totals);

            after = rows.getLast().get("id");
        }
    }

    public enum Mode { PREVIEW, APPLY, PRUNE }

    public record Report(int audio, int thumbnails, int updated, int removed, int failed) {}

    /* -------------------------------------------------------------------------- */

    private void process(QueryRecord sound, Mode mode, Totals totals) {
        String id = sound.get("id");

        try {
            if (mode == Mode.PRUNE) {
                prune(sound, totals);

                return;
            }

            Transfer audio = audioTransfer(sound);
            String image = imageSource(sound);
            if (audio != null)
                totals.audio++;
            if (image != null)
                totals.thumbnails++;
            if (mode == Mode.PREVIEW)
                return;
            if (audio == null && image == null && files.hasObject(files.audioKey(id, sound.get("extension"))))
                return;

            if (database.migrateStorage(id, current -> copy(current, totals))) {
                totals.updated++;
                SoundCache.catalogChanged(id);
            }
        } catch (RuntimeException failure) {
            totals.failed++;
            LOG.warn("Could not relocate sound {}", id, failure);
        }
    }

    private StorageKeys copy(QueryRecord sound, Totals totals) {
        String soundId = sound.get("id");
        String audioKey = sound.get("url");
        String imageKey = sound.get("thumbnail");

        Transfer audio = audioTransfer(sound);
        if (audio != null) {
            if (audioKey != null || files.hasObject(audio.source()))
                files.copy(audio.source(), audio.target());
            else if (!files.hasObject(audio.target()))
                throw new IllegalStateException("Missing R2 audio for sound " + soundId);

            audioKey = audio.target();
        } else if (!files.hasObject(audioKey)) {
            files.copy(files.flatAudioKey(soundId, sound.get("extension")), audioKey);
        }

        try {
            String image = imageSource(sound);
            if (image != null) {
                String target = files.migratedImageKey(soundId, image);
                files.copy(image, target);
                imageKey = target;
            }
        } catch (RuntimeException failure) {
            totals.failed++;
            LOG.warn("Could not relocate the thumbnail for sound {}", soundId, failure);
        }

        return new StorageKeys(audioKey, imageKey);
    }

    private Transfer audioTransfer(QueryRecord sound) {
        String id = sound.get("id");
        String extension = sound.get("extension");
        String current = sound.get("url");
        String target = files.audioKey(id, extension);
        if (target.equals(current))
            return null;

        return new Transfer(current == null ? files.flatAudioKey(id, extension) : current, target);
    }

    private String imageSource(QueryRecord sound) {
        String id = sound.get("id");
        String current = sound.get("thumbnail");
        if (current != null && files.isSoundImageKey(id, current))
            return null;

        return current == null || current.isBlank() ? null : current;
    }

    private void prune(QueryRecord sound, Totals totals) {
        String id = sound.get("id");
        String extension = sound.get("extension");
        if (!files.audioKey(id, extension).equals(sound.get("url")))
            throw new IllegalStateException("Sound still needs migration: " + id);

        if (files.pruneFlatAudio(id, extension))
            totals.removed++;
    }

    private record Transfer(String source, String target) {}

    private static final class Totals {
        private int audio;
        private int thumbnails;
        private int updated;
        private int removed;
        private int failed;

        private Report report() {
            return new Report(audio, thumbnails, updated, removed, failed);
        }
    }
}
