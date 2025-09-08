# Migration Guide: LeagueDB to JPA

This guide shows side-by-side comparisons of how to migrate from the raw SQL LeagueDB methods to the new JPA-based LeagueDataService.

## 1. Basic Summoner Queries

### Before (Raw SQL):
```java
// Get LOL accounts by user ID
QueryResult result = LeagueDB.getLOLAccountsByUserId("user123");
for (QueryRecord record : result) {
    String puuid = record.get("puuid");
    String leagueShard = record.get("league_shard");
    String tracking = record.get("tracking");
    // Manual field extraction and type conversion
}

// Get user ID by account
String userId = LeagueDB.getUserIdByLOLAccountId("puuid123", LeagueShard.EUW1);

// Check if summoner has data
boolean hasData = LeagueDB.hasSummonerData(summonerId);
```

### After (JPA):
```java
// Get LOL accounts by user ID
List<Summoner> accounts = leagueDataService.getLolAccountsByUserId("user123");
for (Summoner account : accounts) {
    String puuid = account.getPuuid();
    Integer leagueShard = account.getLeagueShard();
    Integer tracking = account.getTracking();
    // Type-safe access to entity properties
}

// Get user ID by account
Optional<String> userId = leagueDataService.getUserIdByLolAccountId("puuid123", 3);

// Check if summoner has data
boolean hasData = leagueDataService.hasSummonerData(summonerId);
```

## 2. Complex Aggregation Queries

### Before (Raw SQL):
```java
// Get advanced LOL data with manual SQL and result parsing
QueryResult result = LeagueDB.getAdvancedLOLData(summonerId);
for (QueryRecord record : result) {
    try {
        int champion = record.getAsInt("champion");
        int games = record.getAsInt("games");
        int wins = record.getAsInt("wins");
        int losses = record.getAsInt("losses");
        double avgKills = record.getAsDouble("avg_kills");
        double avgDeaths = record.getAsDouble("avg_deaths");
        double avgAssists = record.getAsDouble("avg_assists");
        int totalLpGain = record.getAsInt("total_lp_gain");
        
        // Manual error handling for type conversion
    } catch (NumberFormatException e) {
        // Handle conversion errors
    }
}
```

### After (JPA):
```java
// Get advanced LOL data with type-safe result handling
List<Object[]> stats = leagueDataService.getAdvancedLolData(summonerId);
for (Object[] stat : stats) {
    Short champion = (Short) stat[0];
    Long games = (Long) stat[1];
    Long wins = (Long) stat[2];
    Long losses = (Long) stat[3];
    Long totalLpGain = (Long) stat[4];
    
    // Compile-time type safety, no conversion errors
    double winRate = games > 0 ? (wins * 100.0 / games) : 0;
}
```

## 3. Update Operations

### Before (Raw SQL):
```java
// Track summoner with raw SQL
boolean success = LeagueDB.trackSummoner("user123", "puuid123", true);

// Delete LOL account with raw SQL
boolean deleted = LeagueDB.deleteLOLaccount("user123", "puuid123");

// Update match events with raw SQL
boolean updated = LeagueDB.setMatchEvent(matchId, jsonString);
```

### After (JPA):
```java
// Track summoner with JPA service
boolean success = leagueDataService.trackSummoner("user123", "puuid123", true);

// Delete LOL account with JPA service
boolean deleted = leagueDataService.deleteLolAccount("user123", "puuid123");

// Update match events with JPA service
boolean updated = leagueDataService.setMatchEvent(matchId, jsonString);
```

## 4. Complex JOIN Queries

### Before (Raw SQL):
```java
// Complex match data query with manual JOIN
QueryResult matchData = LeagueDB.getMatchData();
for (QueryRecord record : matchData) {
    String matchId = record.get("id");
    String gameId = record.get("game_id");
    String leagueShard = record.get("league_shard");
    String gameType = record.get("game_type");
    String bans = record.get("bans");
    String timeStart = record.get("time_start");
    String timeEnd = record.get("time_end");
    String patch = record.get("patch");
    String accountId = record.get("account_id");
    String win = record.get("win");
    String kda = record.get("kda");
    // ... more manual field extraction
}
```

### After (JPA):
```java
// Complex match data query with automatic JOIN through entity relationships
List<Object[]> matchData = leagueDataService.getMatchData();
for (Object[] match : matchData) {
    Integer matchId = (Integer) match[0];
    String gameId = (String) match[1];
    Integer leagueShard = (Integer) match[2];
    Integer gameType = (Integer) match[3];
    String bans = (String) match[4];
    LocalDateTime timeStart = (LocalDateTime) match[5];
    LocalDateTime timeEnd = (LocalDateTime) match[6];
    String patch = (String) match[7];
    String accountId = (String) match[8];
    Boolean win = (Boolean) match[9];
    String kda = (String) match[10];
    // Type-safe access with proper types
}
```

