# Complete LeagueDB Query Mapping

This document provides a complete mapping of every SQL query method in LeagueDB to its JPA equivalent.

## Query Method Mappings

| Original LeagueDB Method | Original SQL Query | JPA Repository Method | JPA Service Method |
|-------------------------|-------------------|---------------------|-------------------|
| `getLOLAccountsByUserId()` | `SELECT puuid, league_shard, tracking FROM summoner WHERE user_id = ? order by id` | `findByUserIdOrderById()` | `getLolAccountsByUserId()` |
| `getUserIdByLOLAccountId()` | `SELECT user_id FROM summoner WHERE puuid = ? AND league_shard = ?` | `findUserIdByPuuidAndLeagueShard()` | `getUserIdByLolAccountId()` |
| `getFocusedSummoners()` | `SELECT riot_id FROM summoner WHERE MATCH(riot_id) AGAINST('+?*' IN BOOLEAN MODE) AND league_shard = ? LIMIT 25` | `findFocusedSummoners()` | `getFocusedSummoners()` |
| `getSummonerIdByPuuid()` | `SELECT id from summoner where puuid = ? and league_shard = ?` | `findSummonerIdByPuuidAndLeagueShard()` | `getSummonerIdByPuuid()` |
| `hasSummonerData()` | `SELECT 1 from participant where summoner_id = ?` | `hasSummonerData()` | `hasSummonerData()` |
| `trackSummoner()` | `UPDATE summoner SET tracking = ? WHERE user_id = ? AND puuid = ?` | `updateTracking()` | `trackSummoner()` |
| `deleteLOLaccount()` | `UPDATE summoner SET tracking = 0, user_id = NULL WHERE user_id = ? AND puuid = ?` | `deleteLolAccount()` | `deleteLolAccount()` |
| `getMatchData()` | Complex JOIN between participant and match with ID filter | `findMatchDataWithParticipants()` | `getMatchData()` |
| `setMatchEvent()` | `UPDATE match SET events = ? WHERE id = ?` | `updateMatchEvents()` | `setMatchEvent()` |
| `getSummonerData()` (basic) | `SELECT ... FROM participant st JOIN match sm ... WHERE summoner_id = ? AND game_type = 43` | `findSummonerDataByGameType()` | `getSummonerData()` |
| `getSummonerData()` (with game_id) | `SELECT ... WHERE summoner_id = ? AND game_id = ?` | `findSummonerDataByGameId()` | `getSummonerData()` |
| `getSummonerData()` (with time range) | `SELECT ... WHERE summoner_id = ? AND time_start >= ? AND time_end <= ?` | `findSummonerDataByTimeRange()` | `getSummonerData()` |
| `getAdvancedLOLData()` | Complex aggregation with GROUP BY champion | `findAdvancedLolDataBySummoner()` | `getAdvancedLolData()` |
| `getAdvancedLOLData()` (with filters) | Advanced aggregation with time and queue filters | `findAdvancedLolDataWithFilters()` | `getAdvancedLolData()` |
| `getAllGamesForAccount()` | `SELECT game_id, game_type, win FROM participant JOIN match ...` | `findAllGamesForAccount()` | `getAllGamesForAccount()` |
| `getRegistredLolAccount()` | Complex query with ROW_NUMBER() window function | `findRegisteredLolAccountsWithLatestData()` | `getRegisteredLolAccounts()` |
| `getRegistredLolAccount()` (single) | Single summoner version of above | `findRegisteredLolAccountWithLatestData()` | `getRegisteredLolAccount()` |
| `setSummonerData()` | `INSERT IGNORE INTO participant(...)` | Handled by `save()` with existence check | `setSummonerData()` |
| `updateSummonerMasteries()` | `INSERT ... ON DUPLICATE KEY UPDATE` for masteries | `saveAll()` with delete/insert pattern | `updateSummonerMasteries()` |
| `updateSummonerEntries()` | `INSERT ... ON DUPLICATE KEY UPDATE` for ranks | `saveAll()` with delete/insert pattern | `updateSummonerEntries()` |

## Detailed Method Conversions

### 1. getLOLAccountsByUserId
```java
// Original SQL
"SELECT puuid, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "' order by id;"

// JPA Repository
List<Summoner> findByUserIdOrderById(String userId);

// Service Method
public List<Summoner> getLolAccountsByUserId(String userId) {
    return summonerRepository.findByUserIdOrderById(userId);
}
```

### 2. getAdvancedLOLData (Complex Aggregation)
```java
// Original SQL
"SELECT `champion`, COUNT(*) AS `games`, SUM(`win`) AS `wins`, " +
"SUM(CASE WHEN `win` = 0 THEN 1 ELSE 0 END) AS `losses`, " +
"AVG(CAST(SUBSTRING_INDEX(`kda`, '/', 1) AS UNSIGNED)) AS avg_kills, " +
"AVG(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(`kda`, '/', -2), '/', 1) AS UNSIGNED)) AS avg_deaths, " +
"AVG(CAST(SUBSTRING_INDEX(`kda`, '/', -1) AS UNSIGNED)) AS avg_assists, " +
"SUM(`gain`) AS total_lp_gain " +
"FROM `participant` WHERE `summoner_id` = '" + summonerId + "' GROUP BY `champion` ORDER BY `games` DESC;"

// JPA Repository
@Query("SELECT p.champion, COUNT(*) as games, SUM(CASE WHEN p.win = true THEN 1 ELSE 0 END) as wins, " +
       "SUM(CASE WHEN p.win = false THEN 1 ELSE 0 END) as losses, " +
       "SUM(p.gain) as totalLpGain " +
       "FROM Participant p WHERE p.summoner.id = :summonerId " +
       "GROUP BY p.champion ORDER BY games DESC")
List<Object[]> findAdvancedLolDataBySummoner(@Param("summonerId") Integer summonerId);

// Service Method
public List<Object[]> getAdvancedLolData(Integer summonerId) {
    return participantRepository.findAdvancedLolDataBySummoner(summonerId);
}
```

