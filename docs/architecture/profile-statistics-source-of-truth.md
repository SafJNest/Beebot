# Profile statistics: single source of truth

- State: implemented — see `docs/HANDBOOK.md` §5.9/§5.10 for changes; `explain` IXSCAN verification still in `TODO.md P1` gate
- Last updated: 2026-08-31
- Scope: `SummonerOverview`, `SummonerProfile`, `ProfileMatchups`, `!summoner`, HTTP profile and LoL Mongo statistics
- Cache, persistence and composition owner: `ProfileService`
- Pure computation owner: `ProfileAnalyzer`
- Asynchronous refresh owner: `lol.queue.ComputeScheduler` (`QueueHandler` → `ComputeScheduler` `PROFILE`) — former `DatabaseTracker` (ADR-0014)

This document is the operational reference for the profile statistics flow. When starting new work search for these terms: `ProfileStatistics`, `ProfileMatchups`, `Filter`, `ActivityFilter`, `toSummonerKey`, `puuid + filterKey`, `recentMatches`, `lastUpdate`, `ComputeScheduler.startProfileStatistics`, `ComputeScheduler.startProfileMatchups`.

## Primary rule

For the same account, all filtered statistics are identified by the pair:

```text
PUUID + complete Filter
```

The PUUID identifies the Riot account. The `Filter` identifies exactly the dataset to aggregate. There is no longer a separate “profile” statistic, an “overview” statistic and a “champion” statistic computed separately for the same case: overview, profile and the generic command read the same `ProfileStatistics`.

`recentMatches` is not part of the aggregate. It is a lightweight projection loaded separately using the same PUUID and the same filter.

## Profile records

Records are a distinct projection in `profile_records`, with identity
`puuid + filterKey + metric`. `ProfileRecordService` owns reading and
computation; `ProfileRecordAnalyzer` is pure; `ComputeScheduler` executes the deduplicated job
`profile-records:<puuid>:<filterKey>` on the PROFILE worker. The
computation uses the same complete filter as statistics, but reads
`match_events` in batches of 250 only in its own pass. At the end of each
consumer `MatchMemoryUtils` recursively frees matches, events, documents and
nested collections; merely assigning `null` to the match does not replace this
release. Normal statistics refresh does not materialize timelines.

Final records use the flat participant fields. Timeline records use
`champion_kills` and `monster_events`: `FIRST_KILL_TIME` is the participant's first kill,
while `FIRST_BLOOD_TIME` explicitly requires the Riot first-blood.
Absence of events excludes only those metrics and does not produce
zero values. TEAM/MATCH records keep one row
per relevant participant and `gameShared=true`; PARTICIPANT
records omit the field. MMR is the snapshot derived from the participant in the match, never
the summoner's current MMR.

## Explicit profile refresh

`POST /api/lol/{shard}/profile/{puuid}/refresh` first refreshes Account,
summoner, rank and mastery via `RiotScheduler` (`QueueHandler.immediate(RiotScheduler.class, shard, ...)`) and persists each component. Only after
successful verification does it update the internal Mongo field `summoner.lastSeenAt` and
enqueue a single `IMMEDIATE profile-refresh:<puuid>` on `ComputeScheduler` (`QueueHandler.immediate(ComputeScheduler.class, PROFILE, ...)`).

The batch reads all matches for the PUUID/shard only once, in
`timeStart` order, with a Mongo cursor without materializing `List<Match>`, and regenerates
from scratch only the three canonical variants: statistics and
matchups and activity on the canonical filter of the current season, without
patch/queue/lane/champion. Derived filters remain on-demand. The profile champion breakdown is included in
`ProfileStatistics`; the refresh does not start global champion statistics and does not
require or modify the matchlist.

## Profile activity

The `GET /api/lol/{shard}/profile/{puuid}/activity` endpoint uses only the
`start`, `end`, `queue` and `champion` parameters. The controller builds a
`Filter.canonical()` when both `start` and `end` are omitted; with an explicit bound
it uses `Filter.summoner(start, end)`. It normalizes `queue=ALL` to a null queue
and uses `0` as the neutral value for champion.

The service reads matches with `MongoDB.findProfileStatisticsMatches`, then
reuses the same `buildMatchFilter` and the same complete filter verification
used by profile statistics. `ProfileActivity.from(...)` traverses the
result only once and updates in the same pass the total, `7x24` cells,
daily/hourly aggregates, queue, sessions and time windows.

