# League Database SQL to JPA Conversion

This document demonstrates the conversion of raw SQL queries from the `LeagueDB` class to Spring Data JPA equivalent methods.

## Database Schema

The League database consists of 5 main tables:
- `summoner` - Stores summoner information
- `match` - Stores match data  
- `participant` - Junction table with match participant data
- `masteries` - Champion mastery data for summoners
- `rank` - Ranking data for summoners

## JPA Entity Classes

### Summoner Entity
```java
@Entity
@Table(name = "summoner")
public class Summoner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "riot_id")
    private String riotId;
    
    @Column(name = "summoner_id", nullable = false)
    private String summonerId;
    // ... other fields with proper JPA annotations
    
    @OneToMany(mappedBy = "summoner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Participant> participants;
}
```

### Match Entity
```java
@Entity
@Table(name = "`match`")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "game_id", nullable = false, unique = true)
    private String gameId;
    // ... other fields
    
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Participant> participants;
}
```

### Participant Entity (Junction Table)
```java
@Entity
@Table(name = "participant")
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summoner_id", nullable = false)
    private Summoner summoner;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;
    // ... other fields
}
```

## SQL to JPA Query Conversion Examples

### 1. Simple SELECT with JOIN

**Original SQL:**
```sql
SELECT st.summoner_id, sm.game_id, st.rank, st.lp, st.gain, st.win, sm.time_start, sm.time_end, sm.patch 
FROM participant st 
JOIN `match` sm ON st.match_id = sm.id 
WHERE st.summoner_id = ? AND sm.game_type = 43 
ORDER BY sm.game_id
```

**JPA Repository Method:**
```java
@Query("SELECT p.summoner.id, p.match.gameId, p.rank, p.lp, p.gain, p.win, p.match.timeStart, p.match.timeEnd, p.match.patch " +
       "FROM Participant p WHERE p.summoner.id = :summonerId AND p.match.gameType = :gameType ORDER BY p.match.gameId")
List<Object[]> findSummonerDataByGameType(@Param("summonerId") Integer summonerId, @Param("gameType") Integer gameType);
```

### 2. Full-Text Search

**Original SQL:**
```sql
SELECT riot_id FROM summoner 
WHERE MATCH(riot_id) AGAINST('+query*' IN BOOLEAN MODE) 
AND league_shard = ? LIMIT 25
```

**JPA Repository Method:**
```java
@Query("SELECT s.riotId FROM Summoner s WHERE s.riotId LIKE CONCAT('%', :query, '%') AND s.leagueShard = :shard")
List<String> findFocusedSummoners(@Param("query") String query, @Param("shard") Integer shard, Pageable pageable);
```

### 3. Complex Aggregation Query

**Original SQL:**
```sql
SELECT `champion`, COUNT(*) AS `games`, SUM(`win`) AS `wins`, 
       SUM(CASE WHEN `win` = 0 THEN 1 ELSE 0 END) AS `losses`,
       AVG(CAST(SUBSTRING_INDEX(`kda`, '/', 1) AS UNSIGNED)) AS avg_kills,
       SUM(`gain`) AS total_lp_gain 
FROM `participant` 
WHERE `summoner_id` = ? 
GROUP BY `champion` 
ORDER BY `games` DESC
```

**JPA Repository Method:**
```java
@Query("SELECT p.champion, COUNT(*) as games, SUM(CASE WHEN p.win = true THEN 1 ELSE 0 END) as wins, " +
       "SUM(CASE WHEN p.win = false THEN 1 ELSE 0 END) as losses, " +
       "SUM(p.gain) as totalLpGain " +
       "FROM Participant p WHERE p.summoner.id = :summonerId " +
       "GROUP BY p.champion ORDER BY games DESC")
List<Object[]> findAdvancedLolDataBySummoner(@Param("summonerId") Integer summonerId);
```

### 4. UPDATE Query

**Original SQL:**
```sql
UPDATE summoner SET tracking = ? WHERE user_id = ? AND puuid = ?
```

**JPA Repository Method:**
```java
@Modifying
@Transactional
@Query("UPDATE Summoner s SET s.tracking = :tracking WHERE s.userId = :userId AND s.puuid = :puuid")
int updateTracking(@Param("userId") String userId, @Param("puuid") String puuid, @Param("tracking") Integer tracking);
```

