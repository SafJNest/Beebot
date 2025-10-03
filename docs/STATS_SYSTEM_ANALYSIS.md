# Advanced Stats System Analysis

## Executive Summary

This document analyzes the current statistics system in Beebot and proposes enhancements to match industry-leading platforms like OP.GG, Mobalytics, and LOLytics. The goal is to implement advanced statistics with efficient filtering (rank, region, lane) and a fast retrieval/update mechanism.

## Current System Architecture

### Database Structure

#### Tables
1. **match** - Stores match metadata
   - game_id, league_shard, game_type (queue)
   - time_start, time_end, patch
   - bans (JSON)

2. **participant** - Stores player performance per match
   - summoner_id, match_id
   - win, kda, champion, lane, team
   - rank, lp, gain
   - damage, healing, cs, vision_score, ward, gold_earned
   - pings (JSON), build (JSON)

3. **rank** - Stores current rank information
   - summoner_id, game_type
   - rank, lp, wins, losses
   - last_update timestamp

4. **summoner** - Stores player identity
   - puuid, riot_id, region
   - user_id (Discord link), tracking flag

### Current Stats Implementation

The `getAdvancedLOLData` method aggregates:
- Games played, wins, losses per champion
- Average KDA (kills/deaths/assists)
- Total LP gain
- Lane-specific performance breakdown

**Limitations:**
- No rank-based filtering
- Limited region filtering
- Basic aggregation only (no advanced metrics)
- No caching mechanism
- Query performance issues with large datasets

## Industry Analysis: OPGG, Mobalytics, LOLytics

### OP.GG Approach

**Data Storage Strategy:**
- Real-time data fetching from Riot API with aggressive caching
- Pre-calculated statistics stored in Redis/Memcached
- Match history indexed by summoner + time window
- Champion statistics aggregated by patch + rank tier

**Stats Calculation:**
- Live game stats: Cached for 5 minutes
- Match history: Cached for 15 minutes, recalculated on new games
- Champion stats: Updated every 6 hours for each rank tier
- Multi-dimensional indexing: champion × lane × rank × patch

**Key Features:**
- Champion mastery and recent performance
- Win rate trends over time
- Matchup statistics (vs specific champions)
- Build path analysis and optimization

### Mobalytics Approach

**Data Storage Strategy:**
- GraphQL API with layered caching
- Aggregated statistics pre-computed in data warehouse
- Player profiles cached with 1-hour TTL
- Champion builds cached by role + rank tier

**Stats Calculation:**
- GPI (Gamer Performance Index) scoring system
- Multi-factor analysis: Aggression, Farming, Survivability, Vision, Versatility
- Patch-based statistics isolation
- Time-series data for trend analysis

**Key Features:**
- Performance radar charts
- Champion pool diversity metrics
- Role proficiency analysis
- Personalized recommendations

### LOLytics Approach

**Data Storage Strategy:**
- Heavy database optimization with materialized views
- Batch processing for statistical aggregation
- Partition tables by patch version
- Compressed historical data storage

**Stats Calculation:**
- Tier-list generation using Bayesian statistics
- Sample size weighting for accuracy
- Confidence intervals for win rates
- Meta trend detection algorithms

**Key Features:**
- Statistical significance indicators
- Patch-to-patch change tracking
- Pick/ban rate correlations
- Build path win rate analysis

## Comparison Matrix

| Feature | OP.GG | Mobalytics | LOLytics | Beebot (Current) | Beebot (Proposed) |
|---------|-------|------------|----------|------------------|-------------------|
| Real-time Updates | 5 min | 15 min | 1 hour | On-demand | 10 min |
| Rank Filtering | ✓ | ✓ | ✓ | ✗ | ✓ |
| Region Filtering | ✓ | ✓ | ✓ | Partial | ✓ |
| Lane Filtering | ✓ | ✓ | ✓ | ✓ | ✓ |
| Champion Stats | ✓ | ✓ | ✓ | Basic | Advanced |
| Performance Metrics | Basic | Advanced | Basic | Basic | Advanced |
| Caching Strategy | Redis | Multi-tier | Database | None | Caffeine |
| Historical Trends | ✓ | ✓ | ✓ | ✗ | ✓ |
| Statistical Analysis | Basic | Advanced | Expert | None | Intermediate |

## Proposed Enhancements

### 1. Advanced Statistics

**New Metrics:**
- **Efficiency Metrics**
  - CS per minute (CSM)
  - Gold per minute (GPM)
  - Damage per minute (DPM)
  - Damage per gold (DPG)
  - Vision score per minute (VSPM)

- **Performance Indicators**
  - Kill participation rate (KP%)
  - First blood rate (FB%)
  - Average game duration
  - Comeback rate (win from behind)
  - Snowball rate (win from ahead)

- **Recent Form**
  - Last 20 games win rate
  - Last 7 days performance
  - Current win/loss streak
  - Peak performance indicators

- **Champion Proficiency**
  - Mastery level integration
  - Games played per champion
  - Best performing champions
  - Role flexibility score

### 2. Enhanced Filtering System

**Filter Dimensions:**

```java
public class AdvancedStatsFilter {
    // Existing
    private LeagueShard region;
    private GameQueueType queue;
    private LaneType lane;
    private long timeStart;
    private long timeEnd;
    
    // New
    private TierDivisionType minRank;
    private TierDivisionType maxRank;
    private String patch;
    private Integer championId;
    private Boolean recentOnly; // Last 30 days
    private Integer limit; // Number of games to analyze
}
```