The response is a dedicated projection and does not modify `SummonerView` or
`overview.recentMatches`. `recentSessions` contains all sessions of the
period in a single response, without a cursor. Heatmap cells are
ordered by `day * 24 + hour`, with Monday `0` and Sunday `6`.

Persistence follows the same read-through as statistics, but on a
dedicated derived collection: `Redis SUMMONER_ACTIVITY(PUUID, filterKey)`, then
Mongo `profile_activity` with `{ puuid, filterKey }`. A missing value returns `202
profile_activity_pending` and enqueues `NORMAL`. A stale value remains
a `200` with the persisted payload and `metadata.refresh=true`, then enqueues only
the activity in `BACKGROUND`; it is not computed in the request.
The response `filter` value is the canonical `Filter`, not a parallel
record.

## Profile matchups

`GET /api/lol/{shard}/profile/{puuid}/matchups` uses `ActivityFilter`, which
extends `Filter` with `minGames`. Omitted `queue` or `ALL` means all
queues, omitted `role` means all roles. If `start` is present without
`end`, the end is set to `23:59:59.999` of the current day in
the server timezone, so the key remains stable during the day; if only
`end` is passed, the lower bound remains open. When at least one of the two bounds is
present, it defines the period and takes precedence over `patch`; if both are missing,
`patch` is the fallback while the period remains that of the canonical season.
`minGames` defaults to 5 and filters only the matchup rows in the response; it does not
participate in `Filter.toSummonerKey()`.

`ProfileMatchups` has a separate contract from `ProfileStatistics`: it saves only
the leaves `champions.<championId>.<CanonicalQueue>.<position>`. Each leaf
contains the base accumulators and `matchups.<opponentChampionId>` for
opponents encountered in the same position. It does not save aggregates per champion,
queue or lane, nor `reference`, `winrate`, `kda` or `avg*`; these values are
computed by the consumer. `UNKNOWN` keeps games without a valid position and
Riot queues are canonicalized at ingestion.

`ProfileMatchups` is the only persisted source for matchups. A consumer
serving a global view rebuilds it by summing the leaves; `ProfileStatistics`
does not store `matchups` nor `duoStats`. `ProfileMatchups` has its own
Redis/Mongo read-through:

```text
Redis SUMMONER_MATCHUPS(PUUID, filterKey)
  -> Mongo profile_matchups { puuid, filterKey }
  -> ComputeScheduler profile-matchups:<puuid>:<filterKey> (PROFILE lane)
  -> Mongo.findProfileStatisticsMatches(..., Filter, 0, 0)
  -> ProfileAnalyzer.matchups(...)
  -> Mongo upsert and Redis cache
```

Computation does not happen during the request. A miss returns `202`; a
stale aggregate remains `200` with `metadata.refresh=true` and enqueues only the
matchup refresh at low priority. The refresh is executed by the general database worker, shared with the other
non-build refreshes; the build worker remains dedicated to build calculations only.
The existing profile JSON does not change.

## Stale freshness

A profile aggregate is stale after `30 days + deterministic jitter 0-14
days`, derived from the PUUID. The GET enqueues the `BACKGROUND` backstop only if
`lastSeenAt` is within the last 60 days; the field remains internal to the
`summoner` document, it does not belong to `Summoner` nor to the JSON/API. Stale never enqueues
the full refresh: overview enqueues only statistics, activity only activity and
matchups only matchups.

## The canonical filter

`Filter` is the object that must be passed without losing fields between UI, service, Mongo query, cache and persistence. The fields that participate in the filter are:

| Field | Meaning |
|---|---|
| `champion` | Summoner champion; `0` means all |
| `lane` | Participant lane |
| `queue` | Match queue |
| `rank` | Required tier |
| `rankBehavior` | `EXACT` or `GREATER_OR_EQUAL` |
| `patch` | Major patch; the match may also have the version suffix |
| `region` | Required League shard |
| `opponent` | Required opponent champion |
| `duo` | Required duo champion |
| `timeStart` | Period start, `0` means no limit |
| `timeEnd` | Period end, `0` means no limit |

The base profile, the leaderboard and APIs without filters use `Filter.canonical()`:

```text
champion = 0
lane = null
queue = null
rank = null
patch = null
region = null
opponent = 0
duo = 0
period = current season
```

Explicit filters modify the period of the same object. Queue, lane, champion and the other selectors produce a distinct aggregate.

