package com.safjnest.core.audio;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.safjnest.sql.BotDB;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

public class SoundAnalytics extends Thread {
    private static final long INTERVAL = TimeConstant.HOUR;
    private static final int BATCH_SIZE = 10000;
    private static Timer timer;
    private static ExecutorService executor;

    public SoundAnalytics() {
        super("SoundAnalytics");
    }

    @Override
    public void run() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new UpdateSound(), TimeConstant.SECOND, INTERVAL);
    }

    static class UpdateSound extends TimerTask {
        @Override
        public void run() {
            BotLogger.trace("[SOUND ANALYTICS] Start update");

            String query = "SELECT DISTINCT sound_id " +
                           "FROM sound_statistics " +
                           "WHERE last_updated < DATE_SUB(NOW(), INTERVAL 1 HOUR) " +
                           "LIMIT " + BATCH_SIZE;

            List<String> soundIds = BotDB.get().query(query).arrayColumn("sound_id");

            if (soundIds.isEmpty()) {
                BotLogger.trace("[SOUND ANALYTICS] Everything is up to date");
                return;
            }

            int numberOfSounds = soundIds.size();
            int threads = (int) Math.ceil((double) numberOfSounds / 100);
            int batchSizePerThread = Math.max(1, numberOfSounds / threads);

            BotLogger.trace("[SOUND ANALYTICS] Number of sounds: " + numberOfSounds);
            BotLogger.trace("[SOUND ANALYTICS] Number of threads: " + threads);
            BotLogger.trace("[SOUND ANALYTICS] Batch size per thread: " + batchSizePerThread);

            executor = Executors.newFixedThreadPool(threads);

            for (int start = 0, i = 0; start < numberOfSounds; start += batchSizePerThread, i++) {
                int end = Math.min(start + batchSizePerThread, numberOfSounds);
                List<String> batch = soundIds.subList(start, end);
                executor.submit(new UpdateBatchTask(i, batch));
            }

            // Shutdown the executor service
            shutdown();
        }
    }

    static class UpdateBatchTask implements Runnable {
        private final List<String> soundIds;
        private final int ID;

        UpdateBatchTask(int ID, List<String> soundIds) {
            this.soundIds = soundIds;
            this.ID = ID;
        }

        @Override
        public void run() {
            BotLogger.trace("[SOUND ANALYTICS - " + ID + "] updating sounds: " + soundIds);
            
            String query = "INSERT INTO sound_statistics (sound_id, plays, likes, dislikes, last_updated) " +
                           "SELECT p.sound_id, SUM(p.times) AS total_plays, SUM(p.like) AS total_likes, SUM(p.dislike) AS total_dislikes, NOW() AS last_updated " +
                           "FROM play p " +
                           "WHERE p.sound_id IN (" + String.join(",", soundIds) + ") AND p.last_play >= DATE_SUB(NOW(), INTERVAL 1 HOUR) " +
                           "GROUP BY p.sound_id " +
                           "ON DUPLICATE KEY UPDATE plays = VALUES(plays), likes = VALUES(likes), dislikes = VALUES(dislikes), last_updated = VALUES(last_updated)";

            BotDB.get().query(query);
            BotLogger.trace("[SOUND ANALYTICS - " + ID + "] updated sounds");
        }
    }

    // Metodo per chiudere correttamente il pool di thread
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool di thread non si è chiuso correttamente");
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
