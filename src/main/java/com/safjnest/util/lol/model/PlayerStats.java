package com.safjnest.util.lol.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive player statistics model that includes advanced metrics
 * and performance indicators.
 * 
 * This model stores both basic and advanced statistics for a player,
 * including efficiency metrics, performance indicators, and recent form.
 * 
 * @author SafJNest Team
 * @since 10.1
 */
public class PlayerStats {
    
    // Basic Information
    private int summonerId;
    private long calculatedAt;
    private long lastMatchTime;
    
    // Overall Stats
    private int totalGames;
    private int totalWins;
    private int totalLosses;
    private double winRate;
    
    // KDA Stats
    private double avgKills;
    private double avgDeaths;
    private double avgAssists;
    private double avgKDA;
    
    // Efficiency Metrics
    private double avgCS;
    private double avgCSM;           // CS per minute
    private double avgGPM;           // Gold per minute
    private double avgDPM;           // Damage per minute
    private double avgDPG;           // Damage per gold
    private double avgVSPM;          // Vision score per minute
    
    // Performance Indicators
    private double killParticipation; // KP%
    private double avgGameDuration;   // In minutes
    private int currentStreak;        // Positive for win streak, negative for loss streak
    
    // LP Stats
    private int totalLPGain;
    private double avgLPGain;
    
    // Champion-specific stats (champion ID -> ChampionStats)
    private Map<Integer, ChampionStats> championStats;
    
    // Lane-specific stats (lane -> LaneStats)
    private Map<String, LaneStats> laneStats;
    
    // Recent Form (last 20 games)
    private RecentForm recentForm;
    
    public PlayerStats() {
        this.championStats = new HashMap<>();
        this.laneStats = new HashMap<>();
        this.calculatedAt = System.currentTimeMillis();
    }
    
    public PlayerStats(int summonerId) {
        this();
        this.summonerId = summonerId;
    }
    
    // Getters and Setters
    
    public int getSummonerId() {
        return summonerId;
    }
    
    public void setSummonerId(int summonerId) {
        this.summonerId = summonerId;
    }
    
    public long getCalculatedAt() {
        return calculatedAt;
    }
    
    public void setCalculatedAt(long calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
    
    public long getLastMatchTime() {
        return lastMatchTime;
    }
    
    public void setLastMatchTime(long lastMatchTime) {
        this.lastMatchTime = lastMatchTime;
    }
    
    public int getTotalGames() {
        return totalGames;
    }
    
    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }
    
