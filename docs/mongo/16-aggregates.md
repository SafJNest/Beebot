# Shared aggregate snapshots

`AggregateService` coordinates indicative counts and distributions. Mongo
`aggregates` is their durable snapshot store; Redis is its disposable cache.
Player positions and ordered player indexes are not stored here. Match facets,
profile/champion statistics and builds retain their existing collections.
Exact count-before queries used by the leaderboard page locator remain live.

## Identity and payload

Every snapshot has exactly one Redis key and one Mongo identity:

```javascript
{
  _id: "beebot:lol:los:records:count:<filterKey>:HIGHEST_MASTERY:GLOBAL:27",
  type: "RECORD_COUNT",
  arguments: ["<filterKey>", "HIGHEST_MASTERY", "GLOBAL", "27"],
  value: NumberLong(12345),
  updatedAt: NumberLong(1792634400000)
}
```

`type` is the `RedisKey` enum name. `arguments` contains its canonical ordered
format arguments as strings; `_id` is exactly `RedisKey.of(arguments)` including
the production/test prefix. Objects and maps are structured BSON, never JSON
strings. Distribution values keep the normal `{entries: [...]}` shape. Division
counts store the complete map of canonical divisions, including zeroes, and use
the existing Redis hash representation. No extra scalar division snapshots exist.

| Type | Arguments |
|---|---|
| `LEADERBOARD_COUNT` | queue, region/GLOBAL, tier/ALL, role/ALL, OTP champion/ALL |
| `LEADERBOARD_RANK_DISTRIBUTION` | queue, region/GLOBAL |
| `LEADERBOARD_TOP_REGIONS` | queue, tier |
| `CONTEXTUAL_LEADERBOARD_COUNTS` | queue, region/GLOBAL |
| `RECORD_COUNT` | filterKey, metric, region/GLOBAL, champion/`all` |
| `STATUS_GAMES_ANALYZED`, `STATUS_TOTAL_SUMMONERS`, `STATUS_TOTAL_MASTERIES`, `STATUS_TRACKED_SUMMONERS`, `STATUS_RANKS_BY_QUEUE` | empty array |

Queue aliases are normalized before creating the key. All record filter
dimensions remain encoded by the canonical `Filter.toSummonerKey()`.

## Reads, writes and freshness

Reads use Redis, then `aggregates`, then the source calculation if both are
missing. A Mongo hit repopulates Redis without scanning the source. There is no
Mongo expiry or periodic recalculation. Existing Redis TTLs remain unchanged:
record totals 60 seconds, leaderboard totals/distributions 12 hours, metrics
10 days, division hashes persistent. Expiry only reloads the durable snapshot.
Startup uses these same reads; there is no collection-wide cache preload.

Calculation completes before the acknowledged Mongo replacement; only then is
Redis published. Valid zeroes and empty results are stored. Source failures are
propagated instead of saved as zeroes; metric sampling retains its previous
in-memory value on failure. Redis failure does not discard the Mongo snapshot.
Local per-key locking orders cache writes and invalidations. Failed cache
publications are bypassed by later reads in the same process until Mongo can
restore the cache; there is no retry job or durable cache backlog. Mongo and
Redis are not a distributed transaction.

Existing competitive updates increment only complete stored division maps and
publish the resulting durable map to Redis. Missing maps are never initialized
from a delta. Existing record row-count changes invalidate both stores. No new
match/summoner/record write hooks increment other snapshots automatically.

## Regeneration

```text
!test regenerate aggregates
!test regenerate aggregates <type>
```

The optional type defaults to `all`:

| Command type | Snapshots rebuilt |
|---|---|
| `leaderboard-counts` | filtered leaderboard page totals |
| `rank-distribution` | players per tier |
| `top-regions` | players per region for a tier |
| `division-counts` | complete division maps, one source aggregation per queue |
| `record-counts` | global/regional totals and champion totals for champion-scoped metrics |
| `metrics` | the five status snapshots above |

The owner command creates a parent job. Its body enqueues one deduplicated
`BACKGROUND` Compute/MONGO child per selected type and returns immediately;
Registry keeps the parent in `WAITING_CHILDREN` without blocking its worker.
Child keys are `aggregate-rebuild:<type>`. Progress exposes total/completed,
missing/failed counts and the current snapshot identity through existing jobs.
Failures preserve successful sibling results and mark the parent with errors.

Each job recalculates from source, bypassing Redis and Mongo snapshots, then
replaces completed results individually. It includes stored scopes and seeds
standard leaderboard/metric scopes. The record job groups `profile_records` by
filter, metric, region and champion, derives global and regional scopes, and
writes zero for previously stored scopes with no remaining rows. There is no
whole-collection delete. `regenerate all` submits these children after its
existing source regeneration steps have finished.

## Indexes and rollout

`_id_` covers normal reads and updates. The operator creates the type index:

```javascript
db.aggregates.createIndex({type: 1}, {name: "aggregates_type"});
db.aggregates.explain("executionStats").find({_id: "<full Redis key>"});
db.aggregates.explain("executionStats").find({type: {$in: ["RECORD_COUNT"]}}, {_id: 1, type: 1, arguments: 1});
```

The application creates the collection but does not manage secondary indexes.
Inspect actual plans before claiming live index coverage. Full-source record
and competitive grouping during an explicit rebuild is intentional.

The code never reads `leaderboard_aggregates` and has no compatibility reader.
Renaming that collection alone is insufficient: the operator must adapt the
identities, type, arguments and value shape above, or rebuild the snapshots.
Before serving traffic with the new code, clear only the affected Redis caches:
leaderboard `count:*`, `rank-distribution:*`, `top-regions:*`, `counts:*`, record
`count:*`, and the five exact status keys, with the applicable environment prefix.
Preserve leaderboard page keys, player ZSETs, segment/access registries and all
unrelated caches. Run the owner command to seed/rebuild; other scopes remain lazy.

Public API paths, filters, response models and presentation remain unchanged.