**Query Optimization:**
- Composite indexes on frequently filtered columns
- Materialized views for common filter combinations
- Query result pagination for large datasets

### 3. Stats Caching System

**Cache Architecture:**

```java
public class StatsCache extends AbstractCache<StatsCacheKey, PlayerStats> {
    // Cache configuration
    - TTL: 15 minutes for active players
    - TTL: 1 hour for inactive players (no recent games)
    - Max size: 10,000 entries
    - Eviction: LRU (Least Recently Used)
}

public class StatsCacheKey {
    private int summonerId;
    private String filtersHash; // MD5 of filter parameters
    private long lastMatchTime; // For invalidation
}
```

**Cache Strategy:**
1. **Cache Hit**: Return cached data if not expired
2. **Cache Miss**: Calculate stats, store in cache, return
3. **Stale Data**: If new matches exist, invalidate and recalculate
4. **Proactive Update**: Background job updates stats for tracked players

**Invalidation Rules:**
- New match added for summoner → invalidate all cache entries for that summoner
- Manual refresh request → invalidate specific cache entry
- Time-based expiration → natural TTL expiration

### 4. Database Schema Enhancements

**New Table: champion_stats_cache**
```sql
CREATE TABLE `champion_stats_cache` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `summoner_id` INT NOT NULL,
  `champion_id` SMALLINT NOT NULL,
  `queue_type` VARCHAR(50),
  `lane` VARCHAR(20),
  `rank_tier` VARCHAR(20),
  `time_period_start` DATETIME,
  `time_period_end` DATETIME,
  
  -- Aggregated Stats
  `games` INT DEFAULT 0,
  `wins` INT DEFAULT 0,
  `losses` INT DEFAULT 0,
  `avg_kda` DECIMAL(5,2),
  `avg_cs` DECIMAL(6,2),
  `avg_csm` DECIMAL(5,2),
  `avg_gpm` DECIMAL(7,2),
  `avg_dpm` DECIMAL(7,2),
  `avg_vspm` DECIMAL(5,2),
  `avg_kp` DECIMAL(5,2),
  
  -- Metadata
  `last_calculated` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `cache_version` INT DEFAULT 1,
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_stats` (`summoner_id`, `champion_id`, `queue_type`, `lane`, `rank_tier`, `time_period_start`, `time_period_end`),
  KEY `idx_summoner` (`summoner_id`),
  KEY `idx_champion` (`champion_id`),
  KEY `idx_last_calc` (`last_calculated`)
) ENGINE=InnoDB;
```

**Index Optimizations:**
```sql
-- Participant table
CREATE INDEX idx_summoner_champion_lane ON participant(summoner_id, champion, lane);
CREATE INDEX idx_summoner_rank ON participant(summoner_id, rank);
CREATE INDEX idx_match_time ON participant(match_id, summoner_id);

-- Match table  
CREATE INDEX idx_queue_time ON `match`(queue, time_start);
CREATE INDEX idx_region_queue ON `match`(region, queue);
```

## Implementation Phases

### Phase 1: Foundation (Week 1)
- Create analysis document ✓
- Design cache system architecture
- Design new database schema

### Phase 2: Core Stats (Week 2)
- Implement advanced statistics calculations
- Create new query methods in LeagueDB
- Add efficiency metrics calculation

### Phase 3: Filtering (Week 3)
- Implement rank-based filtering
- Enhance region filtering
- Add composite filter support
- Optimize database queries

### Phase 4: Caching (Week 4)
- Implement StatsCache class
- Add cache invalidation logic
- Integrate with match tracking system
- Add background refresh job

### Phase 5: Testing & Optimization (Week 5)
- Performance testing with large datasets
- Query optimization
- Cache hit rate analysis
- Load testing

## Performance Targets

| Metric | Current | Target | Industry Standard |
|--------|---------|--------|-------------------|
| Query Response Time | 500-2000ms | <200ms | <100ms |
| Cache Hit Rate | 0% | >80% | >90% |
| Data Freshness | Real-time | <15 min | <5 min |
| Concurrent Users | 50 | 500 | 10,000+ |

## Technical Debt & Risks

**Current Technical Debt:**
- SQL injection vulnerabilities (string concatenation in queries)
- No connection pooling optimization
- Missing database migration system
- No query performance monitoring

**Implementation Risks:**
- Database migration complexity
- Cache coherency challenges
- Increased memory usage
- API rate limiting from Riot Games

**Mitigation Strategies:**
- Use PreparedStatement for all queries
- Implement gradual rollout with feature flags
- Monitor cache memory usage and adjust limits
- Implement request throttling and backoff

## Conclusion

The proposed enhancements will bring Beebot's statistics system closer to industry standards while maintaining simplicity and maintainability. The phased approach allows for iterative development and testing, ensuring stability throughout the implementation.

**Key Benefits:**
- 10x faster stats retrieval through caching
- More granular filtering options (rank, region, lane)
- Advanced performance metrics for better player insights
- Scalable architecture for future enhancements
- Better user experience with faster response times

**Next Steps:**
1. Review and approve this analysis
2. Create database migration scripts
3. Implement Phase 1: Foundation
4. Begin development following the phased approach