### `toKey()` and `toSummonerKey()` are not interchangeable

- `Filter.toKey()` remains the historical key for champion/build aggregates and does not contain the full profile period.
- `Filter.toSummonerKey()` is the dedicated key for `profile_statistics`, Redis and `ComputeScheduler`.

`toSummonerKey()` builds this logical string:

```text
champion|lane|queue|rank|rankBehavior|patch|region|opponent|duo|timeStart|timeEnd
```

Null or neutral values are represented with `*`. The string is encoded with URL-safe Base64 without padding. The effective form is therefore:

```java
Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(rawFilter.getBytes(StandardCharsets.UTF_8));
```

The complete value, not just the period or the queue, must be used for reading. If even a single field changes, the result is another aggregate and must have another document.

## Mongo document

The collection is `profile_statistics`. The target document is flat and stores only the aggregatable leaves:

```json
{
  "_id": "random stable ObjectId",
  "puuid": "Riot PUUID",
  "filterKey": "Filter.toSummonerKey()",
  "timeStart": 1710000000000,
  "timeEnd": 1710002100000,
  "lastUpdate": 1710002200000,
  "oldestMatchAt": 1710000000000,
  "newestMatchAt": 1710002100000,
  "champions": {
    "157": {
      "RANKED_SOLO": {
        "TOP": {
          "games": 42,
          "wins": 24,
          "blueGames": 20,
          "blueWins": 13,
          "redGames": 22,
          "redWins": 11,
          "championLevelTotal": 756
        }
      }
    }
  },
  "pings": {},
  "spellOne": {},
  "spellTwo": {}
}
```

The root field `statistics` must not exist for new documents.
`champions` is the sole source of truth for main statistics, at
granularity `champion × CanonicalQueue × position`.
Each leaf also stores the base counters per side (`blueGames`, `blueWins`,
`redGames`, `redWins`); winrate per queue, lane or side remains derived from these
counters and is not materialized as a separate field.
Each game goes into one leaf; a missing or non-applicable position always uses
`UNKNOWN`.

Riot queues are normalized at ingestion into `CanonicalQueue`
(`RANKED_SOLO`, `RANKED_FLEX`, `NORMAL_DRAFT`, `ARAM`, `ARENA`, etc.).
`total`, `queueStats`, `laneStats`, champion totals,
`context`, `reference`, `winrate`, `kda` or `avg*` fields are not persisted. Discord can recreate
these views in memory, but Mongo, Redis and HTTP expose only the leaves.
`isOtp` is likewise derived from the leaves for a consumer and is never serialized or persisted.
`pings`, `spellOne` and `spellTwo` remain dedicated structures because they do not
require the same granularity. Matchups live only in
`profile_matchups`; there are no matchup or duo aggregates in the main
document.

`championLevelTotal` is the sum of `MatchParticipant.getChampionLevel()` only.
A missing metric field means historical raw data not available; a present `0`
is a collected value. Averages belong to the consumer. Arena fields exist only
in the `ARENA → UNKNOWN` leaf: `avgArenaPlacement` is
`arenaPlacementSum / games` of that leaf.

`timeStart` and `timeEnd` in the payload describe the interval/progress of the aggregated data. The complete filter identity, including the end of the requested period, is `filterKey`.

`lastUpdate` is assigned only after finishing the scan and computation of matches. It is the timestamp that Discord and API show to indicate when the aggregate was computed.

## Mongo identity: operational explanation

The runtime owns the unique index `profile_statistics_identity` on `{ puuid,
filterKey }`. The logical key remains the pair, while `_id` is the physical
identity of the document. Bootstrap is create-only: before creating the index
it checks for missing and duplicate identities and aborts startup without cleanup.

### Why these two keys

The application query is always:

```javascript
db.profile_statistics.findOne({
  puuid: "<PUUID>",
  filterKey: "<Filter.toSummonerKey()>"
})
```

PUUID alone is not enough: the same summoner can have statistics for current split, previous split, all time, queue, lane, champion or different matchups. `filterKey` alone is not enough: the same filter is computed for many accounts. The pair is the unique logical key of the result.

### Why the pair is unique

The application flow treats as invariant:

```text
a single ProfileStatistics per PUUID and complete filter
```

a single `ProfileStatistics` per PUUID and complete filter. The Mongo unique index
protects the invariant even when two concurrent refreshes execute the upsert.
The lookup remains exact on the complete pair.