## 5. Search Operations

### Before (Raw SQL):
```java
// Full-text search with raw MySQL syntax
QueryResult result = LeagueDB.getFocusedSummoners("TestPlayer", LeagueShard.EUW1);
List<String> summonerNames = new ArrayList<>();
for (QueryRecord record : result) {
    summonerNames.add(record.get("riot_id"));
}
```

### After (JPA):
```java
// Database-independent search with pagination
List<String> summonerNames = leagueDataService.getFocusedSummoners("TestPlayer", 3);
// Result is already a typed List<String>, no manual extraction needed
```

## 6. Insert Operations

### Before (Raw SQL):
```java
// Insert summoner data with raw SQL and manual parameter binding
boolean inserted = LeagueDB.setSummonerData(
    summonerId, matchId, participant, rank, lp, gain, build
);
// Complex method with many parameters and manual SQL construction
```

### After (JPA):
```java
// Insert summoner data with type-safe entity creation
Participant participant = leagueDataService.setSummonerData(
    summonerId, matchId, true, "10/2/15", (short) 64, (byte) 2, 
    (byte) 100, (short) 1500, (short) 75, (short) 18, "{}", "{}"
);
// Returns the created entity or null if already exists
```

## 7. Time-based Queries

### Before (Raw SQL):
```java
// Manual timestamp construction and SQL formatting
long timeStart = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000); // 7 days ago
long timeEnd = System.currentTimeMillis();
QueryResult result = LeagueDB.getSummonerData(summonerId, shard, timeStart, timeEnd);

for (QueryRecord record : result) {
    // Manual timestamp parsing
    long timestamp = record.getAsEpochSecond("time_start");
    Date date = new Date(timestamp * 1000);
    // Complex date handling
}
```

### After (JPA):
```java
// Natural LocalDateTime usage
LocalDateTime timeStart = LocalDateTime.now().minusWeeks(1);
LocalDateTime timeEnd = LocalDateTime.now();
List<Object[]> result = leagueDataService.getSummonerData(summonerId, 3, timeStart, timeEnd);

for (Object[] record : result) {
    LocalDateTime timestamp = (LocalDateTime) record[6]; // timeStart field
    // Direct LocalDateTime usage, no conversion needed
}
```

## Key Benefits of Migration

### Type Safety
```java
// Before: Runtime type conversion errors
int value = record.getAsInt("some_field"); // Can throw NumberFormatException

// After: Compile-time type checking
Integer value = (Integer) result[0]; // Type-safe cast
```

### Relationship Navigation
```java
// Before: Manual JOINs and foreign key handling
QueryResult participants = LeagueDB.query(
    "SELECT p.*, m.game_id FROM participant p JOIN match m ON p.match_id = m.id WHERE p.summoner_id = ?"
);

// After: Natural object navigation
List<Participant> participants = summoner.getParticipants();
for (Participant p : participants) {
    String gameId = p.getMatch().getGameId(); // Automatic JOIN
}
```

### Pagination and Sorting
```java
// Before: Manual LIMIT and ORDER BY in SQL
QueryResult result = LeagueDB.query(
    "SELECT * FROM summoner ORDER BY riot_id LIMIT 25"
);

// After: Built-in Spring Data pagination
Pageable pageable = PageRequest.of(0, 25, Sort.by("riotId"));
Page<Summoner> summoners = summonerRepository.findAll(pageable);
```

### Transaction Management
```java
// Before: Manual transaction handling
Connection conn = null;
try {
    conn = getConnection();
    conn.setAutoCommit(false);
    // Multiple operations
    conn.commit();
} catch (SQLException e) {
    if (conn != null) conn.rollback();
} finally {
    if (conn != null) conn.close();
}

// After: Declarative transactions
@Transactional
public void performMultipleOperations() {
    // Multiple JPA operations
    // Automatic transaction management
}
```

### Testing
```java
// Before: Requires actual database connection for testing
@Test
public void testLeagueDBMethod() {
    // Need real database setup
    QueryResult result = LeagueDB.getSummonerData(123);
    // Manual result verification
}

// After: Easy testing with in-memory database
@DataJpaTest
public void testJpaMethod() {
    // Automatic H2 in-memory database
    List<Object[]> result = leagueDataService.getSummonerData(123);
    // Type-safe assertions
}
```

## Migration Checklist

- [ ] Create JPA entities for all database tables
- [ ] Create repository interfaces with custom query methods
- [ ] Create service layer maintaining API compatibility
- [ ] Update Spring configuration for JPA
- [ ] Create comprehensive tests
- [ ] Update existing code to use service layer instead of raw SQL
- [ ] Verify performance with production data
- [ ] Gradually migrate from old LeagueDB methods