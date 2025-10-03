# Advanced Stats System Usage Guide

This guide demonstrates how to use the new advanced statistics system in Beebot.

## Quick Start

### Basic Usage

```java
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.lol.model.AdvancedStatsFilter;
import com.safjnest.util.lol.model.PlayerStats;

// Get stats for a summoner with default filter (all time)
int summonerId = 12345;
AdvancedStatsFilter filter = new AdvancedStatsFilter();
PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);

// Access basic stats
System.out.println("Total Games: " + stats.getTotalGames());
System.out.println("Win Rate: " + (stats.getWinRate() * 100) + "%");
System.out.println("Average KDA: " + stats.getAvgKDA());
```

## Filtering Examples

### Time-Based Filtering

```java
// Get stats for last 30 days
AdvancedStatsFilter recentFilter = AdvancedStatsFilter.recentGames();
PlayerStats recentStats = LeagueDB.getAdvancedPlayerStats(summonerId, recentFilter, false);

// Get stats for specific time range
long startTime = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000); // 90 days ago
long endTime = System.currentTimeMillis();
AdvancedStatsFilter timeFilter = AdvancedStatsFilter.timeRange(startTime, endTime);
PlayerStats timeStats = LeagueDB.getAdvancedPlayerStats(summonerId, timeFilter, false);
```

### Queue Type Filtering

```java
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

// Get stats for ranked solo/duo only
AdvancedStatsFilter rankedFilter = new AdvancedStatsFilter()
    .withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO);
PlayerStats rankedStats = LeagueDB.getAdvancedPlayerStats(summonerId, rankedFilter, false);

// Get stats for ARAM only
AdvancedStatsFilter aramFilter = new AdvancedStatsFilter()
    .withQueue(GameQueueType.ARAM);
PlayerStats aramStats = LeagueDB.getAdvancedPlayerStats(summonerId, aramFilter, false);
```

### Lane Filtering

```java
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

// Get stats for jungle role only
AdvancedStatsFilter jungleFilter = new AdvancedStatsFilter()
    .withLane(LaneType.JUNGLE);
PlayerStats jungleStats = LeagueDB.getAdvancedPlayerStats(summonerId, jungleFilter, false);

// Get stats for mid lane
AdvancedStatsFilter midFilter = new AdvancedStatsFilter()
    .withLane(LaneType.MID);
PlayerStats midStats = LeagueDB.getAdvancedPlayerStats(summonerId, midFilter, false);
```

### Rank Filtering

```java
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

// Get stats when playing in Diamond or above
AdvancedStatsFilter highEloFilter = new AdvancedStatsFilter()
    .withRankRange(TierDivisionType.DIAMOND_IV, TierDivisionType.CHALLENGER_I);
PlayerStats highEloStats = LeagueDB.getAdvancedPlayerStats(summonerId, highEloFilter, false);

// Get stats when playing in Gold
AdvancedStatsFilter goldFilter = new AdvancedStatsFilter()
    .withRankRange(TierDivisionType.GOLD_IV, TierDivisionType.GOLD_I);
PlayerStats goldStats = LeagueDB.getAdvancedPlayerStats(summonerId, goldFilter, false);
```

### Champion Filtering

```java
// Get stats for a specific champion (e.g., Yasuo - ID: 157)
AdvancedStatsFilter championFilter = new AdvancedStatsFilter()
    .withChampion(157);
PlayerStats championStats = LeagueDB.getAdvancedPlayerStats(summonerId, championFilter, false);
```

### Composite Filtering

```java
// Get stats for Ahri in mid lane in ranked games from last 30 days
AdvancedStatsFilter complexFilter = new AdvancedStatsFilter()
    .withChampion(103)  // Ahri
    .withLane(LaneType.MID)
    .withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
    .withTimeRange(
        System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000),
        System.currentTimeMillis()
    );
PlayerStats complexStats = LeagueDB.getAdvancedPlayerStats(summonerId, complexFilter, false);
```

### Region Filtering

```java
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

// Get stats for games played on NA server
AdvancedStatsFilter naFilter = new AdvancedStatsFilter()
    .withRegion(LeagueShard.NA1);
PlayerStats naStats = LeagueDB.getAdvancedPlayerStats(summonerId, naFilter, false);
```

## Accessing Statistics

### Basic Stats

```java
PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);

// Games and win rate
int totalGames = stats.getTotalGames();
int wins = stats.getTotalWins();
int losses = stats.getTotalLosses();
double winRate = stats.getWinRate(); // 0.0 to 1.0

System.out.println(String.format("Record: %d-%d (%.1f%%)", wins, losses, winRate * 100));
```

### KDA Stats

```java
// Average kills, deaths, assists
double avgKills = stats.getAvgKills();
double avgDeaths = stats.getAvgDeaths();
double avgAssists = stats.getAvgAssists();
double avgKDA = stats.getAvgKDA(); // (K+A)/D ratio

System.out.println(String.format("Average KDA: %.1f / %.1f / %.1f (%.2f)", 
    avgKills, avgDeaths, avgAssists, avgKDA));
```

