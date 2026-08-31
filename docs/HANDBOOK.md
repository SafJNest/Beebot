# Beebot Developer Handbook

> **Single operational entry point for making changes.** If you need to create a new command, endpoint, service, model, job, collection, query, cache or aggregate: start here. For architectural decisions see `AGENTS.md` and the ADRs; for the queue walk-through see `new-queue.md`; for Mongo migration see `mongo/README.md`.

## Table of Contents

1. [Rules and Precedence](#1-regole-e-precedenza)
2. [Architecture in 2 Minutes](#2-architettura-in-2-minuti)
3. [Package Map and Naming](#3-mappa-pacchetti-e-naming)
4. [Cross-Cutting Patterns (Filter / ApiResult / cache)](#4-pattern-trasversali)
5. [Operational TOC — How to...](#5-toc-operativo)
   - [5.1 New Discord Command](#51-nuovo-comando-discord)
   - [5.2 New HTTP Endpoint](#52-nuovo-endpoint-http)
   - [5.3 New Service](#53-nuovo-service)
   - [5.4 New Canonical Model](#54-nuovo-model-canonico)
   - [5.5 New Queued Job (Riot / Compute / Sync)](#55-nuovo-job-in-coda)
   - [5.6 New Mongo Collection + Entity](#56-nuova-collection-mongo--entity)
   - [5.7 New Mongo Query](#57-nuova-query-mongo)
   - [5.8 New Redis Cache](#58-nuova-cache-redis)
   - [5.9 New Aggregate / Analyzer (ProfileStatistics-type)](#59-nuovo-aggregate--analyzer)
   - [5.10 Filter Modification](#510-modifica-a-filter)
   - [5.11 Tracker / Periodic Job](#511-tracker--job-periodico)
   - [5.12 New Test](#512-nuovo-test)
6. [Reference — Indexes, Weight, RAM, Queries, Names](#6-reference)
7. [API + Documentation Checklist](#7-checklist-api--documentazione)

---

## 1. Rules and Precedence

Order when documents diverge:

1. `AGENTS.md` — repo-wide rules (Java/JS style, canonical model, presentation stability, service layout, API/doc sync gate).
2. Accepted ADRs in `docs/architecture/adr/`.
3. `docs/HANDBOOK.md` (this file) + assigned macro-task.
4. **CodeGraph** — `codegraph status` → `codegraph sync` → `codegraph explore <symbol>` + `codegraph impact <symbol>` before touching LoL code.
5. Current code — only evidence of migration status.

**Never silently suppress an ADR/macro-task conflict: stop + report to the main agent. Without `explore`/`impact`, do not modify LoL code.**

Quick Style Rules (from `AGENTS.md`):

- **Java:** switch `->`, grouped cases, static utility, static factory, `final` immutable, `List.of`, no Lombok in operational, no DI framework, no `Optional`, loop > stream if clearer.
- **JS:** Custom Elements as Java-like classes, order `static const → getter/setter → static factory → constructor → lifecycle → other`, no `#private`, `async/await` never `.then()`, `try/catch` in `connectedCallback`, `res.text()+DOMParser` where required, magic numbers in `static const`, single `innerHTML` via `parts[]`, CSS for state.
- **Service layout** in every modified service: `1 constants → 2 fields/ctors → 3 public methods → 4 //==== separator ====` → 5 private methods. Only the separator as structural comment.
- **Presentation stability:** changes to data/model/persistence/payload do not touch presentation (embed/view/field order/text/layout) unless a style refactor is explicitly requested.
- **Canonical LoL:** `Summoner`, `Rank`, `Mastery`, `riotId:String`, `SummonerView`, `SummonerLeaderboard`, `Match`, `Participant`, `MatchResult`. Before adding new mapping logic use `ChampionUtils`, `LaneTypeUtils`, `GameQueueTypeUtils`, `LeagueShardUtils`, `TierDivisionUtils`, `LeagueHandler`.

---

## 2. Architecture in 2 Minutes

**Stack:** Java 25, Spring 7.0.8 + Tomcat 11, JDA 6.3.1 + jdave, Jedis 5.1.0 (Redis), MongoDB driver sync 5.8.0, MariaDB (backfill only), R4J 2.8.1, Lavalink, FastUtil/Caffeine. Build `mvn package` → `jar-with-dependencies` main `com.safjnest.App`.

**Runtime LoL:** MongoDB is the sole runtime storage (`beebot` / `beebot_test` with `App.isTesting()`). MariaDB only for `MongoMigration` backfill. Redis cache only (no queue/backlog). R4J for Riot.

**Global scheduling:** `lol.queue` single registry — three separate physical schedulers, never shared:

| Dispatcher | Route | Owner |
|---|---|---|
| `RiotScheduler` | one worker per `LeagueShard` | outbound Riot calls |
| `ComputeScheduler` | `PROFILE` + `CHAMPION` | expensive Mongo computes |
| `SyncScheduler` | one worker per `LeagueShard` (+ global) | tracking, rank, match, sample, participant refresh |

```
QueueHandler.immediate/normal/background → Router → Registry (dedup + parent/child + follower)
                                      → AbstractScheduler<R> → JobQueue (3 lane) → JobWorker → Job
```

- Priority `IMMEDIATE > NORMAL > BACKGROUND`, promotion only if `requested.ordinal() < current`, never interrupting a running body.
- Dedup key = `scheduler:route:key`. Followers reuse the leader's `future`. `retain(job)` + `resume(job, callback)` for async callbacks.
- `profileQueue()` does insert-time least-loaded between PROFILE/CHAMPION; heavy keys (`champion-stats-matrix:|champion-build:|champion-data-refresh:`) reserve `CHAMPION`.

**Match ingestion (2 levels):** `MatchService.insert()` → `tracked=false` (identity seed + raw events). `Tracker` → invalidates cache + `RankService.refresh` from Riot for each participant → `tracked=true` single commit. OP.GG: raw card immediately + best-effort `{rank,lp}` snapshot; never gain/predecessor unless `tracked=true`.

**HTTP API:** Thin Spring controllers (`LolController`, `ChampionController`, `LeaderboardController`, `StatusController`) return canonical `lol.model` models directly. Validation via `LolApiParameters`, mapping via `LolApiResponses.from(ApiResult)`. `ApiResult<T>` = `READY|PARTIAL|PENDING|NOT_FOUND` → `200|200|202|404`. Metadata always `{pagination, lastUpdate, refresh, filter}` except for `LiveGame` and `BotStatus`.

---

## 3. Package Map and Naming

```
com.safjnest
 ├─ commands/<categoria>/<sotto>        // JDA SlashCommand, name = lowercased class
 ├─ lol/
 │   ├─ model/                          // CANONICI — unico posto per success DTO
 │   │   ├─ summoner/  Summoner, Rank, Mastery, SummonerView, SummonerLeaderboard, SummonerOverview
 │   │   ├─ match/     Match, Participant, MatchResult, LiveGame, RankHistory...
 │   │   ├─ statistics/ ProfileStatistics, ProfileActivity, ProfileMatchups, Stats
 │   │   ├─ leaderboard/ LeaderboardPage, LeaderboardDistribution
 │   │   ├─ record/    ProfileRecord, ProfileRecordPage, RecordMetric...
 │   │   ├─ champion/  ChampionView, ChampionStatistics, Build...
 │   │   └─ Filter, ActivityFilter, ApiResult, ResponseMetadata, ChampionIndexable...
 │   ├─ service/       // cache + persist + compose + enqueue; Analyzer puro separato
 │   ├─ queue/         // QueueHandler, Registry, Router, job/, scheduler/, worker/
 │   ├─ tracker/       // Tracker, TrackerScheduler
 │   └─ utils/         // ChampionUtils, LaneTypeUtils, GameQueueTypeUtils...
 ├─ spring/
 │   ├─ controller/    LolController, ChampionController, LeaderboardController, StatusController
 │   ├─ config/        LolApiConfig (Jackson MixIn + CORS)
 │   └─ dto/           LolApiError (solo errori HTTP)
 ├─ nosql/             MongoDB, MongoMigration, AbstractEntity, NoSqlEntityExecutor
 ├─ redis/             RedisKey, RedisClient, RedisMemoryParser
 ├─ sql/               QueryRecord, QueryRecordParser, database/LeagueDB (solo migration)
 └─ status/            StatusService, SystemMetricsSampler, LeagueMetricsStore, SampledMetrics
```

**Class/method names (binding conventions):**

- Package `com.safjnest` + camelCase. Command class `Summoner` → name `summoner`; child `SummonerProfile` → name `profile` (via `replace(father) + toLowerCase`). JSON key identical.
- Controller method `profile/search/profileByName/matches/rankHistory/activity/matchups/records/match`. Service `get/generate/refresh/invalidate`.
- Redis key pattern `beebot:lol:ls:<region>:<shard>:<puuid>:summoner:statistics:<filterKey>` (actual value before resource). Java enum `RedisKey` `UPPER_SNAKE` with `of(args)`.
- Mongo collection = table name (`summoner`, `match`, `match_events`, `profile_statistics`, `profile_activity`, `profile_matchups`, `profile_records`, `champion_builds`, `competitive`).
- Queue key: `profile-statistics:<puuid>:<filterKey>`, `champion-stats-matrix:<patch>:<queue>`, `champion-build:<Filter.toKey()>`, `profile-refresh:<puuid>`.

---

## 4. Cross-Cutting Patterns

### Filter — Single Key of an Aggregate

`Filter` (`lol/model/Filter.java`) is the object that must not lose fields between UI → service → Mongo → Redis → queue:

| Field | default/canonical | notes |
|---|---|---|
| `champion` | `0` = all | int id |
| `opponent` `duo` | `0` | int id |
| `lane` | `null` | `LaneType` |
| `queue` | `null` | `GameQueueType` canonicalized (`CanonicalQueue`) |
| `rank` + `rankBehavior` | `null` + `GREATER_OR_EQUAL` | `TierType` / `EXACT|GREATER_OR_EQUAL` |
| `patch` | `PatchUtils.getPatch()` | major `x.y` |
| `region` | `null` | `LeagueShard` |
| `timeStart/timeEnd` | current season | `0` = no bound, via `SeasonUtils` |

Base64 Keys:

- `Filter.toKey()` — legacy champion/build (no full time).
- `Filter.toSummonerKey()` — **profile/Redis/Mongo/queue**: `champion|lane|queue|rank|behavior|patch|region|opponent|duo|timeStart|timeEnd` → `Base64Url without padding` with `*` for null. **Always use this for profile.**
- `Filter.toStateKey()` — full serialization with ordinal for enums.
- `ActivityFilter extends Filter` adds `minGames` (default 5, not in `toSummonerKey()`).

> Changing a discriminating field → new `filterKey` → new `profile_statistics` document / new job. Never search by `puuid` alone.

### ApiResult + ResponseMetadata

```java
record ApiResult<T>(Status status, T payload, ResponseMetadata metadata)
  enum Status { READY, PARTIAL, PENDING, NOT_FOUND }
record ResponseMetadata(Pagination pagination, Long lastUpdate, Boolean refresh, Filter filter)
```

`READY|PARTIAL` → `200`, `PENDING` → `202` with `LolApiError(202, code, message, metadata)`, `NOT_FOUND` → `404`. `refresh=true` means deduplicated job enqueued. `ProfileService.isStale` = `now - lastUpdate >= 30d + hashMod(puuid)*1d (0-14d)` && `lastSeenAt` within 60d.

### Read-through Cache

```
Redis SUMMONER_STATISTICS(region, shard, puuid, filterKey)  60s TTL (BE: 6h logico, invalidato su upsert)
  → Mongo {puuid, filterKey}   unique profile_statistics_identity
  → miss: ComputeScheduler.startProfileStatistics(...) → 202
  hit:  Redis.set dopo Mongo + invalida overview/recentMatches su upsert riuscito
```

Same for `SUMMONER_ACTIVITY`, `SUMMONER_MATCHUPS`, `SUMMONER_OVERVIEW`, `SUMMONER_RECENT_MATCHES` (5), `CHAMPION_PAGE`, `CHAMPION_TIER_LIST`. Job failure → exception, key removed, retry possible.

---

## 5. Operational TOC

### 5.1 New Discord Command

**When:** user types `!cmd` or `/cmd`. Every LoL command resolves identity via canonical `Summoner` (Mongo `SummonerService.get`), not direct R4J. Embed/button presentation unchanged unless explicitly requested.

**Steps:**

1. **Declare in `rsc/commands.json`** (single source for Help + permissions):
   ```json
   "mycommand": {
      "alias": ["my"],
      "help": "Short help (embed title).",
      "longhelp": "Extended description.",
     "category": "League Of Legends",
     "arguments": "[summonerName]",
     "cooldown": "5",
     "children": {
       "sub": { "alias": [], "help": "Sub help", "longhelp": "...", "arguments": "", "cooldown": "0" }
     }
   }
   ```
   - `name` lowercased, no spaces. `category` existing or new (check `BotCommand.Category`).
2. **Create parent `src/main/java/com/safjnest/commands/<cat>/Mycommand.java` extends `SlashCommand`:**
   ```java
   public class Mycommand extends SlashCommand {
     public Mycommand(){
       this.name = getClass().getSimpleName().replace("Slash","").toLowerCase();
       BotCommand d = CommandsLoader.getCommand(this.name);
       this.aliases=d.getAliases(); this.help=d.getHelp(); this.cooldown=d.getCooldown();
       this.category=d.getCategory(); this.arguments=d.getArguments();
       this.contexts=new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
       String father=getClass().getSimpleName().replace("Slash","");
       this.children=new SlashCommand[]{ new MycommandSub(father) };
       d.setThings(this); // SEMPRE ultimo — registra presence + options/permessi
     }
     @Override protected void execute(SlashCommandEvent e){} // parent vuoto ok
     @Override protected void execute(CommandEvent e){
       Summoner s = LeagueHandler.getSummonerByArgs(e);
       if(s==null){ e.reply("Couldn't find summoner. /mycommand link"); return; }
       LeagueMessage.send(e, null, s, s.puuid(), new LeagueMessageParameter(LeagueMessageType.MYCOMMAND));
     }
   }
   ```
3. **Create child `MycommandSub.java` extends `SlashCommand`:**
   ```java
   public class MycommandSub extends SlashCommand {
     public MycommandSub(String father){
       this.name=getClass().getSimpleName().replace("Slash","").replace(father,"").toLowerCase();
       BotCommand d=CommandsLoader.getCommand(father).getChild(this.name);
       this.help=d.getHelp(); this.cooldown=d.getCooldown(); this.category=d.getCategory();
       this.contexts=new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
       this.options=Arrays.asList(
         new OptionData(OptionType.STRING,"summoner","Name#tag",false).setAutoComplete(true),
         LeagueShardUtils.getAsOptions(),
         new OptionData(OptionType.USER,"user","Discord user",false));
       d.setThings(this);
     }
     @Override protected void execute(SlashCommandEvent e){
       e.deferReply(false).queue();
       Summoner s=LeagueHandler.getSummonerByArgs(e); // gestisce string/user/autocomplete
       if(s==null){ e.getHook().editOriginal("Couldn't find...").queue(); return; }
       LeagueMessage.send(e.getHook(), e.getUser().getId(), s, s.puuid(), new LeagueMessageParameter(LeagueMessageType.MYCOMMAND));
     }
   }
   ```
4. **Message/embed:** add `LeagueMessageType.MYCOMMAND` + handler in `lol/message/LeagueMessage.java` — reuse existing `LeagueEventHandler`, do not duplicate rendering. If new embed: extend `LeagueMessage` following the `SummonerProfile` pattern (fields already present in `SummonerView`, do not automatically expose every new aggregate field).
5. **Autocomplete/search:** reuse `SummonerService.search/autocomplete` (Redis `SUMMONER_SEARCH`, max 25).
6. **Check RAM/weight:** each command lives in memory as a `SlashCommand` singleton; options < 25. No new thread — `deferReply` + async `LeagueMessage`.
7. **Test:** `src/test/java/com/safjnest/lol/message/LeagueMessageOpggTitleTest.java` as reference + manual `!mycommand` on test guild.

**Antipattern:** do not call `R4J` directly from the command; do not create `SummonerDocument`; do not put computation logic in the command — delegate to `lol/service`.

---

### 5.2 New HTTP Endpoint

**When:** new data for the `League-OS` dashboard (Next.js) or external client. Thin controllers, return canonical `lol.model`.

**Steps:**

1. **Choose controller:**
   - `LolController` (`/api/lol/{shard}`) for summoner-scoped summoner/match.
   - `ChampionController` (`/api/lol/champion` / `/api/lol/champions`) for champion aggregates.
   - `LeaderboardController` (`/api/lol/leaderboard`) for leaderboards.
   - `StatusController` (`/api/status`) for process metrics.
   - New scope → new `*Controller.java` in `spring/controller`, add to `spring/config/LolApiConfig` if CORS/Jackson MixIn is needed.
2. **Add method:**
   ```java
   @GetMapping("/my/{puuid}")
   public ResponseEntity<?> my(
     @PathVariable("shard") String shardValue,
     @PathVariable("puuid") String puuid,
     @RequestParam(name="queue", required=false) String queueValue,
     @RequestParam(name="limit", defaultValue="20") int limit) {
     LeagueShard shard = LolApiParameters.requiredShard(shardValue);
     String id = LolApiParameters.requiredText(puuid, "puuid");
     GameQueueType queue = LolApiParameters.optionalQueue(queueValue); // ALL→null
     int pageLimit = LolApiParameters.matchLimit(limit); // 1..100
     ApiResult<MyView> r = myService.get(shard, id, queue);
     return LolApiResponses.from(r, "my_pending", "My data is being prepared", "Profile not found");
   }
   ```
   - Validation **always** via `LolApiParameters.*` (case-insensitive enum + `trim()`, patch regex `\d+\.\d+`, cross-validation of view/season/patch/time).
   - Paginated: build `ResponseMetadata(new Pagination(null,null,limit,offset,total,null,hasMore), lastUpdate, refresh, filter)` + `page.withMetadata(metadata)`.
3. **Service:** create `lol/service/MyService.java` with layout `constants → fields/ctors → public → //==== → private`. No `@Service` DI — `new MyService()` in the controller.
4. **Jackson:** if the new model needs a `MixIn`, register it in `LolApiConfig.configureMessageConverters` (already ignores `ChampionStatistics.filter`, etc.).
5. **Errors:** do not create success DTOs; only `LolApiError(status,code,message,metadata)` for `202/400/404`. Exceptions via `ResponseStatusException`.
6. **Docs:** add `docs/api/<scope>/my.md` with the template (endpoint + curl + params + 200 JSON + alt states + owner file:line). Update `docs/api/lol-api.md` index + `docs/architecture/README.md` if new ADR.
7. **CORS:** `LolApiConfig.addCorsMappings` already `allowedOrigins("*")` for `/api/lol/**` and `/api/status`.

**Query weight:** each endpoint must do at most 1-2 indexed Mongo `find`s + Redis `mget`. No `COLLSCAN` — verify `explain("executionStats").IXSCAN`.

---

### 5.3 New Service

**When:** read/assemble/cache or write/aggregate logic for a LoL domain.

**Steps:**

1. **Create `src/main/java/com/safjnest/lol/service/MyService.java`:**
   ```java
   public final class MyService {
     // 1. constants
     private static final long STALE_MILLIS = TimeConstant.DAY * 7;
     // 2. fields and constructors
     private final OtherService other = new OtherService();
     // 3. public methods
     public ApiResult<MyView> get(LeagueShard shard, String puuid, Filter filter){ ... }
     public boolean generate(String puuid, LeagueShard shard, Filter filter){ ... }
     // ============================================================================
     // 5. private methods
     private static boolean isStale(long lastUpdate){ ... }
   }
   ```
   - `final` fields, no Lombok, no `Optional`, switch `->`, `List.of`, loop > stream if clearer.
   - Separate pure `Analyzer` (see 5.9) — service owns cache + Mongo + queue.
2. **Standard read path:**
   ```java
   String key = RedisKey.MY_VIEW.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
   MyView cached = RedisClient.get(key, MyView.class);
   if(cached!=null && !isStale(cached.lastUpdate())) return ApiResult.ready(cached, metadata(...));
   MyView stored = MongoDB.findMyView(puuid, filter);
   if(stored!=null){ RedisClient.set(RedisKey.MY_VIEW, stored, ...); return ApiResult.ready(...); }
   ComputeScheduler.startMyView(puuid, shard, filter); // enqueue
   return ApiResult.pending(metadata(...));
   ```
3. **Standard write path (see 5.9):** `MongoDB.forEach...` + `Accumulator` → `finish()` → `MongoDB.upsertMyView` → `RedisClient.set` + invalidations.
4. **Staleness:** reuse `ProfileService.isStale` pattern (`30d + hash jitter 0-14d` + `lastSeenAt` <60d) or `ChampionService` weekly. Do not recompute on request for stale — `PARTIAL` + `BACKGROUND`.
5. **Invalidation:** `RedisClient.delete(key)` on parent (`SUMMONER_OVERVIEW`) or dedicated `RedisKey` after successful upsert.
6. **Check RAM/weight:** stateless service; `forEach` cursor batch 100-1000, do not materialize entire `List<Match>`.

---

### 5.4 New Canonical Model

**When:** new domain concept. Success models **only** in `lol.model` (or `lol.model.<subpackage>`). Spring DTOs only for HTTP errors.

**Steps:**

1. **Choose package:** `lol/model/summoner` (identity), `lol/model/match` (match/participant), `lol/model/statistics` (filtered aggregates), `lol/model/leaderboard`, `lol/model/record`, `lol/model/champion`.
2. **Choose form:**
   - Immutable projection → `record MyView(Summoner summoner, Map<GameQueueType,Rank> ranks, ...) { static MyView from(...){...} }`
   - Mutable filter/entity → `class Filter` fluent `setX()->this`, `AbstractEntity<MyEntity>` for persistence.
3. **Constraints from `AGENTS.md`:**
   - No `SummonerRank` / `ChampionMastery` alias — use `Rank` / `Mastery`.
   - `riotId` remains `String`, no `RiotId` record unless ADR.
   - `TierDivisionUtils`, `ChampionUtils`, etc. before new mapping logic.
   - `@JsonInclude(NON_NULL)`, factory `from`/`of`, `Map.copyOf` / `List.copyOf`.
4. **Jackson:** add MixIn in `LolApiConfig` if you need to ignore internal fields (`filter`, `filterKey`).
5. **Check:** `docs/api/lol-api.md` + ADR if new JSON contract (ADR-0005/0006/0013).

---

### 5.5 New Queued Job

**When:** expensive or rate-limited async work. Never standalone `thenApplyAsync` — go through a dispatcher.

**Steps:**

1. **Choose scheduler:**

   | Case | Scheduler | Route type | Thread name |
   |---|---|---|---|
   | Riot outbound | `RiotScheduler` | `LeagueShard` | `r4j-<shard>-` |
   | Expensive Mongo compute | `ComputeScheduler` | `DatabaseWorkerType` | `lol-db-profile-worker-` / `champion` |
   | Tracking/rank/match/sample | `SyncScheduler` | `LeagueShard` (+ global) | `lol-sync-<shard>-` |

2. **Enqueue:**
   ```java
   // Riot — per summoner
   QueueHandler.immediate(RiotScheduler.class, shard,
     shard.name()+":summoner:"+puuid, "summoner puuid="+puuid, job -> {
       job.phase("FETCH"); job.trackItem(puuid);
       Summoner s = RiotApi.getSummonerByPuuid(puuid);
       if(s!=null){ job.done(puuid); return s; } else { job.missing(puuid); return null; }
     });
   // Compute — per aggregate
   QueueHandler.normal(ComputeScheduler.class, DatabaseWorkerType.PROFILE,
     "my:" + puuid + ":" + filter.toSummonerKey(), "my puuid="+puuid, job -> {
       boolean ok = myService.generate(puuid, shard, filter);
       return ok;
     });
   // Stale
   QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.PROFILE, key, name, work);
   ```
   - `immediate/normal/background` = `IMMEDIATE/NORMAL/BACKGROUND`.
   - Dedup key = `scheduler:route:key` — same key → same `Future`, no second entry. Failed → removed, retryable.
   - `job.phase(String)`, `trackItem/trackItems`, `done/missing/failed`, `progress`.
3. **Async callback (parent waiting for children):**
   ```java
   QueueHandler.retain(job); // prima di return del body
   asyncOp.thenAccept(v -> QueueHandler.resume(job, () -> { /* schedule figli */ }));
   ```
4. **Diagnostics:** `GET /api/status` exposes `dispatchers[].queues[].worker` + `runs[]`. No hot-path logging — only on `onBodyFailed`.
5. **RAM/weight:** max 2 concurrent Compute workers; `CHAMPION` reserved for heavy matrix (`champion-stats-matrix:`). Do not move work after `offer` (insert-time placement).

---

### 5.6 New Mongo Collection + Entity

**When:** new persisted aggregate or new projection.

**Steps:**

1. **Choose `_id`:**
   - `summoner` → `_id = puuid` (single field, no duplicate `puuid`).
   - `match` → `_id = fullGameId` (`EUW1_123`), `region` sole shard field, `patch` + `patchMajor`.
   - `profile_*` / `champion_*` / `profile_records` → `_id = random ObjectId`, **always lookup `{puuid, filterKey}` or `{puuid, filterKey, metric}`** with `$setOnInsert` for `_id`.
   - `match_events` → `_id = matchId`, payload JSON, WiredTiger `zstd`.
2. **Create entity (if incremental mutation is needed):**
   ```java
   public class MyEntity extends AbstractEntity<MyEntity> {
     @JsonProperty public String puuid;
     @JsonProperty public String filterKey;
     @Override public String collectionName(){ return "my_collection"; }
     @Override public Object entityId(){ return puuid + ":" + filterKey; }
     @Override protected Map<String,Object> snapshotValues(){
       Map<String,Object> m=new HashMap<>(); m.put("puuid",puuid); m.put("filterKey",filterKey); return m;
     }
     public MyEntity setFoo(String v){ setValue("foo", v); return this; } // auto flush se instant()
   }
   // uso: new MyEntity().instant().setFoo("x").updateNow()  |  .deferred().setFoo().update()
   ```
   For aggregates regenerated from scratch: no `AbstractEntity` needed — do `MongoDB.upsertMy(...)` with `updateOne({puuid,filterKey}, {$set:{...}, $setOnInsert:{_id:ObjectId()}}, upsert:true)`.
3. **Add to `MongoDB.java`:**
   - Constant in `COLLECTION_NAMES`.
   - Methods `findMy(...)`, `upsertMy(...)`, `forEachMy...` (cursor batch, `Filters.eq/and/in/regex`, `Projections.include`, `Sorts.descending`).
   - Trace `traceRead("my.find", "puuid=...")`.
4. **Indexes (create-only, idempotent):** in `MongoDB.initialize()` add `createIndex` with stable name. Preflight for unique (e.g. `profile_statistics_identity {puuid,filterKey}` aborts on duplicates, no `dropIndex`).
   ```java
   // esempio
   collection.createIndex(Indexes.compoundIndex(Indexes.ascending("puuid"), Indexes.ascending("filterKey")),
     new IndexOptions().unique(true).name("my_identity"));
   ```
5. **Backfill:** if migrating from MariaDB is needed, add query to `LeagueDB.java` + method in `MongoMigration.migrateAll()` (batch: summoner 20k bulk unordered, match 1k sub-batches, `_id` preflight).
6. **Storage:** `match_events` already `zstd level 9` server-side; others structured BSON, no Kryo/opaque string.

---

### 5.7 New Mongo Query

**Steps:**

1. Build filter with `Filters.eq/and/or/in/regex/elemMatch` + `Projections.include` (fetch only needed fields) + `Sorts.descending("timeStart")`.
2. Use `FindIterable` + `MongoCursor` with batching; never `toList()` on 6M docs.
3. Helper in `MongoDB.java`:
   ```java
   public static List<QueryRecord> findMy(String puuid, Filter f){
     Bson filter = Filters.and(Filters.eq("puuid", puuid), Filters.eq("filterKey", f.toSummonerKey()));
     return database().getCollection("my").find(filter)
       .projection(Projections.include("puuid","filterKey","data")).into(new ArrayList<>())
       .stream().map(QueryRecordParser::fromDocument).toList();
   }
   ```
4. **Verify index:** `db.collection.explain("executionStats").find({...})` → `winningPlan.stage == IXSCAN`, `totalDocsExamined ≈ nReturned`, no `COLLSCAN`. `db.collection.aggregate([{$collStats:{count:true}}])` for `indexSizes`.
5. **Inventory:** update `docs/mongo/08-query-inventory.md`.

---

### 5.8 New Redis Cache

**Steps:**

1. **Register key in `redis/RedisKey.java`:**
   ```java
   MY_VIEW("los:%s:%s:my:%s:%s", Duration.ofSeconds(60)), // pattern con %s placeholders
   ```
   Pattern: `los:<region>:<shard>:<puuid>:my:<filterKey>` or global `los:my:%s`. Logical TTL 60s (BE: 6h/12h but explicitly invalidated). Auto prefix `beebot:lol:` / `beebot_test:lol` via `App.isTesting()`.
2. **Usage:**
   ```java
   String k = RedisKey.MY_VIEW.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
   MyView c = RedisClient.get(k, MyView.class);
   if(c!=null) return c;
   MyView s = MongoDB.findMy(puuid, filter);
   if(s!=null) RedisClient.set(RedisKey.MY_VIEW, s, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid, filter.toSummonerKey());
   // invalida su upsert: RedisClient.delete(k) o deleteByPattern via scan se serve
   ```
3. **Available operations:** `set/get/mget/setex (60s), claim(NX EX), delete, increment(+expire), exists, rpush/sadd/smembers, lrangeAll/popList, dbSize, usedMemory, ttl`.
4. **Circuit breaker:** `RedisClient` disables for 30s on failure (`disabledUntil`). Do not abuse `mget` with >1k keys.
5. **RAM weight:** each value Jackson-serialized; `SUMMONER_STATISTICS` ~ a few KB per PUUID/filter. Separate keys per namespace: `r4j:*` vs `ls:*` (League OS). Avoid caching entire `List<Match>`.

---

### 5.9 New Aggregate / Analyzer (ProfileStatistics-type)

**When:** filtered aggregate rebuildable from matches (e.g. record, tier list, matchup).

**Steps:**

1. **Define identity:** `puuid + filter.toSummonerKey()` (or `puuid+metric` for `profile_records`). Document in `docs/architecture/profile-statistics-source-of-truth.md`.
2. **Create pure Analyzer `lol/service/MyAnalyzer.java`:**
   ```java
   public final class MyAnalyzer {
     private MyAnalyzer(){}
     public static Accumulator accumulator(String puuid, Filter filter){ return new Accumulator(puuid, filter); }
     public static final class Accumulator {
       private final Map<Key, Stats> leaves = new HashMap<>();
       public void accept(Match match){ // chiamato per ogni match del cursor
         if(!matchesFilter(match, filter)) return;
         // aggiorna leaves[champion][queue][lane].games/wins/...
       }
       public MyAggregate finish(){ // calcola medie, OTP, finalizza
         applyOtp(leaves); return new MyAggregate(leaves, lastUpdate=System.currentTimeMillis());
       }
     }
   }
   ```
   - Pure: no Redis/Mongo inside. One scan per shared `buildMatchFilter`.
   - `forEachProfileStatisticsMatch` already applies Mongo filter; reuse post-read `matchesFilter` for relational cases.
3. **Service `MyService.generate(...)`:**
   ```java
   public boolean generate(String puuid, LeagueShard shard, Filter filter){
     Accumulator acc = MyAnalyzer.accumulator(puuid, filter);
     MongoDB.forEachProfileStatisticsMatch(puuid, shard, filter, 0, 0, acc::accept);
     MyAggregate agg = acc.finish(); agg.lastUpdate = System.currentTimeMillis();
     boolean ok = MongoDB.upsertMyAggregate(puuid, filter, agg);
     if(ok){ RedisClient.set(RedisKey.MY, agg, ...); RedisClient.delete(recentMatchesKey); }
     return ok;
   }
   ```
4. **Enqueue:** `ComputeScheduler.startMy(puuid, shard, filter)` → `QueueHandler.normal(PROFILE, key="my:"+puuid+":"+filter.toSummonerKey(), ...)`.
5. **API:** `MyService.get(...)` read-through → `ApiResult.ready/pending/partial`.

---

### 5.10 Filter Modification

**Steps:**

1. Add field in `Filter.java` (e.g. `int myField`). Update `canonical()` (reset to neutral), `summoner()` if it should be zeroed for activity, `setMyField()` fluent.
2. Update **all** serializations symmetrically:
   - `toSummonerKey()` / `fromSummonerKey()` — business key (included in `profile_*` identity).
   - `toStateKey()` / `fromStateKey()` — state URL.
   - `toKey()` / `fromKey()` if it touches champion/build.
   - `genericKey()` / `pageKey()` if needed.
   - Null values → `"*"`, enum → consistent `name()` or `ordinal`.
3. Update `buildMatchFilter` in `MongoDB` and `ProfileAnalyzer.matchesFilter` to apply the new predicate in `accept()`.
4. Update `docs/architecture/profile-statistics-source-of-truth.md` fields table + `docs/mongo/*` if indexed.
5. **Breaking:** if you change encoding → invalidate cache and regenerate aggregates (new `filterKey` = new document; old orphans removed by cleanup job).

---

### 5.11 Tracker / Periodic Job

**When:** scheduled polling (e.g. every 10m for tracked summoners).

**Steps:**

1. **Add in `lol/tracker/TrackerScheduler.java`:**
   ```java
   ChronoTask myTask = TrackerScheduler::myJob;
   myTask.scheduleAtFixedRate(0, TimeConstant.MINUTE*10, TimeUnit.MILLISECONDS);
   // o scheduleAtFixedTime(3,0,0) per giornaliero
   ```
2. **Implement `Tracker.myJob()`:**
   ```java
   static void myJob(){
     List<Summoner> accounts = MongoDB.findTrackedSummonerModels();
     Map<LeagueShard, List<Summoner>> byShard = groupByShard(accounts);
     QueueHandler.immediate(SyncScheduler.class, null, "my-tracking", "my tracking", root -> {
       for(var e: byShard.entrySet()){
         QueueHandler.immediate(SyncScheduler.class, e.getKey(), "my:"+e.getKey().name(), "my shard="+e.getKey().name(), shardJob -> {
           for(Summoner s: e.getValue()){
             QueueHandler.immediate(SyncScheduler.class, e.getKey(), "my:"+s.puuid(), "my puuid="+s.puuid(), job -> { doWork(s, job); return null; });
           } return null; });
       } return null; });
   }
   // report per item: job.trackItem(id); job.done/missing/failed(id); job.phase("DISCOVERING")
   ```
3. **Enable only if `App.tracking()`** (`TrackerScheduler.scheduleIfEnabled` gate).
4. **Status:** add section in `status/LeagueMetricsStore` if `GET /api/status` dashboard is needed.

---

### 5.12 New Test

**Structure:** `src/test/java/com/safjnest/...`

```java
class MyAnalyzerTest {
  @Test void accumulatesSingleMatch(){ ... }
  @Test void filterRespected(){ ... }
  @Test void staleJitterDeterministic(){ ... }
}
class MyControllerTest {
  // usa MockMvc + LolApiConfig + Jackson MixIn
}
class MongoDBTest {
  // App.isTesting() → beebot_test, pulisci collection in @BeforeEach
}
```

- Unit for analyzer (pure, no Mongo). Integration for service with `MongoDB` test DB + mocked or embedded `RedisClient`.
- Verify `explain` IXSCAN in `docs/TODO.md` P1 before marking gate.
- Never commit real credentials/URIs; use `rsc/settings.json` template.

---

## 6. Reference

### Canonical Models — Where They Live

| Concept | Class | Owner Service | Mongo Collection | Redis key |
|---|---|---|---|---|
| Identity | `Summoner` (`puuid` `_id`) | `SummonerService` | `summoner` | `SUMMONER` / `R4J_SUMMONER` |
| Rank Queue | `Rank` (`Map<GameQueueType,Rank>` in summoner) | `RankService` | `summoner.ranks` | `SUMMONER_RANKS` |
| Mastery | `Mastery` | `MasteryService` | `summoner.masteries` | `SUMMONER_MASTERIES` |
| Overview | `SummonerView` + `SummonerOverview` | `ProfileService` | — (compose) | `SUMMONER_OVERVIEW` |
| Full Match | `Match` + `Participant` | `MatchService` + `Tracker` | `match` (`_id=fullGameId`) + `match_events` (zstd) | `MATCH_DETAIL` |
| Light Match | `MatchResult` | `MatchService` | `match` projection | `SUMMONER_RECENT_MATCHES` (5) |
| Profile Aggregate | `ProfileStatistics` | `ProfileService` + `ProfileAnalyzer` | `profile_statistics` `{puuid,filterKey}` unique | `SUMMONER_STATISTICS` |
| Activity | `ProfileActivity` | `ProfileService` | `profile_activity` | `SUMMONER_ACTIVITY` |
| Matchup | `ProfileMatchups` | `ProfileService` | `profile_matchups` | `SUMMONER_MATCHUPS` |
| Record | `ProfileRecord` | `ProfileRecordService` | `profile_records` `{puuid,filterKey,metric}` | — (via service) |
| Champion | `ChampionView` / `ChampionStatistics` / `Build` | `ChampionService` + `ChampionAnalyzer` | `champion_statistics` / `champion_builds` | `CHAMPION_PAGE` / `CHAMPION_TIER_LIST` |
| Leaderboard | `SummonerLeaderboard` / `LeaderboardPage` | `LeaderboardService` | `competitive` / `leaderboard_aggregates` | `LEADERBOARD_PAGE` |

### Primary Indexes (Mongo)

| Collection | Index | Type | Notes |
|---|---|---|---|
| `summoner` | `_id` | unique | `puuid` |
| `summoner` | `ranks.RANKED_SOLO_5X5.tier` + `region` | compound | leaderboard high elo scan |
| `match` | `_id` | unique | `EUW1_123` |
| `match` | `puuids` (participants) | multikey | `getMatchesByPuuid` |
| `match` | `timeStart` | single | range scan |
| `match` | `queue` + `patchMajor` + `region` | compound | champion matrix |
| `match_events` | `_id` | unique | matchId, WiredTiger zstd |
| `profile_statistics` | `profile_statistics_identity {puuid,filterKey}` | **unique** | preflight aborts on duplicates, no `dropIndex` |
| `profile_activity` | `{puuid,filterKey}` | unique | — |
| `profile_matchups` | `{puuid,filterKey}` | unique | — |
| `profile_records` | `{puuid,filterKey,metric}` | unique | ObjectId `_id` |
| `competitive` | `{puuid,queue}` | unique | MMR/OTP derived |
| `champion_statistics` | `{filterKey}` / `patch+queue` | compound | matrix |

Verify: `db.col.getIndexes()` + `db.col.find({...}).explain("executionStats")` → `IXSCAN`, `nReturned ≈ totalDocsExamined`.

### Weight & RAM — Orders of Magnitude

| Data | Size | Notes |
|---|---|---|
| `summoner` doc | ~1-2 KB | `ranks{}` + `masteries[]` (flat) |
| `match` doc | ~8-15 KB | 10 flat participants + `bans`, no events |
| `match_events` | ~15-40 KB JSON | native WiredTiger zstd lvl 9, ~60% saving vs raw BSON |
| `profile_statistics` | ~2-8 KB | leaves `champion×queue×lane`; no materialized `total`/`queueStats` |
| Redis value | ~1-4 KB JSON | 60s TTL, mget batch ≤100, pool 32 conn, circuit breaker 30s |
| `ChampionStatistics` heap | former 200 MB → <30 MB after `rusted-java` | batch 100, no `List<Match>` in memory, cursor streaming |
| Compute worker | max 2 concurrent | `CHAMPION` reserved for heavy, PROFILE least-loaded |

### Command / Class Names — Quick Lookup

| What | Name Pattern | Example |
|---|---|---|
| Parent command | `Class SimpleName.toLowerCase()` | `Summoner` → `summoner` |
| Child command | `remove(father).toLowerCase()` | `SummonerProfile` → `profile` |
| JSON key | identical to `name` | `summoner.children.profile` |
| Controller method | verb + resource | `profile()`, `search()`, `match()` |
| Service method | `get/generate/refresh/invalidate` | `getStatistics()`, `generateActivity()` |
| Queue key | `domain:puuid:filterKey` | `profile-statistics:<puuid>:<b64>` |
| Riot rate-limit | per `LeagueShard` | `R4J SUMMONER_REFRESH_COOLDOWN 60s NX` |

### Queries — Common Templates

```java
// Trova summoner per puuid (cache first)
Summoner s = RedisClient.get(RedisKey.SUMMONER.of(region, shard.name(), puuid), Summoner.class);
if(s==null) s = MongoDB.findSummoner(puuid, shard);

// Trova aggregato profilo
ProfileStatistics ps = MongoDB.findProfileStatistics(puuid, filter); // {puuid,filterKey}

// Cursor su match filtrati (no List in RAM)
MongoDB.forEachProfileStatisticsMatch(puuid, shard, filter, afterTime, untilTime, match -> acc.accept(match));

// Leaderboard paginato — find + $in senza $facet
List<SummonerLeaderboard> page = MongoDB.findLeaderboardPage(queue, region, offset, limit);
long total = MongoDB.countLeaderboard(queue, region);
```

---

## 7. API + Documentation Checklist

Every change to model/service/persist/filter/command/embed/cache/API **must** pass:

- [ ] **CodeGraph:** `codegraph status` up-to-date → `codegraph explore <symbol>` + `codegraph impact <symbol>` executed and blast radius verified before editing LoL code.
- [ ] **API sync:** controller / `lol.model` / `docs/api/<scope>/*.md` + `docs/api/lol-api.md` aligned in the same task. If internal-only, explicit verification that no exposed endpoint changes.
- [ ] **Doc sync:** `docs/architecture/README.md` + ADR + `docs/mongo/*` + `docs/audit/*` + `HANDBOOK.md` updated or `handoff` with `no-doc-change` rationale.
- [ ] **Stable presentation:** no restyling of embed/view/field order/text/layout unless explicitly requested.
- [ ] **Indexes & explain:** `explain("executionStats")` IXSCAN + `collStats` ok before merge.
- [ ] **Cache invalidation:** `RedisKey` + `RedisClient.set/delete` consistent with `puuid+filterKey`.
- [ ] **Queue gate:** new work goes through `QueueHandler` (no free `thenApplyAsync`).
- [ ] **Naming gate:** canonical model, no `*Document`, no `Optional`, no Lombok in operational code, service layout `//====`.
- [ ] **Tests:** unit analyzer + `MockMvc` controller + Mongo test DB; no secrets in commit.

> **Task incomplete until docs/API are synchronized.**

---

## Canonical Files to Open for Context

- `src/main/java/com/safjnest/lol/model/Filter.java`
- `src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`
- `src/main/java/com/safjnest/lol/service/ProfileService.java` + `ProfileAnalyzer.java`
- `src/main/java/com/safjnest/nosql/MongoDB.java` + `MongoMigration.java`
- `src/main/java/com/safjnest/lol/queue/QueueHandler.java` + `ComputeScheduler.java` + `SyncScheduler.java` + `RiotScheduler.java`
- `src/main/java/com/safjnest/redis/RedisKey.java` + `RedisClient.java`
- `src/main/java/com/safjnest/spring/controller/LolController.java` + `LolApiParameters.java` + `LolApiResponses.java`
- `docs/architecture/profile-statistics-source-of-truth.md`
- `docs/new-queue.md` + `docs/mongo/README.md` + `docs/api/lol-api.md`
