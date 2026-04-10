package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.build.BuildFilter;
import com.safjnest.lol.build.ChampionBuild;
import com.safjnest.lol.build.ChampionBuildService;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.SafJNest;  
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.twitch.TwitchClient;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;


@SpringBootApplication
public class App {

    private static Settings settings;
    private static Bot bot;

    public static void main(String args[]) {
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        settings = SettingsLoader.getSettings();

        if (isTesting()) {
            BotLogger.info("Beebot is in testing mode");
            //runSpring();
        }
        else {
            TwitchClient.init();
            //runSpring();
        }
        
        try (Connection conn = LeagueDB.get().getConnection()) {
            for (StaticChampion champion : LeagueHandler.getRiotApi().getDDragonAPI().getChampions().values()) {
                for (LaneType lane : Arrays.asList(LaneType.TOP, LaneType.JUNGLE, LaneType.MID, LaneType.BOT, LaneType.UTILITY)) {
                    BuildFilter filter = new BuildFilter()
                        .setChampion(champion.getId())
                        .setLane(lane)
                        .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
                        .setPatch("16.7");
        
                    ChampionBuild cb = new ChampionBuildService().getSlotBreakdown(filter, ChampionBuildService.Strategy.MOST_USED, conn);
                    if (cb != null) cb.print();
                    conn.commit();
                
                }
            }
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
    
        }
        //bot = new Bot();
        //bot.il_risveglio_della_bestia();
    }

    public static void runSpring() {
        SpringApplication springApplication = new SpringApplication(App.class);
            
        Properties springProperties = new Properties();
        try {
            springProperties.load(new FileReader("spring.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        springApplication.setDefaultProperties(springProperties);
        springApplication.run();
    }

    public static void shutdown() {
        BotLogger.trace("Shutting down the bot");
        bot.distruzione_demoniaca();
    }

    public static void restart() {
        BotLogger.trace("Restarting the bot");
        bot.distruzione_demoniaca();
        bot.il_risveglio_della_bestia();
    }

    public static boolean isTesting() {
        return settings.getConfig().isTesting();
    }

}