### 5. Complex JOIN with Subquery

**Original SQL:**
```sql
SELECT s.puuid, s.league_shard, st.game_id, st.rank, st.lp, st.time_start 
FROM summoner s 
LEFT JOIN (SELECT t.summoner_id, t.game_id, t.rank, t.lp, t.time_start, 
           ROW_NUMBER() OVER (PARTITION BY t.summoner_id ORDER BY t.time_start DESC) AS rn 
           FROM participant t 
           JOIN `match` sm ON t.match_id = sm.id 
           WHERE sm.time_start >= ? AND sm.game_type = 43) st 
ON s.id = st.summoner_id AND st.rn = 1 
WHERE s.tracking = 1
```

**JPA Repository Method (Simplified):**
```java
@Query("SELECT s.puuid, s.leagueShard, p.match.gameId, p.rank, p.lp, p.match.timeStart " +
       "FROM Summoner s LEFT JOIN Participant p ON s.id = p.summoner.id " +
       "WHERE s.tracking = 1 AND p.match.timeStart >= :timeStart AND p.match.gameType = 43 " +
       "ORDER BY s.id, p.match.timeStart DESC")
List<Object[]> findRegisteredLolAccountsWithLatestData(@Param("timeStart") LocalDateTime timeStart);
```

## Repository Interfaces

### SummonerRepository
```java
@Repository
public interface SummonerRepository extends JpaRepository<Summoner, Integer> {
    List<Summoner> findByUserIdOrderById(String userId);
    Optional<Summoner> findByPuuidAndLeagueShard(String puuid, Integer leagueShard);
    List<String> findFocusedSummoners(String query, Integer shard, Pageable pageable);
    boolean hasSummonerData(Integer summonerId);
    // ... other methods
}
```

## Service Layer

The `LeagueDataService` class provides JPA-based equivalents to all original LeagueDB methods:

```java
@Service
@Transactional
public class LeagueDataService {
    @Autowired
    private SummonerRepository summonerRepository;
    
    public List<Summoner> getLolAccountsByUserId(String userId) {
        return summonerRepository.findByUserIdOrderById(userId);
    }
    
    public List<String> getFocusedSummoners(String query, Integer leagueShard) {
        Pageable limit25 = PageRequest.of(0, 25);
        return summonerRepository.findFocusedSummoners(query, leagueShard, limit25);
    }
    // ... other service methods
}
```

## Key Conversion Patterns

### 1. JOIN Operations
- SQL JOINs are replaced with JPA entity relationships using `@ManyToOne` and `@OneToMany`
- Navigation through relationships: `p.summoner.id` instead of explicit JOIN

### 2. String Functions
- MySQL `SUBSTRING_INDEX()` functions are simplified or handled in application logic
- Full-text search `MATCH() AGAINST()` converted to `LIKE` queries

### 3. Window Functions
- `ROW_NUMBER() OVER()` simplified to `ORDER BY` with application-level filtering when needed

### 4. INSERT ... ON DUPLICATE KEY UPDATE
- Handled by checking existence with `findBy...()` methods and using `save()` for upsert behavior

### 5. Pagination
- SQL `LIMIT` replaced with Spring Data `Pageable` parameter

## Benefits of JPA Conversion

1. **Type Safety**: Compile-time checking of queries and entity relationships
2. **Database Independence**: Queries work across different database vendors
3. **Automatic Schema Generation**: Hibernate can generate DDL from entities
4. **Lazy Loading**: Automatic optimization of data fetching
5. **Caching**: Built-in first and second-level caching support
6. **Transaction Management**: Declarative transaction handling with `@Transactional`
7. **Query Methods**: Spring Data derives queries from method names automatically
8. **Testing**: Easy to test with in-memory databases like H2

## Migration Strategy

1. Create JPA entities first
2. Create repository interfaces with basic CRUD operations
3. Convert complex queries one by one, starting with simpler ones
4. Create service layer to maintain existing API compatibility
5. Add comprehensive tests
6. Gradually replace raw SQL usage with JPA service calls