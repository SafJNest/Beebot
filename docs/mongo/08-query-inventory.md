# LoL query inventory

The runtime counterpart lives in `MongoDB.java`; hot paths use typed projections or detached `QueryRecord`.

| Area | Target Mongo query | Application budget | Consumer |
|---|---|---:|---|
| search/autocomplete | one `find` on `summoner` with `region + riotSearch` prefix, base projection + `ranks`, Solo rank included; global HTTP search uses `$in` over active shard names in the same bounded query | 1 | SummonerService |
| linked accounts by userId | `find({userId})` sorted by `_id`; maps to canonical `Summoner` (`region` as `LeagueShard`) | 1 | UserData / Discord |
| profile | one `find` on `summoner` with `Summoner + ranks + masteries` projection; Redis statistics first, then Mongo | 2 | ProfileService |
| leaderboard | Normal page: `find` on `competitive` with queue/MMR-range/region/role/OTP filter, `mmr DESC, score DESC` and limited PUUIDs; one `$in` on `summoner._id`. Summoner page: exact identity lookup, Redis exact-division rank/range when resident; otherwise indexed count-before plus bounded seek queries around the target tuple, then one `$in` on `summoner._id`. Both use the existing total-count path. | Normal: 2 per page + count on cache miss. Summoner: identity + anchor + rank/page reads + count + summoner fetch. | LeaderboardService |
| contextual leaderboard ranking | Mongo cursor over `competitive {queue,mmr range,region}` into a temporary Redis ZSET, then atomic publish | streaming batch | LeaderboardService |
| contextual record ranking | Mongo cursor over `profile_records {filterKey,metric,region}` into a temporary Redis ZSET, then atomic publish | streaming batch | ProfileRecordService |
| profile statistics batch | `{puuid: {$in: [...]}, filterKey}`, flat root projection, unique identity index | 1 | ProfileService |
| profile matchup rebuild | cursor over bounded match batches plus one `$in` join on `match_events`; process and recursively clear each decoded timeline | 1 batch of 100 | ProfileService |
| champion stats matrix rebuild | exact count plus cursor over match IDs, then bounded match and `match_events` reads; logs planned/completed batches; delivers missing-event matches and leaves eligibility to stats/build consumers | 1 batch of 10,000; 1 match query + 1 `match_events` query per batch | ChampionAnalyzer |
| standalone champion build rebuild | same shared raw ID/match/event join as statistics; standard consumer requires a usable timeline, Arena retains missing events; recursively clear temporary inputs | 1 event batch of 100 matches | ChampionBuildEngine |
| history | participant filter in a single `$elemMatch`, limited projection/paging; direct `countDocuments` | 1 + batch events | LeagueMessage |
| match results | projection of only the fields needed for `MatchResult` and participants | 1 | profile/tracker |
| tracker repair | for each `tracking=true` summoner, scan stored Solo/Duo participant history by `timeStart DESC, _id DESC`, rewrite derived `rankProgress` links and set `tracked=true` | streaming per tracked summoner | `%test fix-tracked` |
| timeline repair | cursor stored matches for one PUUID newest-first, fetch timeline directly by Riot match ID, regenerate the complete compact event payload, replace only that match's `match_events` record; stop after 10 consecutive missing timelines | streaming per PUUID | `!test fix-timeline <puuid>` |
| patch timeline repair | one cursor per region over `match.patchMajor + region`, project only `_id`, fetch timelines in parallel across regions with a 500 ms delay per region, log `current/total`, regenerate compact events and replace only its `match_events` record; continue through missing timelines | streaming per region | `!test fix-timeline-patch <patch>` |
| match events | `_id: {$in: [...]}` on `match_events` | 1 per batch; 100 profile matchups/standalone builds or 10,000 champion matrix matches | match detail/history and aggregate rebuilds |
| champion | cursor of match IDs, then match/event documents joined in bounded batches; process and recursively clear each timeline; raw batch without full `Match -> Participant` | 2 per batch (+ count/trend) | Champion services |
| leaderboard aggregates | Mongo snapshot `leaderboard_aggregates` per filter; rebuild every 12 hours and `$match` + `$group` on `summoner.ranks.<QUEUE>` path for new filters | 1 | LeaderboardService |
| writes | atomic updates, participant pipeline, unordered bulk for builds/statistics/summoners; unique `{puuid, filterKey}` | 1 per update/batch | MongoDB/tracker |

