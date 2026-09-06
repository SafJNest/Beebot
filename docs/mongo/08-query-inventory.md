# LoL query inventory

The runtime counterpart lives in `MongoDB.java`; hot paths use typed projections or detached `QueryRecord`.

| Area | Target Mongo query | Application budget | Consumer |
|---|---|---:|---|
| search/autocomplete | one `find` on `summoner` with `region + riotSearch` prefix, base projection + `ranks`, Solo rank included | 1 | SummonerService |
| linked accounts by userId | `find({userId})` sorted by `_id`; maps to canonical `Summoner` (`region` as `LeagueShard`) | 1 | UserData / Discord |
| profile | one `find` on `summoner` with `Summoner + ranks + masteries` projection; Redis statistics first, then Mongo | 2 | ProfileService |
| leaderboard | `find` on `competitive` with queue/tier/region/role/OTP filter, MMR sort and limited PUUIDs; one `$in` on `summoner._id` loads the page; separate total Redis → aggregate → `countDocuments` | 2 per page + count only on cache miss | LeaderboardService |
| profile statistics batch | `{puuid: {$in: [...]}, filterKey}`, flat root projection, unique identity index | 1 | ProfileService |
| history | participant filter in a single `$elemMatch`, limited projection/paging; direct `countDocuments` | 1 + batch events | LeagueMessage |
| match results | projection of only the fields needed for `MatchResult` and participants | 1 | profile/tracker |
| match events | `_id: {$in: [...]}` on `match_events` | 1 | match detail/history |
| champion | match id with projection; builds and statistics read only the required participants; raw batch without full `Match -> Participant` | 2 per batch (+ count/trend) | Champion services |
| leaderboard aggregates | Mongo snapshot `leaderboard_aggregates` per filter; rebuild every 12 hours and `$match` + `$group` on `summoner.ranks.<QUEUE>` path for new filters | 1 | LeaderboardService |
| writes | atomic updates, participant pipeline, unordered bulk for builds/statistics/summoners; unique `{puuid, filterKey}` | 1 per update/batch | MongoDB/tracker |

## Projections and filters

Search returns directly the payload needed for search and autocomplete: `Summoner` and `RANKED_SOLO_5X5` rank are read in the same projection. The `findRank` loop per PUUID no longer exists.

Profile and leaderboard use structured BSON fields. Champion and lane filters are applied to the same `participants` element via a single `$elemMatch`; they can no longer match champion and lane on two different participants.

Paginated queries are limited to 100 matches, 50 leaderboard summoners, 25 search results, 500,000 summoner keys per page and 50,000 match keys per page. Full summoner data is read and written in sub-batches of 20,000; matches and events remain in sub-batches of 1,000. Long-batch cursors must be closed explicitly.

## Invariants

PUUID is the summoner identity and the document `_id`; the full Riot match ID is the match identity; R4J enums use `name()`; bans use BLUE and RED; participant stays flat; upsert/update/delete are idempotent; application reads and writes are Mongo-only; Mongo read errors are explicit.

MariaDB stores UTF-8 JSON in `champion_builds.data`, `champion_stats.data`, and `profile_statistics.data`. Mongo stores `build` as structured BSON; `profile_statistics` stores timestamps directly and only the leaf nodes `champions.<championId>.<canonicalQueue>.<position>`, plus `pings`, `spellOne`, and `spellTwo`, never under a `statistics` field. `isOtp`, totals and queue/lane/champion aggregates are runtime-only. `champion_stats` stores exactly one raw `ChampionStatsDocument` per scope under `_id = scope.toKey()`: root scope/games/banGames/previousPatch/ready/updatedAt plus `champions.<championId>.bans` and lane leaves. It never stores `statistics`, `overview`, `filter`, `laneStats`, or any calculated rate. Matchups live only in raw leaves keyed directly by opponent champion ID. No Kryo payloads, compatibility reads or `legacyPayload` are used.

`profile_matchups` is a separate collection: its `matchups` payload stores exclusively `champions.<championId>.<canonicalQueue>.<position>.matchups.<opponentId>`. It does not store aggregate rows per champion or matchup outside the leaf.

