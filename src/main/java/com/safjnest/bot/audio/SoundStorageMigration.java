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

    public Report run(String soundId, boolean apply) {
        if (apply)
            files.requireBucket();

        Totals totals = new Totals();
        if (soundId != null) {
            process(database.storageRecord(soundId), apply, totals);

            return totals.report();
        }

        String after = "0";

        while (true) {
            List<QueryRecord> rows = database.storagePage(after, BATCH_SIZE);
            if (rows.isEmpty())
                return totals.report();

            for (QueryRecord row : rows)
                process(row, apply, totals);

            after = rows.getLast().get("id");
        }
    }

    public record Report(int audio, int thumbnails, int updated, int failed) {}

    /* -------------------------------------------------------------------------- */

    private void process(QueryRecord sound, boolean apply, Totals totals) {
        String id = sound.get("id");

        try {
            Transfer audio = audioTransfer(sound);
            String image = imageSource(sound);
            if (audio != null)
                totals.audio++;
            if (image != null)
                totals.thumbnails++;
            if (!apply || audio == null && image == null)
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
            files.copy(audio.source(), audio.target());
            audioKey = audio.target();
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

        return new Transfer(current == null ? files.legacyAudioKey(id, extension) : current, target);
    }

    private String imageSource(QueryRecord sound) {
        String id = sound.get("id");
        String current = sound.get("thumbnail");
        if (current != null && files.isSoundImageKey(id, current))
            return null;

        return current == null ? sound.get("board_thumbnail") : current;
    }

    private record Transfer(String source, String target) {}

    private static final class Totals {
        private int audio;
        private int thumbnails;
        private int updated;
        private int failed;

        private Report report() {
            return new Report(audio, thumbnails, updated, failed);
        }
    }
}