### Why `_id` is not the lookup key

`_id` is random (`ObjectId`) and is generated only on first insertion. It contains no PUUID, period or filter. The pair `puuid + filterKey` is the business key; `_id` is only the stable physical identity of the Mongo document.

The write path uses an atomic upsert:

```javascript
db.profile_statistics.updateOne(
  { puuid: "<PUUID>", filterKey: "<CANONICAL_KEY>" },
  {
    $set: {
      puuid: "<PUUID>",
      filterKey: "<CANONICAL_KEY>",
      timeStart: ..., timeEnd: ..., lastUpdate: ...,
      champions: { <championId>: { <canonicalQueue>: { <position>: <Stats> } } },
      pings: ..., spellOne: ..., spellTwo: ...
    },
    $setOnInsert: { _id: ObjectId() }
  },
  { upsert: true }
)
```

Consequences:

1. if the pair does not exist, Mongo creates a document with a random `_id`;
2. if the pair exists, Mongo updates the same document;
3. `_id` is not overwritten because it is present only in `$setOnInsert`;
4. the application pair remains stable even during concurrent refreshes;
5. do not use `replace` with an `_id` derived from PUUID or season;
6. do not search anymore by `{ puuid, seasonStart }`.

### OTP classification

For each CanonicalQueue there is at most one OTP champion. The champion's games
are summed across all playable positions of the queue; with N games, p1 and p2
as share of the top two champions, the first is OTP when:

```text
N >= 20
p1 >= 0.50 + 0.30 * exp(-N / 250)
p1 - p2 >= 0.15
```

The `isOtp: true` flag is saved in every playable leaf of the winning champion
in the same queue; for every other champion the field is omitted. It is a
derived classification, not a counter: each `finish()` clears and rebuilds it. UNKNOWN and non-playable lanes cannot produce an OTP.

### Mongo bootstrap

Bootstrap creates only missing collections and indexes and does not modify or
remove secondary indexes. `profile_statistics_identity` is preceded by the
preflight for missing or duplicate identities; legacy documents must be
handled by separate migration/regeneration and a document with a different filter must not be reused just because it belongs to the same PUUID.

To diagnose a mismatch in Mongo:

```javascript
db.profile_statistics.getIndexes()
db.profile_statistics.find({ puuid: "<PUUID>" }, {
  _id: 1,
  puuid: 1,
  filterKey: 1,
  timeStart: 1,
  timeEnd: 1,
  lastUpdate: 1
})
```

The document's `filterKey` must be compared byte-for-byte with `Filter.toSummonerKey()` generated by the command. Do not compare only `timeStart`.

## Read and refresh flow

```text
Discord/API request
  -> resolves Summoner and PUUID
  -> builds a complete Filter
  -> ProfileService.get(PUUID, Filter)
       -> Redis SUMMONER_STATISTICS(PUUID, filterKey)
       -> Mongo {puuid, filterKey}
   -> hit: uses ProfileStatistics
   -> miss: ComputeScheduler.startProfileStatistics(Summoner, Filter)
        -> partial/pending response, no synchronous computation
        -> PROFILE queue (insert-time least-loaded between PROFILE/CHAMPION)
            -> Mongo match projection with the same Filter
            -> ProfileStatistics.accumulate(match, puuid, filter)
            -> set lastUpdate after computation
            -> atomic upsert {puuid, filterKey}
            -> cache ProfileStatistics
            -> invalidate recent matches and profile page
```

The owner case `test highstats` executes an explicit rebuild of profile statistics
for Challenger, Grandmaster and `tracking=true`, considering all
active regions and the two ranked queues for high elo. It uses the same
`Filter.canonical()` as the frontend, forces `rebuild=true` even when the aggregate
already exists, deduplicates PUUIDs and processes one page at a time waiting for
completion before the next page. This way the workload
does not fill the FIFO nor keep the entire high-elo list in memory.

The rank entries job, started by `pushhighelo` or `getallrank`, saves each
`LeagueEntry` only in the queue it belongs to. High elo (Master+) and all
entries (below Master) share the same job and cannot overlap;
a concurrent request adds the complementary tier to the same
execution. For an already persisted summoner it does not perform Riot identity calls and
atomically updates only the path `summoner.ranks.<QUEUE>`. For a missing PUUID
it first resolves Summoner and, if the Riot ID is not already available, Account, then
persists the identity before the rank. The tracker starts one worker per shard; the
outbound rate limiting remains owned by `RiotScheduler` per shard.

