# ADR-0009: MongoDB persistence and LoL migration

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-17
- Approved: 2026-07-18, main-agent approval after the full implementation request

## Amendment 2026-07-27

The `match` document uses the full Riot match ID directly in `_id` and `region` as the sole shard field. `fullGameId`, `gameId`, `game_id` and `leagueShard` are mapping residues and are not persisted. `patch` keeps the full version, while `patchMajor` keeps the first two segments, for example `14.2`, and is the field used by Mongo filters. The `raw-v6-match-schema` migration also normalizes already present matches without rewriting participants or events. The HTTP contract remains unchanged: `Match` continues to be the canonical response model.

## Amendment 2026-08-27

`summoner.ranks` is a BSON object keyed by canonical `GameQueueType.name()`, for example
`ranks.RANKED_SOLO_5X5`. The embedded `Rank` value has no `queue` field. Runtime
models use `Map<GameQueueType, Rank>` and every write produces the object form.
The Mongo reader centrally accepts legacy `ranks[]` only until the manual migration
has reduced `db.summoner.countDocuments({ranks: {$type: "array"}})` to zero.
Leaderboard queries read the selected dynamic queue path directly; they do not
unwind ranks. Redis keys retain their existing names, so rank/profile/search/
leaderboard payloads must be invalidated at deployment before serving the new
object contract.

## Amendment 2026-08-29

`Rank` remains the canonical Riot queue payload and stores no MMR. The derived
`competitive` collection owns the leaderboard access path with one row per
`{puuid, canonicalQueue}`: `puuid`, `region`, `queue`, calculated `mmr`,
statistics-derived primary lane and `lastUpdate`. A row exists when the rank
for that queue exists; the primary lane is absent until canonical profile
statistics are available. There is no `filterKey` and no materialized
leaderboard page.

The same projection may contain optional `otpChampionId`, copied from the
single canonical queue-level OTP classification; it is independent from the
primary lane and non-OTP rows omit it. `!test stats otp` recomputes this
classification from existing canonical statistics, then rebuilds
`competitive` and leaderboard aggregates.

Leaderboard reads filter/sort/page `competitive` first (MMR range, optional
region, primary lane and OTP champion ID), then fetch the limited PUUID list from `summoner` by
`_id: {$in: [...]}`. Rank refresh and canonical profile-statistics refresh both
upsert or remove the affected competitive rows. `!test stats otp` rebuilds
the projection for the initial population or repair. Side-specific base
counters (`blueGames`, `blueWins`, `redGames`, `redWins`) are persisted in the
same profile-statistics leaves, so future side/queue/lane aggregates do not
need match scans.

## Amendment 2026-08-30: profile records

`profile_records` is a rebuildable profile projection with one document per
`{ puuid, filterKey, metric }`. It never replaces canonical `match`,
`match_events`, participant snapshots or profile statistics. The row stores the
winning match reference, value and derived score, historical MMR when the
participant snapshot is available, and the event actor/team only where the
metric requires it. A `gameShared` field is present only for TEAM and MATCH
metrics: TEAM records are written for the five participants of the team and
MATCH records for all ten participants.

The global Records overview reads the indexed top five rows for every metric; the
nested metric ladder reads `profile_records` directly with a filterKey, metric
and score sort. Positions are not persisted. Timeline metrics are rebuilt from
`match_events` in bounded batches; matches without events simply do not
produce timeline records. The operator owns the unique identity and
global/regional indexes documented in `docs/mongo/12-profile-record-indexes.md`.

## Amendment 2026-07-26

The leaderboard keeps `summoner.ranks` as the sole canonical source of ranks and does not save duplicate rows or materialized pages. Mongo may however keep the derived collection `leaderboard_aggregates` for rank distribution, top-region and leaderboard count: each document contains only the aggregated result and the key filter, and is always rebuildable from summoners. Materialized snapshots are rebuilt every 12 hours; new filters are built lazily on first read. The page uses a limited and ordered `find()` on the queue's MMR path; the total follows Redis, Mongo aggregates and only finally `countDocuments()`. Redis is invalidated together with the rebuild. The HTTP contract of `LeaderboardPage`, `SummonerLeaderboard`, distribution, top-region and `202` status remains unchanged.

Secondary indexes are managed operationally outside the runtime and the
migration. `MongoDB` creates only missing collections and does not create, verify,
modify or remove indexes.

## Context

Historical LoL persistence is concentrated in `LeagueDB`, a static class that contains SQL queries and mapping for summoner, rank, mastery, match and participant. The runtime must be separated from the MariaDB backfill.

The repository already contains canonical LoL models, Redis as cache and services that use the `Redis -> database -> Riot` flow. The migration must introduce Mongo without creating a second HTTP contract or losing data during the transition.

