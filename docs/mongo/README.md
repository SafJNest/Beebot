# MongoDB LoL migration

This directory describes the linear implementation of the MariaDB → MongoDB migration for LoL.

## Operational state

- MongoDB is the sole LoL runtime storage.
- MariaDB is read exclusively by MongoMigration for the backfill.
- LoL application reads go through MongoDB; there is no MariaDB fallback.
- A Mongo error is explicit in the runtime and does not trigger a MariaDB fallback.
- App.isTesting() selects beebot_test_test; otherwise beebot_test is used (current MongoDB constants).
- Custom builds and summoner.metrics are out of scope.
- The initial backfill migrates only raw data: first `summoner` with `ranks{}` and `masteries[]` in the same batch, then `match` with participants.
- Identity and participant upserts initialize `ranks{}` only when the summoner is new; existing ranks remain owned by the explicit rank writers and are never replaced by an identity refresh.
- Owner-only migration commands enqueue their work on the Mongo database worker: `!test migrate` (or `!test migrate all`) runs the global backfill, `!test migrate tracked` recovers missing raw matches and RankProgress for `summoner.tracking=true`, and `!test migrate ranks` recovers canonical `summoner.ranks` then rebuilds its derived projections. The recovery commands are `!test migrate fix-tracked`, which repairs only stored tracked RankProgress, and `!test migrate rankprogress [runId]`, which runs the checkpointed Mongo RankProgress schema/history stages globally. MariaDB remains reachable only through `MongoMigration`.
- `!test regenerate competitive` rebuilds only `competitive` from canonical Mongo data. It replaces every competitive row, so it removes retired projection fields such as `tier`. `!test regenerate aggregates` rebuilds only `leaderboard_aggregates`. Permanent leaderboard indexes are warmed independently at application startup.
- `!test regenerate profiles`, `records`, `champions` and `indexables` rebuild their respective Mongo-derived data; `!test regenerate all` runs the complete sequence on the Mongo scheduler.
- `!test fix-timeline <puuid>` streams stored matches for that PUUID from newest to oldest, fetches each Riot timeline directly by match ID, regenerates the complete compact event payload, and replaces only that match's `match_events` record. It stops after 10 consecutive matches without a Riot timeline. Job progress reports completed matches against the initial total; missing timelines are counted, and stored match and participant data are left untouched.
- `!test fix-timeline-patch <patch>` starts one stream per stored region for that `patchMajor`. Each region fetches its timelines independently with a 500 ms delay between requests, logs `current/total`, regenerates the complete compact event payload, and replaces only that match's `match_events` record. It processes the full patch even when timelines are missing. Each regional job reports total, completed, updated, missing and failed matches; stored match and participant data are left untouched.
- MariaDB's historical participant KDA string is split into the flat `kills`, `deaths` and `assists` fields before the raw match is written to Mongo.
- Global profile-record rebuilds scan all season PUUIDs through a Mongo cursor in batches of 2,000; the batch size does not cap the total population.
- `profile_statistics`, `profile_activity`, `profile_matchups`, build and `leaderboard_aggregates` are built subsequently by the application; the latter contain only rebuildable snapshots of distribution and top-region.
- The complete `profile_statistics` flow, including the application key `puuid + filterKey`, is documented in [`docs/architecture/profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).
- Collections use table names (`summoner`, `match`, `profile_statistics`, `profile_activity`, `profile_matchups`, etc.) without `lol_` prefix.
- Derived projections `champions_indexable` and `profiles_indexable` are rebuilt from runtime Mongo data. Profile-indexable cleanup scans projection IDs and deletes stale documents in batches of 2,000, so the Mongo filter stays below the BSON document limit.
- The `summoner` document uses `_id = puuid`; numeric MariaDB identifiers and the duplicate `puuid` field are not written.
- The `match` document uses `_id` as the full Riot match ID and `region` as the sole shard field; `fullGameId`, `gameId`, `game_id` and `leagueShard` are not written. `patch` keeps the full version and `patchMajor` the first two segments for filters.
- The migration normalizes `match` document residues; other legacy documents and old Kryo payloads remain outside automatic cleanup and are removed manually before regeneration.
- Readers use `_id` as fallback only for defensive compatibility with documents outside the clean migration.
- Events are not in the `match` document: they live in `match_events` as JSON and the collection uses native WiredTiger Zstandard.
- Champion-stat rebuilds join `match_events` to match records in batches of 1,000; profile-matchup and champion-build rebuilds use batches of 100. Each path processes the bounded event batch and recursively releases the temporary event tree. Champion stats and builds exclude a match when its stored event payload has no usable timeline; it contributes neither picks nor games to those aggregates. The compact timeline marks the available frame nearest minute 15 (without interpolation); event counts use timestamps through minute 15. Missing fields inside an available timeline remain absent.
- Champion builds use final participant fields for item identity and batch-loaded `match_events` for attributed purchase and skill-upgrade times. If a timeline exists but a particular event is ambiguous or absent, final-inventory counts remain, without a time sample for that item. The same rule applies when build aggregation runs as part of a champion statistics refresh.
- Champion-build Mongo documents carry a root `buildVersion`; reads and freshness checks ignore documents from older aggregation generations so the normal API request schedules their rebuild.

## Code structure

LoL Mongo/NoSQL persistence lives in package `com.safjnest.nosql` and has these main files:

- `src/main/java/com/safjnest/nosql/MongoDB.java`: URI, database, collection, query, mapping and runtime writes;
- `src/main/java/com/safjnest/nosql/MongoMigration.java`: batchable MariaDB → Mongo backfill;
- `src/main/java/com/safjnest/nosql/AbstractEntity.java` and `NoSqlEntityExecutor.java`: common infrastructure for entities persisted in NoSQL.

SQL adapters used exclusively by the backfill remain separate in package `com.safjnest.sql`:

- `src/main/java/com/safjnest/sql/QueryRecordParser.java`: common detached parser for MariaDB rows and Mongo documents;
- `src/main/java/com/safjnest/sql/database/LeagueDB.java`: SQL adapter reduced to the queries needed by `MongoMigration`.

Do not introduce LeagueStore, store or infrastructure packages, external codec/mapper, outbox, dual-write proxy or *Document classes.

## Reading order

Operational: `docs/HANDBOOK.md` §6 + this README + `08-query-inventory.md` + `12-profile-record-indexes.md` + `13-contextual-ranking.md` + ADR-0009.

1. 08-query-inventory.md — indexed query inventory
2. 12-profile-record-indexes.md — `profile_records` indexes
3. 13-contextual-ranking.md — exact-rank counts and Redis ZSET segments
4. ADR-0009

## BSON rules

- Summoner: _id = puuid.
- Match: _id = full Riot match ID, for example EUW1_123.
- Competitive: `_id` is BSON `Binary` with the first 16 SHA-256 bytes of the UTF-8 canonical key `puuid:queue`; `puuid`, `queue`, `region`, `mmr`, `primary`, optional `otpChampionId` and `lastUpdate` are the projection fields. Riot tier is not persisted.
- Match: `region` is the sole shard field; `patchMajor` is derived from `patch` and used in filters.
- R4J enum: name().
- Ban: bans.BLUE and bans.RED, always present even if empty.
- Participant: flat fields; no mega-nested build field.
- Events: `match_events` collection, JSON payload with checksum and original size; the collection is created with `block_compressor=zstd`.
- Build and statistics: `build` is structured BSON; `profile_statistics` is a flat document with aggregates directly at root, never an opaque string and never `legacyPayload`.
- Activity: `profile_activity` saves the structured `ProfileActivity` payload with identity `{ puuid, filterKey }`, separate from `profile_statistics`.
- Matchups: `profile_matchups` saves the structured `ProfileMatchups` payload with identity `{ puuid, filterKey }`, separate from `profile_statistics`.
- MariaDB retains historical data read by the migration; the LoL runtime does not query it.
- Redis: uses the same shared Jackson codec and remains cache, without data migration.

For `profile_statistics`, `profile_activity` and `profile_matchups`, `_id` is not a business key: lookup and upsert always use `{ puuid, filterKey }`. `$setOnInsert` generates a random ObjectId only on first write and subsequent updates keep the same `_id`; the respective unique indexes protect the uniqueness of the pair.

## Indexes and space

During backfill collections are created; normal startup and the
RankProgress job do not create indexes. `match_rank_progress_history` and
`match_rank_progress_subjects` must therefore be applied before
rebuilding. Each page first runs a preflight of Mongo `_id`s: full MariaDB data
is read only for missing summoners and matches, while missing events
for already present matches require only the `events` column. Summoners are
sent with unordered bulk of 20,000 documents; matches remain in sub-batches
of 1,000.

Initialization is create-only and idempotent: it creates missing indexes, reuses compatible ones and aborts bootstrap on name, key pattern or option conflicts. It does not run `dropIndex` and the preflight of the unique index `profile_statistics_identity` aborts startup on missing or duplicate identities without modifying data. `MongoDB.spaceAudit(sampleSize)` collects `collStats`, `indexSizes`, sampled average/maximum BSON, presence of `userId`, tracking and regions.

Application compression is disabled: `match_events` uses native WiredTiger compression. The Mongo server must use `zstdCompressionLevel: 9`; match, summoner, masteries, build and statistics remain structured BSON documents and are compressed by the server.

## Configuration

rsc/settings.json contains a server-level URI. The URI must not contain the application database. MongoDB creates missing collections and indexes idempotently during lazy initialization; existing incompatible indexes require an explicit operational migration. Real credentials and URIs must not appear in logs, tests or commits.

## Gate

Before completion verify LoL Mongo-only reads and writes, no runtime import of LeagueDB, no mirror/outbox/dual-write proxy and tests for test database, registry/idempotency/preflight of indexes, bans, enum, flat participant, conversions and migration resume/high-water mark.

## Arena in the existing build collection

Exactly one champion_builds document per normal filterKey stores standard and
build.arena. MongoDB owns both entrypoints. Standard single/bulk updates `$set`
root standard metadata and individual standard build paths, preserving Arena.
Arena sets build.arena atomically and initializes filterKey on insertion. No
new identity, discriminator, collection, replacement or read-merge-replace.
Arena-only inserts have no standard buildVersion/readiness marker. Standard
read projection removes Arena before deserialization, so malformed Arena cannot
break standard reads and the public response stays unchanged.

Arena schema=4/aggregation=4 uses JsonCodec structured BSON with only overall,
core, Prismatic, augment and minimal coverage aggregates. Its full subtree is validated/encoded before the update. Local size/headroom guards cannot
measure existing standard fields without reading: Mongo's atomic combined-document
limit remains authoritative, and rejection leaves the previous value intact.
The old schema 3 subtree is unsupported and must be regenerated; no compatibility
reader or conversion is provided.

The exact full-patch CHERRY match query includes queue/patchMajor/champion plus
exact patch. Each source batch contains at most 100 matches and joins their event
documents through `match_events._id`; matches with no event document are still
delivered. `ArenaGameParser` reads attributed `item_events`, orders purchases by
timeline timestamp and falls back to final slots for the first Prismatic when
needed. Arena timestamps are used for ordering and are not persisted. Standard
build/stat reader eligibility is unchanged. Internal callers provide a
patch-correct immutable item catalog.

The batch callback is synchronous. Each Arena `Match` holds one decoded
`JSONObject` timeline in `events`; the duplicate `eventData` map is dropped.
Mongo documents and event trees are released after processing.
`ArenaChampionAnalyzer` retains aggregate counters and compact primitive IDs,
not match or timeline objects. Its `coverage.matches` value is incremented from
the unique Mongo match documents.

Index definitions/options and explains were not inspected on a live database;
champion_builds_filter remains the documented candidate access path. No index
creation/migration or automatic rebuild is introduced. Live Mongo round-trip,
combined BSON/cardinality, representative heap and rollout gates remain open.
See [phases](../arena-build/phases.md) for current offline evidence.

Code at MongoDB.PRODUCTION_DATABASE/TEST_DATABASE currently maps production to
beebot_test and testing to beebot_test_test; the historical names in ADR-0009
configuration are stale. No database connection or production operation was
performed in Phase 3. Do not identify an isolated environment from its name alone.
