package com.safjnest.util.lol.model;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Enhanced filter for advanced statistics queries with support for
 * rank, region, lane, queue, time period, and champion filtering.
 * 
 * This class provides a comprehensive filtering system for player statistics
 * and includes a hash generation method for cache key creation.
 * 
 * @author SafJNest Team
 * @since 10.1
 */
public class AdvancedStatsFilter {
    
    // Existing filters
    private LeagueShard region;
    private GameQueueType queue;
    private LaneType lane;
    private long timeStart;
    private long timeEnd;
    
    // New filters
    private TierDivisionType minRank;
    private TierDivisionType maxRank;
    private String patch;
    private Integer championId;
    private Boolean recentOnly;  // Last 30 days
    private Integer limit;       // Number of games to analyze
    
    public AdvancedStatsFilter() {
        this.timeStart = 0;
        this.timeEnd = System.currentTimeMillis();
        this.recentOnly = false;
    }
    
    /**
     * Create a filter for recent games (last 30 days)
     * 
     * @return Filter configured for last 30 days
     */
    public static AdvancedStatsFilter recentGames() {
        AdvancedStatsFilter filter = new AdvancedStatsFilter();
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000); // 30 days in milliseconds
        filter.setTimeStart(thirtyDaysAgo);
        filter.setTimeEnd(now);
        filter.setRecentOnly(true);
        return filter;
    }
    
    /**
     * Create a filter for a specific time range
     * 
     * @param timeStart Start time in milliseconds
     * @param timeEnd End time in milliseconds
     * @return Filter configured for time range
     */
    public static AdvancedStatsFilter timeRange(long timeStart, long timeEnd) {
        AdvancedStatsFilter filter = new AdvancedStatsFilter();
        filter.setTimeStart(timeStart);
        filter.setTimeEnd(timeEnd);
        return filter;
    }
    
    /**
     * Generate a hash string for cache key creation
     * This hash represents the unique combination of filter parameters
     * 
     * @return MD5 hash of filter parameters
     */
    public String generateHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(region != null ? region.name() : "ALL");
        sb.append("|");
        sb.append(queue != null ? queue.name() : "ALL");
        sb.append("|");
        sb.append(lane != null ? lane.name() : "ALL");
        sb.append("|");
        sb.append(timeStart);
        sb.append("|");
        sb.append(timeEnd);
        sb.append("|");
        sb.append(minRank != null ? minRank.name() : "NONE");
        sb.append("|");
        sb.append(maxRank != null ? maxRank.name() : "NONE");
        sb.append("|");
        sb.append(patch != null ? patch : "ALL");
        sb.append("|");
        sb.append(championId != null ? championId : "ALL");
        sb.append("|");
        sb.append(recentOnly != null && recentOnly ? "RECENT" : "ALL");
        sb.append("|");
        sb.append(limit != null ? limit : "UNLIMITED");
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(sb.toString().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if MD5 not available
            return String.valueOf(sb.toString().hashCode());
        }
    }
    
    /**
     * Generate cache key for this filter with summoner ID
     * 
     * @param summonerId The summoner ID
     * @return Cache key string
     */
    public String generateCacheKey(int summonerId) {
        return "summoner:" + summonerId + ":" + generateHash();
    }
    
    // Builder-style methods for fluent API
    
    public AdvancedStatsFilter withRegion(LeagueShard region) {
        this.region = region;
        return this;
    }
    
    public AdvancedStatsFilter withQueue(GameQueueType queue) {
        this.queue = queue;
        return this;
    }
    
    public AdvancedStatsFilter withLane(LaneType lane) {
        this.lane = lane;
        return this;
    }
    
    public AdvancedStatsFilter withTimeRange(long timeStart, long timeEnd) {
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        return this;
    }
    
    public AdvancedStatsFilter withRankRange(TierDivisionType minRank, TierDivisionType maxRank) {
        this.minRank = minRank;
        this.maxRank = maxRank;
        return this;
    }
    
    public AdvancedStatsFilter withPatch(String patch) {
        this.patch = patch;
        return this;
    }
    
    public AdvancedStatsFilter withChampion(Integer championId) {
        this.championId = championId;
        return this;
    }
    
    public AdvancedStatsFilter withLimit(Integer limit) {
        this.limit = limit;
        return this;
    }
    
    // Getters and Setters
    
    public LeagueShard getRegion() {
        return region;
    }
    
    public void setRegion(LeagueShard region) {
        this.region = region;
    }
    
    public GameQueueType getQueue() {
        return queue;
    }
    
    public void setQueue(GameQueueType queue) {
        this.queue = queue;
    }
    
    public LaneType getLane() {
        return lane;
    }
    
    public void setLane(LaneType lane) {
        this.lane = lane;
    }
    
    public long getTimeStart() {
        return timeStart;
    }
    
    public void setTimeStart(long timeStart) {
        this.timeStart = timeStart;
    }
    
    public long getTimeEnd() {
        return timeEnd;
    }
    
    public void setTimeEnd(long timeEnd) {
        this.timeEnd = timeEnd;
    }
    
    public TierDivisionType getMinRank() {
        return minRank;
    }
    
    public void setMinRank(TierDivisionType minRank) {
        this.minRank = minRank;
    }
    
    public TierDivisionType getMaxRank() {
        return maxRank;
    }
    
    public void setMaxRank(TierDivisionType maxRank) {
        this.maxRank = maxRank;
    }
    
    public String getPatch() {
        return patch;
    }
    
    public void setPatch(String patch) {
        this.patch = patch;
    }
    
    public Integer getChampionId() {
        return championId;
    }
    
    public void setChampionId(Integer championId) {
        this.championId = championId;
    }
    
    public Boolean getRecentOnly() {
        return recentOnly;
    }
    
    public void setRecentOnly(Boolean recentOnly) {
        this.recentOnly = recentOnly;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdvancedStatsFilter that = (AdvancedStatsFilter) o;
        return timeStart == that.timeStart &&
               timeEnd == that.timeEnd &&
               Objects.equals(region, that.region) &&
               Objects.equals(queue, that.queue) &&
               Objects.equals(lane, that.lane) &&
               Objects.equals(minRank, that.minRank) &&
               Objects.equals(maxRank, that.maxRank) &&
               Objects.equals(patch, that.patch) &&
               Objects.equals(championId, that.championId) &&
               Objects.equals(recentOnly, that.recentOnly) &&
               Objects.equals(limit, that.limit);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(region, queue, lane, timeStart, timeEnd, minRank, maxRank, patch, championId, recentOnly, limit);
    }
    
    @Override
    public String toString() {
        return "AdvancedStatsFilter{" +
               "region=" + region +
               ", queue=" + queue +
               ", lane=" + lane +
               ", timeStart=" + timeStart +
               ", timeEnd=" + timeEnd +
               ", minRank=" + minRank +
               ", maxRank=" + maxRank +
               ", patch='" + patch + '\'' +
               ", championId=" + championId +
               ", recentOnly=" + recentOnly +
               ", limit=" + limit +
               '}';
    }
}
