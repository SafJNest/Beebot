package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.lol.build.Filter;
import com.safjnest.lol.model.ChampionStats;
import com.safjnest.lol.service.ChampionStatsService;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.util.SafJNest;  
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.twitch.TwitchClient;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

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

        Filter filter = new Filter().setRank(TierType.CHALLENGER).setPatch("16.8");
        Map<Integer, ChampionStats> stats = new ChampionStatsService().getAll(filter);
        
        List<ChampionStats> topChamps = stats.values().stream()
            .filter(s -> s.laneStats().stream().anyMatch(l -> l.lane() == LaneType.TOP))
            .toList();
        
        double avgGames = topChamps.stream()
            .mapToInt(s -> s.getLaneStat(LaneType.TOP).games())
            .average()
            .orElse(0);
        
        topChamps.stream()
            .filter(s -> s.getLaneStat(LaneType.TOP).games() > avgGames)
            .sorted(Comparator.comparingDouble((ChampionStats s) ->
                s.getLaneStat(LaneType.TOP).winrate()
            ).reversed())
            .forEach(s -> {
                System.out.println(ChampionUtils.getChampion(s.filter().champion()).getName() + " - " + s.getLaneStat(LaneType.TOP).lane() + " - " + s.getLaneStat(LaneType.TOP).winrate());
            });

            List<ChampionStats> allChamps = stats.values().stream().toList();

double avgBans = allChamps.stream()
    .mapToInt(ChampionStats::bans)
    .average()
    .orElse(0);

allChamps.stream()
    .filter(s -> s.bans() > avgBans)
    .sorted(Comparator.comparingDouble(ChampionStats::banrate).reversed())
    .forEach(s -> {
        System.out.println(ChampionUtils.getChampion(s.filter().champion()).getName() + " - " + s.prettyBanrate());
    });

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
