package com.safjnest.core.cache.managers;

import com.safjnest.core.cache.AbstractCache;
import com.safjnest.util.lol.model.PlayerStats;

import java.util.concurrent.TimeUnit;

/**
 * Cache for player statistics to reduce database load and improve performance.
 * 
 * This cache stores aggregated player statistics with configurable TTL based on 
 * data freshness. Active players (with recent matches) have shorter TTL to ensure
 * data accuracy, while inactive players have longer TTL to reduce unnecessary 
 * database queries.
 * 
 * @author SafJNest Team
 * @since 10.1
 */
public class StatsCache extends AbstractCache<String, PlayerStats> {
    
    private static StatsCache instance;
    
    static {
        instance = new StatsCache();
    }
    
    private StatsCache() {
        // Set cache limits and expiration
        setTypeLimit(10000); // Maximum 10,000 cached stats entries
        setExpireTime(15, TimeUnit.MINUTES); // Default TTL: 15 minutes
    }
    
    public static StatsCache getInstance() {
        return instance;
    }
    
    /**
     * Retrieve cached stats for a player
     * 
     * @param key Cache key (summoner_id + filter hash)
     * @return PlayerStats if cached and not expired, null otherwise
     */
    public static PlayerStats getStats(String key) {
        return instance.get(key);
    }
    
    /**
     * Store player stats in cache
     * 
     * @param key Cache key
     * @param stats Player statistics to cache
     */
    public static void putStats(String key, PlayerStats stats) {
        instance.put(key, stats);
    }
    
    /**
     * Check if stats exist in cache
     * 
     * @param key Cache key
     * @return true if stats are cached, false otherwise
     */
    public static boolean hasStats(String key) {
        return instance.contains(key);
    }
    
    /**
     * Invalidate cached stats for a summoner (all filter combinations)
     * 
     * @param summonerId The summoner ID to invalidate
     */
    public static void invalidateSummoner(int summonerId) {
        String prefix = "summoner:" + summonerId + ":";
        instance.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .forEach(instance::invalidate);
    }
    
    /**
     * Clear all cached statistics
     */
    public static void clearAll() {
        instance.invalidateAll();
    }
    
    /**
     * Get the number of cached entries
     * 
     * @return Number of cached player stats
     */
    public static int getCacheSize() {
        return instance.getTypeSize(PlayerStats.class);
    }
    
    /**
     * Check how long until a cache entry expires
     * 
     * @param key Cache key
     * @return Milliseconds until expiration, 0 if not found
     */
    public static long getTimeUntilExpiration(String key) {
        return instance.expiresAfter(key);
    }
    
    @Override
    protected Class<String> getKeyType() {
        return String.class;
    }
    
    @Override
    protected Class<PlayerStats> getValueType() {
        return PlayerStats.class;
    }
}