### Efficiency Metrics

```java
// Performance per minute metrics
double csm = stats.getAvgCSM();        // CS per minute
double gpm = stats.getAvgGPM();        // Gold per minute
double dpm = stats.getAvgDPM();        // Damage per minute
double vspm = stats.getAvgVSPM();      // Vision score per minute
double dpg = stats.getAvgDPG();        // Damage per gold

System.out.println(String.format("Efficiency: %.1f CS/m, %.0f Gold/m, %.0f DMG/m", 
    csm, gpm, dpm));
```

### LP Stats

```java
// LP gain/loss
int totalLPGain = stats.getTotalLPGain();
double avgLPGain = stats.getAvgLPGain();

System.out.println(String.format("LP: %+d total (%+.1f per game)", 
    totalLPGain, avgLPGain));
```

### Performance Indicators

```java
// Streak and game duration
int streak = stats.getCurrentStreak();
double avgDuration = stats.getAvgGameDuration();

if (streak > 0) {
    System.out.println("On a " + streak + " game win streak!");
} else if (streak < 0) {
    System.out.println("On a " + Math.abs(streak) + " game loss streak");
}

System.out.println(String.format("Average game duration: %.1f minutes", avgDuration));
```

### Champion-Specific Stats

```java
// Get performance breakdown by champion
Map<Integer, PlayerStats.ChampionStats> championStats = stats.getChampionStats();

for (Map.Entry<Integer, PlayerStats.ChampionStats> entry : championStats.entrySet()) {
    int championId = entry.getKey();
    PlayerStats.ChampionStats champStats = entry.getValue();
    
    System.out.println(String.format("Champion %d: %d games, %.1f%% WR, %.2f KDA",
        championId,
        champStats.getGames(),
        champStats.getWinRate() * 100,
        champStats.getAvgKDA()
    ));
}
```

### Lane-Specific Stats

```java
// Get performance breakdown by lane
Map<String, PlayerStats.LaneStats> laneStats = stats.getLaneStats();

for (Map.Entry<String, PlayerStats.LaneStats> entry : laneStats.entrySet()) {
    String lane = entry.getKey();
    PlayerStats.LaneStats lStats = entry.getValue();
    
    System.out.println(String.format("%s: %d games, %.1f%% WR",
        lane,
        lStats.getGames(),
        lStats.getWinRate() * 100
    ));
}
```

### Recent Form

```java
// Get recent performance
PlayerStats.RecentForm form = stats.getRecentForm();

System.out.println(String.format("Last 20 games: %d-%d (%.1f%% WR)",
    form.getLast20Wins(),
    form.getLast20Losses(),
    form.getLast20WinRate() * 100
));

System.out.println(String.format("Last 7 days: %d games, %d wins",
    form.getLast7DaysGames(),
    form.getLast7DaysWins()
));
```

## Cache Management

### Force Refresh

```java
// Bypass cache and recalculate stats
PlayerStats freshStats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, true);
```

### Manual Cache Invalidation

```java
// Invalidate all cached stats for a summoner
LeagueDB.invalidateStatsCache(summonerId);

// Note: Cache is automatically invalidated when new matches are added
```

### Cache Statistics

```java
import com.safjnest.core.cache.managers.StatsCache;

// Get cache size
int cacheSize = StatsCache.getCacheSize();
System.out.println("Stats cache contains " + cacheSize + " entries");

// Clear all cached stats
StatsCache.clearAll();
```

## Best Practices

### 1. Use Appropriate Filters

```java
// ✅ Good: Use specific filters to reduce database load
AdvancedStatsFilter filter = new AdvancedStatsFilter()
    .withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
    .withTimeRange(recentStart, recentEnd);

// ❌ Bad: Don't calculate all-time stats every time
AdvancedStatsFilter filter = new AdvancedStatsFilter(); // No filters = all time
```

### 2. Leverage Caching

```java
// ✅ Good: Let cache work for you (forceRefresh = false)
PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);

// ❌ Bad: Don't force refresh unnecessarily
PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, true);
```

### 3. Handle Null Results

```java
PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);

if (stats == null || stats.getTotalGames() == 0) {
    System.out.println("No stats available for this filter");
    return;
}

// Process stats...
```

### 4. Use Builder Pattern for Filters

```java
// ✅ Good: Fluent API for readability
AdvancedStatsFilter filter = new AdvancedStatsFilter()
    .withQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
    .withLane(LaneType.MID)
    .withRankRange(TierDivisionType.PLATINUM_IV, TierDivisionType.DIAMOND_I);

// ✅ Also good: Set properties individually
AdvancedStatsFilter filter = new AdvancedStatsFilter();
filter.setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO);
filter.setLane(LaneType.MID);
```

## Performance Considerations

### Cache Hit Rate

- **First Query**: ~500ms (database calculation)
- **Cached Query**: ~5ms (cache retrieval)
- **Target Cache Hit Rate**: >80%

