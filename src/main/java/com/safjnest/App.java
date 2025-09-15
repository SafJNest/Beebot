package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.safjnest.core.Bot;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.util.SafJNest;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.lol.LeagueService;
import com.safjnest.util.lol.SummonerRepository;
import com.safjnest.util.lol.entity.SummonerDTO;
import com.safjnest.util.lol.entity.MatchDTO;
import com.safjnest.util.lol.entity.ParticipantDTO;
import com.safjnest.util.lol.entity.RankDTO;
import com.safjnest.util.lol.entity.MasteryDTO;
import com.safjnest.util.twitch.TwitchClient;

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
            runSpring();
        }
        else {
            TwitchClient.init();
            //runSpring();
        }
        
        // Test League of Legends DTOs
        testLeagueOfLegendsDTOs();
        
        //bot = new Bot();
        //bot.il_risveglio_della_bestia();
    }

    /**
     * Test method for League of Legends DTOs
     */
    public static void testLeagueOfLegendsDTOs() {
        System.out.println("=== Testing League of Legends DTOs ===");
        
        // Test SummonerDTO
        testSummonerDTO();
        
        // Test MatchDTO
        testMatchDTO();
        
        // Test ParticipantDTO
        testParticipantDTO();
        
        // Test RankDTO
        testRankDTO();
        
        // Test MasteryDTO
        testMasteryDTO();
        
        System.out.println("=== DTO Tests Completed Successfully ===");
    }
    
    private static void testSummonerDTO() {
        System.out.println("Testing SummonerDTO...");
        
        SummonerDTO summoner = new SummonerDTO();
        summoner.setId(1);
        summoner.setRiotId("TestUser#123");
        summoner.setSummonerId("testSummonerId");
        summoner.setAccountId("testAccountId");
        summoner.setPuuid("test-puuid-12345");
        summoner.setLeagueShard(3);
        summoner.setUserId("testUserId");
        summoner.setTracking(1);
        
        // Test getters
        assert summoner.getId().equals(1) : "SummonerDTO ID test failed";
        assert summoner.getRiotId().equals("TestUser#123") : "SummonerDTO RiotId test failed";
        assert summoner.getSummonerId().equals("testSummonerId") : "SummonerDTO SummonerId test failed";
        assert summoner.getAccountId().equals("testAccountId") : "SummonerDTO AccountId test failed";
        assert summoner.getPuuid().equals("test-puuid-12345") : "SummonerDTO PUUID test failed";
        assert summoner.getLeagueShard().equals(3) : "SummonerDTO LeagueShard test failed";
        assert summoner.getUserId().equals("testUserId") : "SummonerDTO UserId test failed";
        assert summoner.getTracking().equals(1) : "SummonerDTO Tracking test failed";
        
        System.out.println("✓ SummonerDTO tests passed");
    }
    
    private static void testMatchDTO() {
        System.out.println("Testing MatchDTO...");
        
        MatchDTO match = new MatchDTO();
        match.setId(1);
        match.setGameId("test-game-id");
        match.setLeagueShard(3);
        match.setGameType(420);
        match.setBans("[]");
        match.setTimeStart(LocalDateTime.now().minusHours(1));
        match.setTimeEnd(LocalDateTime.now());
        match.setPatch("14.21");
        
        // Test getters
        assert match.getId().equals(1) : "MatchDTO ID test failed";
        assert match.getGameId().equals("test-game-id") : "MatchDTO GameId test failed";
        assert match.getLeagueShard().equals(3) : "MatchDTO LeagueShard test failed";
        assert match.getGameType().equals(420) : "MatchDTO GameType test failed";
        assert match.getBans().equals("[]") : "MatchDTO Bans test failed";
        assert match.getPatch().equals("14.21") : "MatchDTO Patch test failed";
        assert match.getTimeStart() != null : "MatchDTO TimeStart test failed";
        assert match.getTimeEnd() != null : "MatchDTO TimeEnd test failed";
        
        System.out.println("✓ MatchDTO tests passed");
    }
    
    private static void testParticipantDTO() {
        System.out.println("Testing ParticipantDTO...");
        
        ParticipantDTO participant = new ParticipantDTO();
        participant.setId(1);
        participant.setWin(true);
        participant.setKda("10/2/5");
        participant.setChampion((short) 103);
        participant.setLane((byte) 2);
        participant.setTeam((byte) 100);
        participant.setSubteam((byte) 0);
        participant.setSubteamPlacement((byte) 0);
        participant.setRank((short) 1200);
        participant.setLp((short) 50);
        participant.setGain((short) 20);
        participant.setDamage(25000);
        participant.setDamageBuilding(5000);
        participant.setHealing(3000);
        participant.setCs((short) 180);
        participant.setGoldEarned(15000);
        participant.setWard((short) 15);
        participant.setWardKilled((short) 3);
        participant.setVisionScore((short) 45);
        participant.setPings("{}");
        participant.setBuild("{}");
        
        // Test getters
        assert participant.getId().equals(1) : "ParticipantDTO ID test failed";
        assert participant.getWin().equals(true) : "ParticipantDTO Win test failed";
        assert participant.getKda().equals("10/2/5") : "ParticipantDTO KDA test failed";
        assert participant.getChampion().equals((short) 103) : "ParticipantDTO Champion test failed";
        assert participant.getLane().equals((byte) 2) : "ParticipantDTO Lane test failed";
        assert participant.getTeam().equals((byte) 100) : "ParticipantDTO Team test failed";
        assert participant.getRank().equals((short) 1200) : "ParticipantDTO Rank test failed";
        assert participant.getLp().equals((short) 50) : "ParticipantDTO LP test failed";
        assert participant.getGain().equals((short) 20) : "ParticipantDTO Gain test failed";
        assert participant.getDamage().equals(25000) : "ParticipantDTO Damage test failed";
        assert participant.getCs().equals((short) 180) : "ParticipantDTO CS test failed";
        assert participant.getGoldEarned().equals(15000) : "ParticipantDTO GoldEarned test failed";
        assert participant.getWard().equals((short) 15) : "ParticipantDTO Ward test failed";
        assert participant.getVisionScore().equals((short) 45) : "ParticipantDTO VisionScore test failed";
        
        System.out.println("✓ ParticipantDTO tests passed");
    }
    
    private static void testRankDTO() {
        System.out.println("Testing RankDTO...");
        
        RankDTO rank = new RankDTO();
        rank.setId(1);
        rank.setGameType(420);
        rank.setRank(1200);
        rank.setLp(50);
        rank.setWins(25);
        rank.setLosses(15);
        rank.setLastUpdate(LocalDateTime.now());
        
        // Test getters
        assert rank.getId().equals(1) : "RankDTO ID test failed";
        assert rank.getGameType().equals(420) : "RankDTO GameType test failed";
        assert rank.getRank().equals(1200) : "RankDTO Rank test failed";
        assert rank.getLp().equals(50) : "RankDTO LP test failed";
        assert rank.getWins().equals(25) : "RankDTO Wins test failed";
        assert rank.getLosses().equals(15) : "RankDTO Losses test failed";
        assert rank.getLastUpdate() != null : "RankDTO LastUpdate test failed";
        
        System.out.println("✓ RankDTO tests passed");
    }
    
    private static void testMasteryDTO() {
        System.out.println("Testing MasteryDTO...");
        
        MasteryDTO mastery = new MasteryDTO();
        mastery.setId(1);
        mastery.setChampionId(103);
        mastery.setChampionLevel(7);
        mastery.setChampionPoints(150000);
        mastery.setLastPlayTime(LocalDateTime.now());
        
        // Test getters
        assert mastery.getId().equals(1) : "MasteryDTO ID test failed";
        assert mastery.getChampionId().equals(103) : "MasteryDTO ChampionId test failed";
        assert mastery.getChampionLevel().equals(7) : "MasteryDTO ChampionLevel test failed";
        assert mastery.getChampionPoints().equals(150000) : "MasteryDTO ChampionPoints test failed";
        assert mastery.getLastPlayTime() != null : "MasteryDTO LastPlayTime test failed";
        
        System.out.println("✓ MasteryDTO tests passed");
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