## Projections and filters

Search returns directly the payload needed for search and autocomplete: `Summoner` and `RANKED_SOLO_5X5` rank are read in the same projection. The `findRank` loop per PUUID no longer exists.

Profile and leaderboard use structured BSON fields. Champion and lane filters are applied to the same `participants` element via a single `$elemMatch`; they can no longer match champion and lane on two different participants.

Tracker predecessor lookup filters the participant PUUID, shard and canonical
Solo/Duo queues, excludes the current full match ID, and reads the latest
earlier `timeStart` with `{timeStart: -1, _id: -1}` ordering.

Paginated queries are limited to 100 matches, 50 leaderboard summoners, 25 search results, 500,000 summoner keys per page and 50,000 match keys per page. Full summoner data is read and written in sub-batches of 20,000; matches and events remain in sub-batches of 1,000. Long-batch cursors must be closed explicitly.

## Invariants

PUUID is the summoner identity and the document `_id`; the full Riot match ID is the match identity; R4J enums use `name()`; bans use BLUE and RED; participant stays flat; upsert/update/delete are idempotent; application reads and writes are Mongo-only; Mongo read errors are explicit.

MariaDB stores UTF-8 JSON in `champion_builds.data`, `champion_stats.data`, and `profile_statistics.data`. Mongo stores `build` as structured BSON; its `filterKey` is `champion + lane + queue + rank + rankBehavior + patch + region`, plus opponent/duo when requested. `profile_statistics` stores timestamps directly and only the leaf nodes `champions.<championId>.<canonicalQueue>.<position>`, plus `pings`, `spellOne`, and `spellTwo`, never under a `statistics` field. `isOtp`, totals and queue/lane/champion aggregates are runtime-only. `champion_stats` stores exactly one raw `ChampionStatsDocument` per `queue + rankBehavior + rank + patch + region` scope under `_id = scope.toKey()`: root scope/games/banGames/previousPatch/ready/updatedAt plus `champions.<championId>.bans` and lane leaves. It never stores `statistics`, `overview`, `filter`, `laneStats`, or any calculated rate. Matchups live only in raw leaves keyed directly by opponent champion ID. No Kryo payloads, compatibility reads or `legacyPayload` are used.

`profile_matchups` is a separate collection: its `matchups` payload stores `champions.<championId>.<canonicalQueue>.<position>` leaves, with same-position `matchups.<opponentChampionId>` and applicable `synergies.<allyChampionId>`. For lane queues, root-leaf timeline metric sums/counts aggregate available differences against same-lane opponents; matchup leaves keep the same values scoped by opponent. Synergy leaves keep pair-scoped timeline metrics. Means, adjusted win rate and tier are response-only. The persisted maps retain all champion IDs; `others` is formed only in the API projection by combining buckets below `minGames`.