## Decision

The first migration covers only `league_of_legends`. Other MariaDB domains will be handled in separate ADRs after the LoL cutover.

The operational strategy is:

1. `MongoMigration` reads MariaDB with checkpoint and high-water mark;
2. the LoL runtime reads and writes exclusively to MongoDB;
3. Redis remains cache only;
4. Riot API remains external source for fallback and refresh;
5. `LeagueDB` remains a SQL adapter used exclusively by `MongoMigration`.

There are no MariaDB queries, mirror, SQL fallback, outbox or dual-write proxy in the LoL runtime.

Mongo will use:

- `puuid` as `_id` for `summoner`;
- full Riot match ID as `_id` for `match`;
- rank and mastery embedded in summoner;
- champion statistics and build in separate aggregate collections;
- participants embedded in match;
- separate collections only for derived data that requires an autonomous access pattern; the leaderboard uses `summoner.ranks` directly for rows and keeps only aggregated snapshots in `leaderboard_aggregates`;
- no numeric MariaDB identifier is written into Mongo documents; canonical keys are PUUID, full Riot match ID, queue and championId.
- match events are separated into `match_events` and compressed by WiredTiger with Zstandard; match and masteries remain normal BSON.

## Boundary

`SummonerService`, `RankService`, `MasteryService`, `MatchService` and
`ProfileService` are the LoL cache-aware runtime boundaries (ADR-0011 / ADR-0012).
Runtime Mongo persistence is exposed directly via `MongoDB`; `LeagueDB` is confined to the migration read path.

Spring continues to own only controllers, HTTP configuration and error models. `QueryRecord` is the common projection container; complex objects use the already existing LoL models.

## Serialization rules

- R4J enums are saved as strings produced by `name()`;
- bans use `BLUE` and `RED`, never numeric ordinals;
- participants have no nested mega `build` object;
- JSON events are serialized into `match_events` with `uncompressedBytes`, `checksum` and `encoding`; compression is native WiredTiger with server level 9;
- the reader loads events separately and history uses a batch query, without N+1;
- `null` and `[]` keep distinct semantics.

## Write path

Every LoL runtime mutation goes directly through `MongoDB` with an idempotent operation. Rank updates do not invalidate snapshots; the periodic task rebuilds Mongo aggregates and increments the Redis version every 12 hours. SQL queries are allowed only in the `MongoMigration` read path; no runtime consumer may keep a LoL `INSERT`, `UPDATE` or `DELETE`.

## Configuration

Mongo configuration is read from `rsc/settings.json` as a connection URI string. The application database is chosen by code:

```json
"mongo": "mongodb://<user>:<password>@safjnest.com:27017/"
```

`App.isTesting() == false` uses `beebot`; `App.isTesting() == true` uses `beebot_test`. Collections use the same names as MariaDB tables, without `lol_` prefix, in both databases.

The code owns bootstrap of collections and declared secondary indexes. Bootstrap is idempotent and does not perform automatic drops; compatible existing indexes are reused, while conflicting ones require an explicit operational migration. The new flow does not require general automatic cleanups; the match schema migration normalizes only the residues identified by this ADR. The operator manually removes other obsolete or duplicate payloads before regeneration.

## API compatibility

This migration does not implicitly change the HTTP contract. Canonical models remain those in `lol.model`.

The `leaderboard_aggregates` snapshot amendment is internal: it does not require changes to controllers, canonical models or API reference.

Numeric fields of public models remain compatible with the historical model, but are not persisted in Mongo documents and are not lookup keys.

## Consequences

### Positive

- profile and match detail eliminate hot joins;
- participant and profile data can be read with natural access patterns;
- local projections can use `QueryRecord` and `List<QueryRecord>`, even nested;
- complex objects reuse canonical models, without duplicate Mongo DTOs;
- MariaDB remains available for backfill and for other domains;
- the same Mongo infrastructure can be reused by other domains.

### Negative

- the runtime cannot use MariaDB as fallback if Mongo is unavailable;
- the leaderboard requires a derived projection and MMR indexes per queue/scope; the page first reads `competitive`, then the summoners of only that page, while the total uses Redis and Mongo aggregates before the `countDocuments()` fallback; distribution, top-region and count are persisted in `leaderboard_aggregates`, rebuilt every 12 hours and cached in Redis;
- the backfill requires checkpoint, high-water mark and corrupted payload handling;
- the backfill and the runtime must be verified separately.

## Gate

This ADR is approved for Java implementation. Any conflict with existing LoL ADRs must be reported and not implicitly resolved by the macro-task. The old `summoner.metrics` and legacy custom builds remain out of the current target.