Details on the `filterKey` format, the reason for the compound index, and the difference between aggregate and `recentMatches` are in [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

## Index policy

Indexes are managed by the database operator, not by the runtime or the migration. They must follow the actual queries:

| Collection | Index | Covered query |
|---|---|---|
| `summoner` | `summoner_search_prefix` | search/autocomplete by region and prefix, with `riotId` sort |
| `summoner` | `summoner_riot_id` | exact/case-insensitive fallback for `findPuuid` |
| `summoner` | `summoner_user_accounts` | Discord accounts by `userId`, sorted by `_id` |
| `summoner` | `summoner_tracking_true` | tracker and accounts with `tracking=true` |
| `competitive` | queue/region/role/OTP/MMR with PUUID | leaderboard page, `mmr DESC` sort; scope-specific index |
| `match` | `match_participant_time` | history, profile, OPGG, recent matches and LP data |
| `match` | `match_shard_time`, `match_shard_patch_time`, `match_patch` | temporal queries, region/patchMajor, bans and champion wins |
| `match` | `match_champion_filter` | champion batch with equality-first filter and participant/lane |
| `match` | `match_champion_keyset` | `findChampionMatchIds` with keyset paging on `_id` |
| `profile_statistics` | `profile_statistics_identity` | lookup/upsert/delete/batch for `{puuid, filterKey}`, `unique` |
| `profile_statistics` | `profile_statistics_period` | projection over `timeStart`/`timeEnd` ranges |
| `profile_activity` | `profile_activity_identity` | lookup/upsert for `{puuid, filterKey}`, `unique` |
| `profile_matchups` | `profile_matchups_identity` | lookup/upsert for `{puuid, filterKey}`, `unique` |
| `champion_builds` | `champion_builds_filter` | aggregate builds per `filterKey` |
| `champion_stats` | `_id` | one raw scope document, direct lookup by `scope.toKey()` |

`match_events`, `leaderboard_aggregates`, `migration_runs`, `champion` and
direct match/summoner lookups remain covered by `_id`. No indexes are created
on `masteries`, participant metrics, or every possible `Filter` combination;
`opponent` and `duo` remain relational filters applied in Java.

Uniqueness of `{puuid, filterKey}` requires an operational check for missing and duplicate identities before applying the corresponding unique index; cleanup remains manual.

## Required explains

Before acceptance, run on a database with representative data:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}, {riotId: 1, ranks: 1}).sort({riotId: 1}).limit(25).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid", champion: 1}}, region: "EUW1", queue: "RANKED_SOLO_5X5", patchMajor: "14.2"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid"}}, region: "EUW1", queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}, timeStart: {$lt: 1714514400000}}).sort({timeStart: -1, _id: -1}).limit(1).explain("executionStats")
db.match.distinct("participants.puuid", {region: "EUW1", queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}})
db.match.aggregate([
  {$match: {queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}}},
  {$unwind: "$participants"},
  {$group: {_id: {region: "$region", puuid: "$participants.puuid"}}},
  {$sort: {"_id.region": 1, "_id.puuid": 1}}
], {allowDiskUse: true}).explain("executionStats")
db.competitive.find(
  {queue: "RANKED_SOLO_5X5", region: "EUW1", mmr: {$gte: 800, $lt: 1200}},
  {_id: 0, puuid: 1}
).sort({mmr: -1}).skip(50).limit(50).explain("executionStats")
db.competitive.find(
  {queue: "RANKED_SOLO_5X5", region: "EUW1", primary: "UTILITY", mmr: {$gte: 30000}},
  {_id: 0, puuid: 1}
).sort({mmr: -1}).limit(50).explain("executionStats")
db.summoner.find(
  {_id: {$in: ["<page-puuid-1>", "<page-puuid-2>"]}},
  {_id: 1, riotId: 1, region: 1, level: 1, icon: 1, ranks: 1, masteries: 1}
).explain("executionStats")
```

Explains must verify `executionTimeMillis`, `totalKeysExamined`,
`totalDocsExamined`, `nReturned`, `winningPlan`, `indexName` and the absence of
`COLLSCAN` and of a blocking `SORT`. Compare the before/after baseline with `collStats` and
`indexSizes`. The leaderboard uses `competitive` for MMR/range/role and then a
`$in` on the `summoner` primary key; the required indexes are described in
[`11-leaderboard-rank-indexes.md`](11-leaderboard-rank-indexes.md).
