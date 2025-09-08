package com.safjnest.spring.example;

import com.safjnest.spring.entity.Match;
import com.safjnest.spring.entity.Participant;
import com.safjnest.spring.entity.Summoner;
import com.safjnest.spring.service.LeagueDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Example class demonstrating how to use the new JPA-based LeagueDataService
 * instead of the raw SQL LeagueDB methods.
 * 
 * This shows the migration path from raw SQL to JPA for common League data operations.
 */
@Component
public class LeagueDataExample {

    @Autowired
    private LeagueDataService leagueDataService;

    /**
     * Example: Get all League accounts for a Discord user
     * Replaces: LeagueDB.getLOLAccountsByUserId(userId)
     */
    public void exampleGetUserAccounts(String discordUserId) {
        List<Summoner> accounts = leagueDataService.getLolAccountsByUserId(discordUserId);
        
        System.out.println("Found " + accounts.size() + " League accounts for user " + discordUserId);
        for (Summoner account : accounts) {
            System.out.println("- " + account.getRiotId() + " (" + account.getPuuid() + ")");
        }
    }

    /**
     * Example: Search for summoners by name
     * Replaces: LeagueDB.getFocusedSummoners(query, shard)
     */
    public void exampleSearchSummoners(String searchQuery) {
        Integer euwShard = 3; // EUW1 shard
        List<String> foundSummoners = leagueDataService.getFocusedSummoners(searchQuery, euwShard);
        
        System.out.println("Found summoners matching '" + searchQuery + "':");
        foundSummoners.forEach(System.out::println);
    }

    /**
     * Example: Get advanced statistics for a summoner
     * Replaces: LeagueDB.getAdvancedLOLData(summonerId)
     */
    public void exampleGetSummonerStats(String puuid) {
        Optional<Integer> summonerIdOpt = leagueDataService.getSummonerIdByPuuid(puuid, 3);
        
        if (summonerIdOpt.isPresent()) {
            Integer summonerId = summonerIdOpt.get();
            List<Object[]> stats = leagueDataService.getAdvancedLolData(summonerId);
            
            System.out.println("Champion statistics for summoner:");
            for (Object[] stat : stats) {
                Short champion = (Short) stat[0];
                Long games = (Long) stat[1];
                Long wins = (Long) stat[2];
                Long losses = (Long) stat[3];
                Long totalLp = (Long) stat[4];
                
                double winRate = games > 0 ? (wins * 100.0 / games) : 0;
                System.out.printf("Champion %d: %d games, %.1f%% winrate, %d LP gained%n", 
                    champion, games, winRate, totalLp);
            }
        }
    }

    /**
     * Example: Get recent match data
     * Replaces: LeagueDB.getMatchData()
     */
    public void exampleGetRecentMatches() {
        List<Object[]> matchData = leagueDataService.getMatchData();
        
        System.out.println("Recent match data:");
        for (Object[] match : matchData) {
            // match contains: [match.id, match.gameId, match.leagueShard, match.gameType, ...]
            Integer matchId = (Integer) match[0];
            String gameId = (String) match[1];
            System.out.println("Match " + matchId + " (Game ID: " + gameId + ")");
        }
    }

    /**
     * Example: Add a new League account
     * Replaces: LeagueDB.addLOLAccount(userId, summoner)
     */
    public void exampleAddLeagueAccount(String discordUserId, String riotId, 
                                        String summonerId, String puuid) {
        try {
            Summoner newAccount = leagueDataService.addLolAccount(
                discordUserId, riotId, summonerId, null, puuid, 3
            );
            
            System.out.println("Successfully added League account: " + newAccount.getRiotId());
        } catch (Exception e) {
            System.err.println("Failed to add League account: " + e.getMessage());
        }
    }

    /**
     * Example: Record match participation
     * Replaces: LeagueDB.setSummonerData(...)
     */
    public void exampleRecordMatchParticipation(Integer summonerId, Integer matchId) {
        Participant participant = leagueDataService.setSummonerData(
            summonerId, matchId, 
            true, // won the game
            "10/2/15", // KDA
            (short) 64, // Yasuo champion ID
            (byte) 2, // Mid lane
            (byte) 100, // Blue team
            (short) 1500, // Current rank
            (short) 75, // Current LP
            (short) 18, // LP gained
            "{}", // Build JSON
            "{}" // Pings JSON
        );
        
        if (participant != null) {
            System.out.println("Successfully recorded match participation");
        } else {
            System.out.println("Participant already exists for this match");
        }
    }

    /**
     * Example: Track/untrack a summoner for automatic updates
     * Replaces: LeagueDB.trackSummoner(userId, puuid, track)
     */
    public void exampleToggleTracking(String discordUserId, String puuid, boolean enable) {
        boolean success = leagueDataService.trackSummoner(discordUserId, puuid, enable);
        
        if (success) {
            System.out.println("Tracking " + (enable ? "enabled" : "disabled") + " for summoner");
        } else {
            System.out.println("Failed to update tracking status");
        }
    }

    /**
     * Example: Get summoner match history with time filtering
     * Replaces: LeagueDB.getSummonerData(summonerId, shard, timeStart, timeEnd)
     */
    public void exampleGetMatchHistory(Integer summonerId) {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        LocalDateTime now = LocalDateTime.now();
        
        List<Object[]> matchHistory = leagueDataService.getSummonerData(
            summonerId, 3, oneWeekAgo, now
        );
        
        System.out.println("Match history for last week:");
        for (Object[] match : matchHistory) {
            // match contains: [summonerId, gameId, rank, lp, gain, win, timeStart, timeEnd, patch]
            String gameId = (String) match[1];
            Boolean won = (Boolean) match[5];
            Short lpGain = (Short) match[4];
            
            System.out.printf("Game %s: %s (%+d LP)%n", 
                gameId, won ? "Victory" : "Defeat", lpGain);
        }
    }

    /**
     * Example: Get all games for a summoner in a time period
     * Replaces: LeagueDB.getAllGamesForAccount(summonerId, timeStart, timeEnd)
     */
    public void exampleGetAllGames(Integer summonerId) {
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        LocalDateTime now = LocalDateTime.now();
        
        List<Object[]> allGames = leagueDataService.getAllGamesForAccount(
            summonerId, lastMonth, now
        );
        
        int wins = 0;
        int losses = 0;
        
        for (Object[] game : allGames) {
            Boolean won = (Boolean) game[2];
            if (won) wins++;
            else losses++;
        }
        
        double winRate = allGames.size() > 0 ? (wins * 100.0 / allGames.size()) : 0;
        System.out.printf("Last month: %d games, %d wins, %d losses (%.1f%% winrate)%n", 
            allGames.size(), wins, losses, winRate);
    }
}