The owner command `tracker` reads on demand the state of schedulers and `Job`s via `QueueHandler`/`Registry`, without adding logging on
the hot path of refreshes.

For activity the synchronous flow is instead:

```text
API request
  -> builds complete Filter
  -> Redis SUMMONER_ACTIVITY(PUUID, filterKey)
  -> Mongo profile_activity {puuid, filterKey}
  -> Mongo findProfileStatisticsMatches(..., Filter, 0, 0)
  -> ProfileActivity.from(...): single scan, shared stats and accumulators
  -> Mongo upsertProfileActivity(PUUID, Filter, activity)
  -> Redis SUMMONER_ACTIVITY(PUUID, filterKey)
```

Asynchronous work deduplication uses the same logical identity:

```text
in-flight key = profile-statistics:puuid:filter.toSummonerKey()
```

Two requests for the same PUUID and the same filter share the Future while the job is queued or executing. Two different filters can be enqueued separately; the general worker executes only one non-build refresh at a time and can work in parallel with the build worker. The marker is removed both after success and after error, so a subsequent request can retry.

## Computation and filters

`ProfileService` is the sole owner of cache, query and persistence; `ProfileAnalyzer` is the sole owner of pure computation. `MongoDB.findProfileStatisticsMatches` uses the complete filter and a projection of the necessary matches/participants. `ProfileStatistics.matchesFilter` is also applied after reading to ensure that relational filters are not satisfied by the wrong participants.

All these fields must be respected:

- queue;
- region/shard;
- summoner champion;
- summoner lane;
- major patch;
- rank and rank behavior;
- opponent on the opposing lane;
- duo on the allied team;
- period `timeStart/timeEnd`.

During aggregation total, queue, lane, champion, matchup, duo, ping and spell are produced in the same pass. Do not introduce a separate service for pings, matchup or champion overview.

## `recentMatches` and raw data

`recentMatches` is a separate responsibility:

- Redis cache: `SUMMONER_RECENT_MATCHES` with PUUID and `filterKey`;
- separate Mongo query with `MatchResult` projection;
- invalidation after a successful statistics refresh;
- no `recentMatches` field inside `ProfileStatistics` or in the `profile_statistics` document.

Views that require events or complete matches, such as timeline and OP.GG details, continue to read raw matches and events from their collection. They must not use the aggregate to reconstruct events.

## Application composition

### SummonerOverview, SummonerProfile and `!summoner`

All use the same `ProfileStatistics` for the PUUID and current filter as data source. Composition and presentation however remain separate: a change to the model or source does not authorize a change to the existing embed.

`SummonerOverview.from(...)` composes:

```text
ProfileStatistics + ranks + masteries + recentMatches
  -> SummonerOverview
  -> SummonerView
```

The generic `!summoner` command no longer uses a separate statistics path: it reads the same aggregate as the overview, but keeps the previous embed format. It therefore shows the fields already present in the generic view, fed by the new `ProfileStatistics`, plus `lastUpdate`; it must not automatically show every new field added to the aggregate.

The base overview keeps its historical format and includes pings in the already existing block. Matchup and the full champion list remain in their respective dedicated views, using the same `ProfileStatistics`. `recentMatches` is composed separately from the HTTP profile and is not loaded by `LeagueMessage.getSummonerEmbed`.

`lastUpdate` is formatted in the Discord layer as a readable date/time and relative Discord timestamp. The persisted value always remains a numeric timestamp in milliseconds.

### Discord menu

`OVERVIEW_PING` and `OVERVIEW_OBJECTIVES` are no longer active flows. Pings are already inside the base overview. Objectives are no longer computed nor persisted. Legacy values may remain in the enum only to normalize old component/button state, but must not be exposed by menus, buttons or dispatchers.

## Cache and invalidation

Redis keys are separated by namespace: `beebot:lol:r4j:*` identifies
Riot4J payloads, while `beebot:lol:ls:*` identifies League OS projections and queues.
Application keys tied to an account put real values
before the resource: `beebot:lol:ls:<region>:<shard>:<puuid>:summoner` or
`beebot:lol:ls:<region>:<shard>:<puuid>:summoner:statistics:<filterKey>`, so scanning by
PUUID finds the summoner's projections without a literal `puuid` token.

