# MongoDB LoL migration

This directory describes the linear implementation of the MariaDB → MongoDB migration for LoL.

## Operational state

- MongoDB is the sole LoL runtime storage.
- MariaDB is read exclusively by MongoMigration for the backfill.
- LoL application reads go through MongoDB; there is no MariaDB fallback.
- A Mongo error is explicit in the runtime and does not trigger a MariaDB fallback.
- App.isTesting() selects beebot_test; otherwise beebot is used.
- Custom builds and summoner.metrics are out of scope.
- The initial backfill migrates only raw data: first `summoner` with `ranks{}` and `masteries[]` in the same batch, then `match` with participants.
- `profile_statistics`, `profile_activity`, `profile_matchups`, build and `leaderboard_aggregates` are built subsequently by the application; the latter contain only rebuildable snapshots of distribution and top-region.
- The complete `profile_statistics` flow, including the application key `puuid + filterKey`, is documented in [`docs/architecture/profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).
- Collections use table names (`summoner`, `match`, `profile_statistics`, `profile_activity`, `profile_matchups`, etc.) without `lol_` prefix.
- Derived projections `champions_indexable` and `profiles_indexable` are rebuilt from runtime Mongo data.
- The `summoner` document uses `_id = puuid`; numeric MariaDB identifiers and the duplicate `puuid` field are not written.
- The `match` document uses `_id` as the full Riot match ID and `region` as the sole shard field; `fullGameId`, `gameId`, `game_id` and `leagueShard` are not written. `patch` keeps the full version and `patchMajor` the first two segments for filters.
- The migration normalizes `match` document residues; other legacy documents and old Kryo payloads remain outside automatic cleanup and are removed manually before regeneration.
- Readers use `_id` as fallback only for defensive compatibility with documents outside the clean migration.
- Events are not in the `match` document: they live in `match_events` as JSON and the collection uses native WiredTiger Zstandard.

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

Operational: `docs/HANDBOOK.md` §6 + this README + `08-query-inventory.md` + `12-profile-record-indexes.md` + ADR-0009.
Historical archived in `_archive/` (01-06, 09-11): see `_archive/` for step-by-step migration — not needed for new features.

1. 07-agent-strategy.md (historical Mongo agent workflow)
2. 08-query-inventory.md — **operational**, indexed query inventory
3. 12-profile-record-indexes.md — **operational**, `profile_records` indexes
4. ADR-0009

## BSON rules

- Summoner: _id = puuid.
- Match: _id = full Riot match ID, for example EUW1_123.
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