### 3. getMatchData (Complex JOIN)
```java
// Original SQL
"SELECT sm.id, sm.game_id, sm.league_shard, sm.game_type, sm.bans, sm.time_start, sm.time_end, sm.patch, " +
"st.account_id, st.win, st.kda, st.rank, st.lp, st.gain, st.champion, st.lane, st.side, st.build " +
"FROM participant st JOIN `match` sm ON st.match_id = sm.id where sm.id > 10353;"

// JPA Repository
@Query("SELECT m.id, m.gameId, m.leagueShard, m.gameType, m.bans, m.timeStart, m.timeEnd, m.patch, " +
       "p.summoner.accountId, p.win, p.kda, p.rank, p.lp, p.gain, p.champion, p.lane, p.team, p.build " +
       "FROM Match m JOIN m.participants p WHERE m.id > :minId")
List<Object[]> findMatchDataWithParticipants(@Param("minId") Integer minId);

// Service Method
public List<Object[]> getMatchData() {
    return matchRepository.findMatchDataWithParticipants(10353);
}
```

### 4. getRegistredLolAccount (Complex Subquery)
```java
// Original SQL (with ROW_NUMBER window function)
"SELECT s.puuid, s.league_shard, st.game_id, st.rank, st.lp, st.time_start " +
"FROM summoner s " +
"LEFT JOIN (SELECT t.summoner_id, t.game_id, t.rank, t.lp, t.time_start " +
"           FROM (SELECT st.summoner_id, sm.game_id, st.rank, st.lp, sm.time_start, " +
"                        ROW_NUMBER() OVER (PARTITION BY st.summoner_id ORDER BY sm.time_start DESC) AS rn " +
"                 FROM participant st " +
"                 JOIN `match` sm ON st.match_id = sm.id " +
"                 WHERE sm.time_start >= ? AND sm.game_type = 43) t " +
"           WHERE t.rn = 1) st " +
"ON s.id = st.summoner_id " +
"WHERE s.tracking = 1;"

// JPA Repository (Simplified without ROW_NUMBER)
@Query("SELECT s.puuid, s.leagueShard, p.match.gameId, p.rank, p.lp, p.match.timeStart " +
       "FROM Summoner s LEFT JOIN Participant p ON s.id = p.summoner.id " +
       "WHERE s.tracking = 1 AND p.match.timeStart >= :timeStart AND p.match.gameType = 43 " +
       "ORDER BY s.id, p.match.timeStart DESC")
List<Object[]> findRegisteredLolAccountsWithLatestData(@Param("timeStart") LocalDateTime timeStart);

// Service Method
public List<Object[]> getRegisteredLolAccounts(LocalDateTime timeStart) {
    return participantRepository.findRegisteredLolAccountsWithLatestData(timeStart);
}
```

### 5. setSummonerData (INSERT IGNORE)
```java
// Original SQL
"INSERT IGNORE INTO participant(summoner_id, match_id, win, kda, rank, lp, gain, champion, lane, team, build, ...) " +
"VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ...)"

// JPA Service Method (with existence check)
public Participant setSummonerData(Integer summonerId, Integer matchId, Boolean win, String kda, ...) {
    // Check if participant already exists (equivalent to INSERT IGNORE)
    if (participantRepository.existsBySummonerIdAndMatchId(summonerId, matchId)) {
        return null;
    }
    
    // Create and save new participant
    Summoner summoner = summonerRepository.findById(summonerId).orElse(null);
    Match match = matchRepository.findById(matchId).orElse(null);
    
    if (summoner == null || match == null) {
        return null;
    }
    
    Participant participant = new Participant(summoner, match, win, kda);
    // Set other fields...
    
    return participantRepository.save(participant);
}
```

### 6. updateSummonerMasteries (UPSERT Pattern)
```java
// Original SQL
"INSERT INTO masteries (summoner_id, champion_id, champion_level, champion_points, last_play_time) " +
"VALUES (?, ?, ?, ?, ?) " +
"ON DUPLICATE KEY UPDATE " +
"champion_level = VALUES(champion_level), " +
"champion_points = VALUES(champion_points), " +
"last_play_time = VALUES(last_play_time);"

// JPA Service Method (delete and insert pattern)
public boolean updateSummonerMasteries(Integer summonerId, List<Masteries> masteriesList) {
    try {
        // First, delete existing masteries for the summoner
        masteriesRepository.deleteBySummonerId(summonerId);
        
        // Save new masteries
        masteriesRepository.saveAll(masteriesList);
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

## Advantages of JPA Approach

1. **Type Safety**: All queries are checked at compile time
2. **Entity Relationships**: Natural navigation through object relationships
3. **Database Independence**: Works with MySQL, H2, PostgreSQL, etc.
4. **Automatic Mapping**: No manual result set parsing
5. **Caching**: Automatic first and second-level caching
6. **Transaction Management**: Declarative transaction handling
7. **Testing**: Easy to test with in-memory databases

## Migration Guidelines

1. **Start with Simple Queries**: Begin with basic CRUD operations
2. **Convert Complex Queries Gradually**: Break down complex queries into simpler JPA equivalents
3. **Use Native Queries When Needed**: For very complex queries, fall back to `@Query` with native SQL
4. **Maintain API Compatibility**: Use service layer to keep existing method signatures
5. **Add Comprehensive Tests**: Ensure all conversions work correctly
6. **Performance Testing**: Compare performance of JPA vs raw SQL for critical queries