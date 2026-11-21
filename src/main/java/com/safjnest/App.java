package com.safjnest;

import com.safjnest.bot.Bot;
import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.config.R2Settings;
import com.safjnest.config.Settings;
import com.safjnest.database.R2;
import com.safjnest.database.nosql.MongoDB;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;
import com.safjnest.lol.service.LeaderboardService;
import com.safjnest.spring.SpringServer;
import com.safjnest.status.SystemMetricsSampler;
import com.safjnest.status.log.BotLogger;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.twitch.TwitchClient;

public class App {

    private static Bot bot;
    private static SpringServer springServer;
    private static Boolean trackingEnabled;

    public static void main(String args[]) {
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        Settings settings = Settings.get();
        R2Settings r2 = settings.services().getR2();
        R2.configure(r2);
        SoundFiles.configure(r2);

        runSpring(settings.api());
        
        TwitchClient.init();
        SystemMetricsSampler.start();
        QueueHandler.start();
        LeaderboardService.warmupIndexAsync();

        bot = new Bot();
        bot.il_risveglio_della_bestia();
    }

    private static void runSpring(Settings.Api api) {
        try {
            springServer = SpringServer.start(api);
            BotLogger.info("Spring API started on port " + api.port());
        } catch (Exception e) {
            BotLogger.error("Failed to start Spring API: " + e.getMessage());
        }
    }

    public static String getAuthAllowedRedirectUris() {
        Settings.Discord discord = Settings.get().api().discord();

        return discord == null ? null : discord.allowedRedirectUris();
    }

    public static void shutdown() {
        BotLogger.trace("Shutting down the bot");
        if (springServer != null) {
            try {
                springServer.stop();
            } catch (Exception e) {
                BotLogger.error("Failed to stop Spring API: " + e.getMessage());
            }
        }
        bot.distruzione_demoniaca();
        QueueHandler.shutdown();
        SyncScheduler.shutdown();
        ComputeScheduler.shutdown();
        RiotScheduler.shutdown();
        R2.getInstance().close();
        MongoDB.close();
    }

    public static void restart() {
        BotLogger.trace("Restarting the bot");
        bot.distruzione_demoniaca();
        bot.il_risveglio_della_bestia();
    }

    public static boolean isTesting() {
        return Settings.get().runtime().testing();
    }

    public static boolean tracking() {
        if (trackingEnabled != null) return trackingEnabled;
        return !isTesting();
    }

    public static boolean toggleTracking() {
        trackingEnabled = !tracking();
        return trackingEnabled;
    }

}