Details on the `filterKey` format, the reason for the compound index, and profile-match ownership are in [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

## Index policy

Indexes are managed by the database operator, not by the runtime or the migration. They must follow the actual queries:

| Collection | Index | Covered query |
|---|---|---|
| `summoner` | `summoner_search_prefix` | search/autocomplete by region and prefix, with `riotId` sort |
| `summoner` | `summoner_riot_id` (`{riotSearch: 1, riotId: 1, region: 1}`) | normalized `riotSearch` candidate lookup, then anchored case-insensitive `riotId` verification for `findPuuid` and summoner leaderboard mode |
| `summoner` | `summoner_user_accounts` | Discord accounts by `userId`, sorted by `_id` |
| `summoner` | `summoner_tracking_true` | tracker and accounts with `tracking=true` |
| `competitive` | `{queue: 1, mmr: -1, score: -1}` and `{queue: 1, region: 1, mmr: -1, score: -1}` | leaderboard page order and scoped query |
| `match` | `match_participant_time` | history, profile, OPGG, recent matches and LP data |
| `match` | `match_shard_time`, `match_shard_patch_time`, `match_patch` | temporal queries, region/patchMajor, bans and champion wins |
| `match` | `match_champion_filter` | champion batch with equality-first filter and participant/lane |
| `profile_statistics` | `profile_statistics_identity` | lookup/upsert/delete/batch for `{puuid, filterKey}`, `unique` |
| `profile_statistics` | `profile_statistics_period` | projection over `timeStart`/`timeEnd` ranges |
| `profile_activity` | `profile_activity_identity` | lookup/upsert for `{puuid, filterKey}`, `unique` |
| `profile_matchups` | `profile_matchups_identity` | lookup/upsert for `{puuid, filterKey}`, `unique` |
| `champion_builds` | `champion_builds_filter` | aggregate builds per `filterKey` |
| `champion_stats` | `_id` | one raw scope document, direct lookup by `scope.toKey()` |

`summoner_riot_id` must index `{riotSearch: 1, riotId: 1, region: 1}` so exact
Riot ID lookup can narrow on the normalized indexed value before verifying the
stored case-insensitive exact string. Check the existing key before creating or
replacing the operator-managed index:

```javascript
db.summoner.createIndex(
  {riotSearch: 1, riotId: 1, region: 1},
  {name: "summoner_riot_id"}
)
```

`match_events`, `leaderboard_aggregates`, `migration_runs`, `champion` and
direct match/summoner lookups remain covered by `_id`. No indexes are created
on `masteries`, participant metrics, or every possible `Filter` combination;
`opponent` and `duo` remain relational filters applied in Java.

Uniqueness of `{puuid, filterKey}` requires an operational check for missing and duplicate identities before applying the corresponding unique index; cleanup remains manual.

## Required explains

Before acceptance, run on a database with representative data:

```javascript
db.competitive.aggregate([
  {$match: {queue: "RANKED_SOLO_5X5", region: "EUW1", mmr: {$gte: 1200, $lt: 1600}, $or: [{mmr: {$gt: 1432}}, {mmr: 1432, score: {$gt: 18}}]}},
  {$count: "count"}
]).explain("executionStats")
db.competitive.find({queue: "RANKED_SOLO_5X5", region: "EUW1", mmr: {$gte: 1200, $lt: 1600}, $or: [{mmr: {$gt: 1432}}, {mmr: 1432, score: {$gt: 18}}]}).sort({mmr: 1, score: 1}).limit(24).explain("executionStats")
db.competitive.find({queue: "RANKED_SOLO_5X5", region: "EUW1", mmr: {$gte: 1200, $lt: 1600}, $or: [{mmr: {$lt: 1432}}, {mmr: 1432, score: {$lt: 18}}]}).sort({mmr: -1, score: -1}).limit(25).explain("executionStats")
db.summoner.find({riotSearch: "playernameeuw", riotId: /^Player Name#EUW$/i, region: "EUW1"}, {_id: 1, riotId: 1, region: 1, ranks: 1, masteries: 1}).limit(2).explain("executionStats")
db.summoner.find({region: "EUW1", riotSearch: /^name/}, {riotId: 1, ranks: 1}).sort({riotId: 1}).limit(25).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid", champion: 1}}, region: "EUW1", queue: "RANKED_SOLO_5X5", patchMajor: "14.2"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid"}}, region: "EUW1", queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}, _id: {$ne: "EUW1_123"}, timeStart: {$lt: 1714514400000}}).sort({timeStart: -1, _id: -1}).limit(1).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid"}}, queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}}).sort({timeStart: -1, _id: -1}).limit(1).explain("executionStats")
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
).sort({mmr: -1, score: -1}).skip(50).limit(50).explain("executionStats")
db.competitive.find(
  {queue: "RANKED_SOLO_5X5", region: "EUW1", primary: "UTILITY", mmr: {$gte: 30000}},
  {_id: 0, puuid: 1}
).sort({mmr: -1, score: -1}).limit(50).explain("executionStats")
db.summoner.find(
  {_id: {$in: ["<page-puuid-1>", "<page-puuid-2>"]}},
  {_id: 1, riotId: 1, region: 1, level: 1, icon: 1, ranks: 1, masteries: 1}
).explain("executionStats")
```

Explains must verify `executionTimeMillis`, `totalKeysExamined`,
`totalDocsExamined`, `nReturned`, `winningPlan`, `indexName` and the absence of
`COLLSCAN` and of a blocking `SORT`. Compare the before/after baseline with `collStats` and
`indexSizes`. The leaderboard uses `competitive` for MMR/range/role and then a
`$in` on the `summoner` primary key; contextual segment ownership and rebuilds
are described in [`13-contextual-ranking.md`](13-contextual-ranking.md) and
[ADR-0015](../architecture/adr/0015-contextual-ranking.md).

Summoner leaderboard mode keeps only queue and optional region from the
request. It resolves an exact PUUID or exact stored `Riot ID#tag`, obtains the
queue rank and matching `competitive` anchor, and bypasses the page cache.
For a resident Redis rank segment, `ZREVRANK` gives the target ordinal and
`ZREVRANGE` reads the computed page. If a segment needed for that page is cold,
the fallback counts rows ahead by `(mmr > target.mmr) OR (mmr = target.mmr AND
score > target.score)`, then seeks to the closest preceding and following rows
with the compound sort index. It does not use `skip`. No stable field is added
for exact MMR-and-score ties.

The plans above are operator verification steps, not results claimed by this
change. Confirm `IXSCAN`, no blocking `SORT`, and latency on representative
Gold/Emerald data before deciding whether lower-division Redis prewarming is
needed.

## Arena build path

Reuse champion_builds with `_id=filterKey`, one standard+Arena document and
champion/patch/CHERRY scope with canonical rank/region filters; role is invalid. No discriminator or extra collection.

| Operation | Implemented contract | Owner |
|---|---|---|
| Arena source | shared raw query queue+patchMajor+champion/rank/region, exact patch for full-patch CHERRY scopes; union ID/boots/placement/subTeam/skillOrder projection for the standard build consumer; 100 standalone or 10,000 matrix | MongoDB/ChampionBuildProvider |
| Arena timeline join | one `match_events` query by `_id: {$in: batch IDs}`; missing event documents do not drop matches | MongoDB |
| Arena parsing | attributed `item_events`; timestamp orders purchases, final slots provide first-Prismatic fallback; full-build item lists include consumables and exclude Prismatics, including Anvil 220007; timestamps are not persisted | ArenaGameParser |
| Standard write | atomic `$set` root metadata and individual standard build.* paths, never build/replace | MongoDB |
| Arena write | `_id=filterKey`, `$set build.arena` + arenaLastUpdate, filterKey on insert; aggregate subtree senza campi di versione | MongoDB |
| Standard read | existing filterKey/buildVersion lookup, detached projection removes arena | MongoDB |
| CHERRY read | Arena data presence + freshness timestamp; legacy separate consumables fields and skillOrders remain readable but omitted from output; flat HTTP Arena projection; stats ready marker without per-champion bucket completeness checks | ChampionService/MongoDB |

Candidate indexes remain match queue/patchMajor and champion_builds_filter;
match_events uses unique _id. No new runtime indexes or live explain was executed.
Indexes/options/coverage must be verified operationally, not inferred from this table.
BSON local payload/update guards plus Mongo's atomic final-document check protect
previous data on failure; combined size/heap measurements on representative data
remain pending. Standard eligibility remains unchanged; the existing champion API now dispatches CHERRY to Arena.

### CHERRY contract update (2026-10-14)

Only CHERRY uses the new [Arena schema](../arena-build/champion-schema.json):
HTTP build is the flat Arena aggregate, with coreBuilds[].builds, six augment
position rankings and global/core Prismatics. Full-build item lists include
consumables and exclude Prismatic items, including consumable Anvil 220007. No other legacy fields or build.arena
wrapper are exposed. Stats use exact same-subTeam ally combinations, team
sizes 2/3, `subteamsById` counts for Arena IDs 1–8, and overview placement
counts/average from `subTeamPlacement`; these additions do not change synergy
aggregation. Lane relations are omitted. The raw projection includes subTeam and subTeamPlacement.
Mongo preserves build.arena ownership. Stats readiness uses the ready marker;
Arena build availability uses Arena data presence. Legacy `skillOrders` values
are ignored and omitted; removing them does not trigger a refresh. Timestamps can enqueue background
refreshes, but readers do not require legacy-field absence or all eight subteam
ID counters and placement buckets. There are no version fields or version-based
cache guards. Other queues, jobs
and Discord presentation remain unchanged. No live rebuild/explain is claimed.