| Data | Key | TTL | Owner | Invalidation |
|---|---|---:|---|---|
| summoner base | `SUMMONER(region, shard, PUUID)` | 6h | `SummonerService` | after component refresh or `SummonerService.invalidate` |
| summoner rank | `SUMMONER_RANKS(region, shard, PUUID)` | 6h | `RankService` | after component refresh or `SummonerService.invalidate` |
| summoner mastery | `SUMMONER_MASTERIES(region, shard, PUUID)` | 6h | `MasteryService` | after component refresh or `SummonerService.invalidate` |
| aggregated statistics | `SUMMONER_STATISTICS(region, shard, PUUID, filterKey)` | 6h | `ProfileService` | update after upsert |
| aggregated activity | `SUMMONER_ACTIVITY(region, shard, PUUID, filterKey)` | 6h | `ProfileService` | update after upsert |
| summoner matchups | `SUMMONER_MATCHUPS(region, shard, PUUID, filterKey)` | 6h | `ProfileService` | update after upsert |
| recent matches | `SUMMONER_RECENT_MATCHES(region, shard, PUUID, filterKey)` | 1h | `ProfileService` | after statistics refresh |
| summoner overview | `SUMMONER_OVERVIEW(region, shard, PUUID)` | 1h | `ProfileService` | after statistics or summoner component refresh; does not contain `recentMatches` |
| raw match | existing match keys | per `RedisKey` | `MatchService`/`Tracker` | per match flow |

A Mongo aggregate without `champions` is treated as obsolete and
regenerated by `ComputeScheduler`. Redis key TTLs remain
defined exclusively by `RedisKey`; expiration reduces projection retention,
but does not replace explicit invalidation after a successful refresh.

Do not use the profile page cache as source of truth for statistics. The source is always `ProfileStatistics` read with the complete filter; the page is a derived composition.

## API and states

The API continues to return the canonical models `SummonerView` and `SummonerOverview`. In the public JSON:

- `overview.statistics` contains the filtered leaves in `champions`;
- `overview.statistics.champions[championId][canonicalQueue][position]` distinguishes champion, queue and lane;
- `overview.recentMatches` contains the separate lightweight list;
- `overview.statistics.lastUpdate` indicates computation completion;
- complete `Match` remains reserved for details and timeline.

If identity, rank and mastery are ready but `ProfileStatistics` is missing, the HTTP profile immediately returns the available profile as `PARTIAL` with empty `recentMatches` and enqueues the refresh; the recent-match query starts only when the aggregate is available. If base components are missing, it keeps the `202 profile_pending` behavior. Discord shows the preparation message only until the exact PUUID/filter pair is available.

## Checklist for future work

Before modifying this flow verify:

1. the new field belongs to `Filter`, to `ProfileStatistics` or to raw matches;
2. the field is included in `toSummonerKey()` if it changes the dataset;
3. Redis and Mongo use the same key;
4. `MongoDB` reads and writes `{puuid, filterKey}`;
5. the pair `{ puuid, filterKey }` remains the application identity;
6. computation goes through `ProfileAnalyzer` via `ProfileService`, not via Discord/API/controller;
7. `recentMatches` remains separate;
8. activity uses the same `Filter` and the shared match query, without creating a second semantics for queue or period;
9. `lastUpdate` is written only after computation;
10. overview, profile and `!summoner` read the same object;
11. existing presentation remains unchanged unless an explicit style refactor is requested;
12. API docs, audit, Mongo documentation and operational rules remain synchronized.

### Canonical files to open to recover context

- `src/main/java/com/safjnest/lol/model/Filter.java`
- `src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`
- `src/main/java/com/safjnest/lol/service/ProfileService.java`
- `src/main/java/com/safjnest/lol/service/ProfileAnalyzer.java`
- `src/main/java/com/safjnest/nosql/MongoDB.java`
- `src/main/java/com/safjnest/lol/queue/QueueHandler.java` + `lol/queue/scheduler/ComputeScheduler.java`
- `src/main/java/com/safjnest/lol/tracker/Tracker.java`
- `src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`
- `src/main/java/com/safjnest/lol/message/LeagueMessage.java`
- `src/main/java/com/safjnest/lol/model/summoner/SummonerOverview.java`
- `docs/mongo/01-db-structure.md`
- `docs/architecture/adr/0004-profile-statistics-refresh-queue.md`
- `docs/architecture/adr/0010-database-refresh-queue.md`
- `docs/architecture/adr/0008-endpoint-cache-and-async-lookups.md`
