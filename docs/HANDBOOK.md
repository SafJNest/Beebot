# Beebot developer handbook

Operational guide for changes to commands, LoL services, persistence and jobs.
Repository conventions are in [`AGENTS.md`](../AGENTS.md); accepted architecture
decisions are in [`architecture/adr`](architecture/README.md).

## 1. Rules and precedence

When documents conflict, follow `AGENTS.md`, then accepted ADRs, then this
handbook. Treat code as evidence of current implementation, not as an
architecture decision. Report conflicts rather than silently changing owners.

For LoL code changes, check CodeGraph status, sync a stale index, then inspect
the affected symbol and its impact before editing.

## 2. Core contracts

- Canonical LoL success models live in `lol.model`; Spring owns controllers,
  configuration and HTTP errors.
- `Summoner`, `Rank`, `Mastery`, `Match`, `Participant` and `MatchResult` are
  canonical. Reuse existing mapping utilities.
- Mongo is the LoL runtime store. MariaDB is limited to `MongoMigration`.
- Redis stores caches and derived indexes. It is not a durable queue.
- Changes to data or persistence preserve existing command and API presentation
  unless a presentation change is requested.

## 3. Ownership map

| Responsibility | Owner |
|---|---|
| Commands and registration | `commands/`, `rsc/commands.json` |
| HTTP routes and validation | `spring/controller/`, `LolApiParameters` |
| Domain models and behavior | `lol/model/`, `lol/service/` |
| Job lifecycle and routing | `lol/queue/QueueHandler`, schedulers |
| Periodic tracking | `lol/tracker/` |
| Mongo persistence and backfill | `nosql/MongoDB`, `MongoMigration` |
| Redis keys and invalidation | `redis/RedisKey`, `CacheInvalidationService` |

## 4. Shared data flow

Profile aggregates use `{ puuid, filterKey }`, where `filterKey` comes from the
complete `Filter.toSummonerKey()`. `Filter.toKey()` identifies champion/build
data and is not a substitute. Keep the filter consistent across Mongo, Redis
and job deduplication. See the [profile source of truth](architecture/profile-statistics-source-of-truth.md).

`ApiResult` maps `READY` and `PARTIAL` to `200`, `PENDING` to `202` and
`NOT_FOUND` to `404`. Use the existing response mapper and canonical models.
Cache misses enqueue work through `QueueHandler`; persistence success precedes
cache updates and invalidation.

## 5. Operational steps

### 5.1 New Discord command

Register the command in `rsc/commands.json` and its `SlashCommand` class. Reuse
existing identity lookup, message types and rendering. Keep computation in the
owning service and preserve the existing presentation.

### 5.2 New HTTP endpoint

Use the existing scope controller where possible. Parse every path and query
parameter with `LolApiParameters`, call the owning service and return canonical
models through `LolApiResponses`. Update the endpoint page and API index when
the public contract changes.

### 5.3 New service

Place domain behavior in `lol/service`. Order members as constants, fields and
constructors, public methods, one separator, then private methods. Keep cache,
Mongo and scheduling in the existing service owner; put pure calculations in an
Analyzer.

### 5.4 New model

Place LoL success models in `lol.model`. Extend a canonical concept instead of
adding a duplicate DTO or persistence-only alias. Keep Spring DTOs for HTTP
errors and infrastructure representations.

### 5.5 New queued job

Submit through `QueueHandler` to `RiotScheduler`, `ComputeScheduler` or
`SyncScheduler`, according to the work. Use a stable logical key for
deduplication. Report phase and item progress through the job; do not add a
parallel queue or retry store.

### 5.6 New Mongo collection or entity

Use `AbstractEntity` and `NoSqlEntityExecutor` where applicable. Register the
collection and its create-only indexes in `MongoDB`. Define identity and
migration behavior before adding a persisted shape. Do not add duplicate
`*Document` models or dual-write paths.

### 5.7 New Mongo query

Add the query to `MongoDB`, use typed filters and narrow projections, and stream
large result sets with a cursor. Add or reuse an index, inspect
`explain("executionStats")`, and update `docs/mongo/08-query-inventory.md`.

### 5.8 New Redis cache

Declare the key and TTL in `RedisKey`. Use `RedisClient` and invalidate through
`CacheInvalidationService` after the durable write succeeds. Keep cache values
rebuildable from Mongo.

### 5.9 New aggregate or analyzer

Put pure accumulation and finalization in an Analyzer. Let the owning service
read bounded Mongo batches, persist the completed aggregate, then update Redis.
Define the aggregate identity and include every dataset-changing filter in its
key.

### 5.10 Filter change

Update all affected serialization methods, Mongo predicates, post-read matching,
cache keys and job keys together. A field that changes the dataset must be part
of `toSummonerKey()` or the relevant aggregate key. Review stored data and
indexes for migration needs.

### 5.11 Tracker or periodic job

Schedule through `TrackerScheduler` and dispatch work through `QueueHandler`.
Respect shard routing and existing refresh ownership. Report `total`,
`completed` and `missing` through job progress when processing collections of
items.

### 5.12 Tests and validation

Use unit tests for pure analyzers and focused integration tests for persistence
or HTTP behavior. Report exactly which checks ran; an offline test is not proof
of live Mongo, Redis, Riot or Discord behavior.

## 6. Quick reference

| Data | Owner | Storage |
|---|---|---|
| Summoner, ranks, masteries | `SummonerService`, `RankService`, `MasteryService` | `summoner` |
| Matches and events | `MatchService`, `Tracker` | `match`, `match_events` |
| Profile statistics, activity, matchups | `ProfileService`, `ProfileAnalyzer` | `profile_statistics`, `profile_activity`, `profile_matchups` |
| Champion statistics and builds | `ChampionService`, `ChampionAnalyzer` | `champion_statistics`, `champion_builds` |
| Competitive ranks and leaderboard indexes | `CompetitiveService`, `LeaderboardService` | `competitive`, Redis |

The unique profile identity index is `profile_statistics_identity` on
`{ puuid, filterKey }`. Match `_id` is the complete Riot match ID; summoner
`_id` is the PUUID. Query and migration details are in [`docs/mongo`](mongo/README.md).

## 7. Completion checklist

- Review CodeGraph impact before LoL code changes and resolve ADR conflicts.
- Review affected API endpoints; update API docs only when their contract changes.
- Update the owning architecture or Mongo source-of-truth document when behavior
  or persistence changes.
- Preserve presentation for data-only changes.
- Report focused checks separately from live integration evidence.

## Arena builds

CHERRY uses the existing champion API and build flow. Its approved schema and
processing rules live in [`docs/arena-build`](arena-build/README.md) and
ADR-0012.