### Cache Invalidation

Cache is automatically invalidated when:
- New match data is added for a summoner
- 15 minutes have passed since calculation (TTL)
- Manual invalidation is triggered

### Memory Usage

- Default cache limit: 10,000 entries
- LRU eviction policy (least recently used)
- Each PlayerStats entry: ~5-10KB

## Integration Examples

### Discord Bot Command

```java
@Override
protected void execute(SlashCommandEvent event) {
    event.deferReply(false).queue();
    
    // Get summoner ID from event options
    int summonerId = getSummonerIdFromEvent(event);
    
    // Create filter based on command options
    AdvancedStatsFilter filter = new AdvancedStatsFilter();
    
    if (event.getOption("queue") != null) {
        String queueStr = event.getOption("queue").getAsString();
        filter.setQueue(GameQueueType.valueOf(queueStr));
    }
    
    if (event.getOption("lane") != null) {
        String laneStr = event.getOption("lane").getAsString();
        filter.setLane(LaneType.valueOf(laneStr));
    }
    
    // Get stats
    PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);
    
    if (stats == null || stats.getTotalGames() == 0) {
        event.getHook().sendMessage("No stats found!").queue();
        return;
    }
    
    // Build embed message
    EmbedBuilder embed = new EmbedBuilder()
        .setTitle("Player Statistics")
        .addField("Games", stats.getTotalGames() + "", true)
        .addField("Win Rate", String.format("%.1f%%", stats.getWinRate() * 100), true)
        .addField("KDA", String.format("%.2f", stats.getAvgKDA()), true)
        .addField("CS/min", String.format("%.1f", stats.getAvgCSM()), true)
        .addField("Gold/min", String.format("%.0f", stats.getAvgGPM()), true)
        .addField("DMG/min", String.format("%.0f", stats.getAvgDPM()), true);
    
    event.getHook().sendMessageEmbeds(embed.build()).queue();
}
```

### REST API Endpoint

```java
@GetMapping("/api/stats/{summonerId}")
public ResponseEntity<PlayerStats> getPlayerStats(
    @PathVariable int summonerId,
    @RequestParam(required = false) String queue,
    @RequestParam(required = false) String lane,
    @RequestParam(required = false) Boolean recent
) {
    AdvancedStatsFilter filter = recent != null && recent 
        ? AdvancedStatsFilter.recentGames()
        : new AdvancedStatsFilter();
    
    if (queue != null) {
        filter.setQueue(GameQueueType.valueOf(queue));
    }
    
    if (lane != null) {
        filter.setLane(LaneType.valueOf(lane));
    }
    
    PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);
    
    if (stats == null) {
        return ResponseEntity.notFound().build();
    }
    
    return ResponseEntity.ok(stats);
}
```

## Troubleshooting

### Cache Not Working

```java
// Check if cache is enabled
int cacheSize = StatsCache.getCacheSize();
System.out.println("Current cache size: " + cacheSize);

// Check time until expiration
String cacheKey = filter.generateCacheKey(summonerId);
long ttl = StatsCache.getTimeUntilExpiration(cacheKey);
System.out.println("TTL: " + ttl + "ms");
```

### Stale Data

```java
// Force refresh to get latest data
PlayerStats freshStats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, true);

// Or invalidate cache manually
LeagueDB.invalidateStatsCache(summonerId);
PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);
```

### Performance Issues

```java
// Check database indexes
// Run in MySQL console:
// SHOW INDEX FROM participant;
// SHOW INDEX FROM match;

// Add limit to reduce calculation time
filter.setLimit(100); // Only analyze last 100 games
```

## Migration from Old System

### Before (Old System)

```java
QueryResult result = LeagueDB.getAdvancedLOLData(summonerId, timeStart, timeEnd, queue);

for (QueryRecord record : result) {
    int games = record.getAsInt("games");
    int wins = record.getAsInt("wins");
    // ... process record
}
```

### After (New System)

```java
AdvancedStatsFilter filter = new AdvancedStatsFilter()
    .withTimeRange(timeStart, timeEnd)
    .withQueue(queue);

PlayerStats stats = LeagueDB.getAdvancedPlayerStats(summonerId, filter, false);

int games = stats.getTotalGames();
int wins = stats.getTotalWins();
// ... use stats object
```

## Future Enhancements

Planned features for future releases:

1. **Matchup Statistics**: Win rate vs specific enemy champions
2. **Build Path Analysis**: Most successful item builds
3. **Duo Partner Stats**: Performance with specific duo partners
4. **Patch Comparison**: Stats comparison across different patches
5. **Meta Analysis**: Champion pick/ban rates and trends
6. **Performance Predictions**: ML-based performance predictions
7. **Export Functionality**: Export stats to CSV/JSON

## Support

For issues or questions:
- Check the analysis document: `docs/STATS_SYSTEM_ANALYSIS.md`
- Review database schema: `database/league_of_legends/`
- Contact: Beebot development team