    public int getTotalWins() {
        return totalWins;
    }
    
    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }
    
    public int getTotalLosses() {
        return totalLosses;
    }
    
    public void setTotalLosses(int totalLosses) {
        this.totalLosses = totalLosses;
    }
    
    public double getWinRate() {
        return winRate;
    }
    
    public void setWinRate(double winRate) {
        this.winRate = winRate;
    }
    
    public double getAvgKills() {
        return avgKills;
    }
    
    public void setAvgKills(double avgKills) {
        this.avgKills = avgKills;
    }
    
    public double getAvgDeaths() {
        return avgDeaths;
    }
    
    public void setAvgDeaths(double avgDeaths) {
        this.avgDeaths = avgDeaths;
    }
    
    public double getAvgAssists() {
        return avgAssists;
    }
    
    public void setAvgAssists(double avgAssists) {
        this.avgAssists = avgAssists;
    }
    
    public double getAvgKDA() {
        return avgKDA;
    }
    
    public void setAvgKDA(double avgKDA) {
        this.avgKDA = avgKDA;
    }
    
    public double getAvgCS() {
        return avgCS;
    }
    
    public void setAvgCS(double avgCS) {
        this.avgCS = avgCS;
    }
    
    public double getAvgCSM() {
        return avgCSM;
    }
    
    public void setAvgCSM(double avgCSM) {
        this.avgCSM = avgCSM;
    }
    
    public double getAvgGPM() {
        return avgGPM;
    }
    
    public void setAvgGPM(double avgGPM) {
        this.avgGPM = avgGPM;
    }
    
    public double getAvgDPM() {
        return avgDPM;
    }
    
    public void setAvgDPM(double avgDPM) {
        this.avgDPM = avgDPM;
    }
    
    public double getAvgDPG() {
        return avgDPG;
    }
    
    public void setAvgDPG(double avgDPG) {
        this.avgDPG = avgDPG;
    }
    
    public double getAvgVSPM() {
        return avgVSPM;
    }
    
    public void setAvgVSPM(double avgVSPM) {
        this.avgVSPM = avgVSPM;
    }
    
    public double getKillParticipation() {
        return killParticipation;
    }
    
    public void setKillParticipation(double killParticipation) {
        this.killParticipation = killParticipation;
    }
    
    public double getAvgGameDuration() {
        return avgGameDuration;
    }
    
    public void setAvgGameDuration(double avgGameDuration) {
        this.avgGameDuration = avgGameDuration;
    }
    
    public int getCurrentStreak() {
        return currentStreak;
    }
    
    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }
    
    public int getTotalLPGain() {
        return totalLPGain;
    }
    
    public void setTotalLPGain(int totalLPGain) {
        this.totalLPGain = totalLPGain;
    }
    
    public double getAvgLPGain() {
        return avgLPGain;
    }
    
    public void setAvgLPGain(double avgLPGain) {
        this.avgLPGain = avgLPGain;
    }
    
    public Map<Integer, ChampionStats> getChampionStats() {
        return championStats;
    }
    
    public void setChampionStats(Map<Integer, ChampionStats> championStats) {
        this.championStats = championStats;
    }
    
    public void addChampionStats(int championId, ChampionStats stats) {
        this.championStats.put(championId, stats);
    }
    
    public ChampionStats getChampionStats(int championId) {
        return this.championStats.get(championId);
    }
    
    public Map<String, LaneStats> getLaneStats() {
        return laneStats;
    }
    
    public void setLaneStats(Map<String, LaneStats> laneStats) {
        this.laneStats = laneStats;
    }
    
    public void addLaneStats(String lane, LaneStats stats) {
        this.laneStats.put(lane, stats);
    }
    
    public LaneStats getLaneStats(String lane) {
        return this.laneStats.get(lane);
    }
    
    public RecentForm getRecentForm() {
        return recentForm;
    }
    
    public void setRecentForm(RecentForm recentForm) {
        this.recentForm = recentForm;
    }
    
    /**
     * Check if the cached stats are still fresh based on last match time
     * 
     * @param currentLastMatchTime The current last match time from database
     * @return true if stats need recalculation, false otherwise
     */
    public boolean isStale(long currentLastMatchTime) {
        return this.lastMatchTime < currentLastMatchTime;
    }
    
    /**
     * Check if the cache has expired based on time
     * 
     * @param ttlMillis Time to live in milliseconds
     * @return true if expired, false otherwise
     */
    public boolean isExpired(long ttlMillis) {
        return (System.currentTimeMillis() - this.calculatedAt) > ttlMillis;
    }
    
    @Override
    public String toString() {
        return String.format(
            "PlayerStats{summonerId=%d, games=%d, wins=%d, losses=%d, winRate=%.2f%%, avgKDA=%.2f, avgCSM=%.2f, avgGPM=%.2f}",
            summonerId, totalGames, totalWins, totalLosses, winRate * 100, avgKDA, avgCSM, avgGPM
        );
    }
    
    /**
     * Champion-specific statistics
     */
    public static class ChampionStats {
        private int championId;
        private int games;
        private int wins;
        private int losses;
        private double winRate;
        private double avgKDA;
        private String mostPlayedLane;
        
        public ChampionStats(int championId) {
            this.championId = championId;
        }
        
        // Getters and Setters
        
        public int getChampionId() {
            return championId;
        }
        
        public void setChampionId(int championId) {
            this.championId = championId;
        }
        
        public int getGames() {
            return games;
        }
        
        public void setGames(int games) {
            this.games = games;
        }
        
        public int getWins() {
            return wins;
        }
        
        public void setWins(int wins) {
            this.wins = wins;
        }
        
        public int getLosses() {
            return losses;
        }
        
        public void setLosses(int losses) {
            this.losses = losses;
        }
        
        public double getWinRate() {
            return winRate;
        }
        
        public void setWinRate(double winRate) {
            this.winRate = winRate;
        }
        
        public double getAvgKDA() {
            return avgKDA;
        }
        
        public void setAvgKDA(double avgKDA) {
            this.avgKDA = avgKDA;
        }
        
        public String getMostPlayedLane() {
            return mostPlayedLane;
        }
        
        public void setMostPlayedLane(String mostPlayedLane) {
            this.mostPlayedLane = mostPlayedLane;
        }
    }
    
    /**
     * Lane-specific statistics
     */
    public static class LaneStats {
        private String lane;
        private int games;
        private int wins;
        private int losses;
        private double winRate;
        
        public LaneStats(String lane) {
            this.lane = lane;
        }
        
        // Getters and Setters
        
        public String getLane() {
            return lane;
        }
        
        public void setLane(String lane) {
            this.lane = lane;
        }
        
        public int getGames() {
            return games;
        }
        
        public void setGames(int games) {
            this.games = games;
        }
        
        public int getWins() {
            return wins;
        }
        
        public void setWins(int wins) {
            this.wins = wins;
        }
        
        public int getLosses() {
            return losses;
        }
        
        public void setLosses(int losses) {
            this.losses = losses;
        }
        
        public double getWinRate() {
            return winRate;
        }
        
        public void setWinRate(double winRate) {
            this.winRate = winRate;
        }
    }
    
    /**
     * Recent form statistics (last 20 games)
     */
    public static class RecentForm {
        private int last20Wins;
        private int last20Losses;
        private double last20WinRate;
        private int last7DaysGames;
        private int last7DaysWins;
        
        // Getters and Setters
        
        public int getLast20Wins() {
            return last20Wins;
        }
        
        public void setLast20Wins(int last20Wins) {
            this.last20Wins = last20Wins;
        }
        
        public int getLast20Losses() {
            return last20Losses;
        }
        
        public void setLast20Losses(int last20Losses) {
            this.last20Losses = last20Losses;
        }
        
        public double getLast20WinRate() {
            return last20WinRate;
        }
        
        public void setLast20WinRate(double last20WinRate) {
            this.last20WinRate = last20WinRate;
        }
        
        public int getLast7DaysGames() {
            return last7DaysGames;
        }
        
        public void setLast7DaysGames(int last7DaysGames) {
            this.last7DaysGames = last7DaysGames;
        }
        
        public int getLast7DaysWins() {
            return last7DaysWins;
        }
        
        public void setLast7DaysWins(int last7DaysWins) {
            this.last7DaysWins = last7DaysWins;
        }
    }
}
