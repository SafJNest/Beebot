# Match indexes and filtered counts

The current rollout retains the five secondary v3 indexes measured on
2026-10-19 and adds two player selector/time indexes. It supersedes the v2
catalog with nine player permutations. Priorities are speed, storage, then
index count. The resulting catalog has **seven secondary indexes plus `_id_`**;
this is not a claim that every filter combination has an optimal access path.

## Measured baseline

The supplied benchmark used 2,055,417 matches, 8,321 matches for the selected
EUW1 player, patch 16.20, no player time bounds, offset zero and descending
pages. All 49 probes completed without errors. Results included:

| Query | Total | Explain keys/documents | Normal client times |
|---|---:|---|---|
| Player base count | 8,321 | 8,321 / 0 | 125-151 ms |
| Player champion + lane count | 61 | 8,322 / 61 | 150-155 ms |
| Player lane + patch count | 8 | 4,384 / 4,384 | 173-245 ms |
| Player first page | 21 returned | 21 / 21 | 211-519 ms |

`match_player_search_v3` used 1,846 MiB out of approximately 2,154 MiB of
indexes. The explain command executes separately and ignores the plan cache;
its timing and selected index do not identify the cached plan or duration of
preceding timed queries. The benchmark did not establish season-bounded,
deep-offset, player-rank or opponent/duo performance.

## Query rules

- PUUID and known region remain present in player scopes. Unknown-region
  discovery, global champion/patch reads and ID-only access retain their scope.
- A single participant predicate uses a dotted path. Multiple conditions on
  the requested participant use one `$elemMatch`, with PUUID, champion, lane in
  that order. Independent opponent/duo existence predicates keep the existing
  Java team/position checks.
- BSON predicate order is a maintenance convention; index field order,
  equality prefixes, range bounds and sort determine index efficiency.
- `scopedMatchFilter` and `buildMatchFilter` share `addMatchTimeFilters`. An
  effective end bound now limits `timeStart <= end` as well as `timeEnd <= end`.
  Start bounds and incremental intersections remain inclusive. This preserves
  valid matches under the invariant `timeStart <= timeEnd`, excludes games
  ending after the selected range, and bounds chronological scans at both ends.
- Count and page use the same effective filter. Profile statistics, records
  and rank-history reads using these builders receive the same time bounds.
- Chronological sorts use only `timeStart`; reverse scans support ascending
  reads. No new index contains `_id` or `timeEnd`. `_id_` remains the identity
  index, including ID batches and migration cursors.
- Application queries have no hardcoded hints or runtime index management.
  Diagnostic hints in the operational script compare alternatives only.

## Existing indexes retained

| Name | Ordered keys | Access |
|---|---|---|
| `match_player_search_v3` | `{participants.puuid:1, region:1, timeStart:-1, participants.champion:1, participants.lane:1, queue:1, patchMajor:1, rank:1, timeEnd:1}` | Unfiltered player pages, player/queue counts and fallback for optional filters |
| `match_region_time_v3` | `{region:1, timeStart:-1}` | Regional history and season subject scans |
| `match_patch_region_time_v3` | `{patchMajor:1, region:1, timeStart:-1}` | Patch-wide reads, regional patch counts and latest match |
| `match_queue_patch_scope_v3` | `{queue:1, patchMajor:1, region:1, rank:1, patch:1}` | Global matrix scopes, queue repairs and training sources |
| `match_champion_scope_v3` | `{participants.champion:1, participants.lane:1, patchMajor:1, queue:1, region:1, rank:1, patch:1}` | Global champion sources without a PUUID |

All five occurred in the supplied explain plans. Global champion and scalar
scope indexes serve refresh sources, not the precomputed `champion_builds`
or `profile_statistics` reads. ID projections can still require document
fetches, followed by event-batch reads and Java processing. Those costs are
not an exact-count problem and are not changed here.

## Two additions

```javascript
{
  "participants.puuid": 1,
  region: 1,
  "participants.champion": 1,
  "participants.lane": 1,
  timeStart: -1,
  queue: 1
}
// match_player_champion_lane_time_v4

{
  "participants.puuid": 1,
  region: 1,
  "participants.lane": 1,
  timeStart: -1,
  queue: 1
}
// match_player_lane_time_v4
```

| Optional player selectors | Candidate |
|---|---|
| none or queue | Existing `match_player_search_v3` |
| champion, optionally queue | Champion prefix of `match_player_champion_lane_time_v4`; omitted lane leaves a gap before time |
| lane, optionally queue | `match_player_lane_time_v4`; no omitted champion before lane/time |
| champion + lane, optionally queue | `match_player_champion_lane_time_v4` |

With champion and lane specified, the new prefix targets matching player
entries before time, instead of scanning the player's entire chronology. The
historical 61-match case should approach 61 keys and 61 documents; boundary
seeks and deduplication can change exact examined counts. `$elemMatch` still
requires document verification; these are not promised zero-document counts.
Time-bounded lane-only queries similarly start at PUUID+region+lane and use the
bounded time interval. They do not require a third lane/queue permutation.

Queue follows time because it is optional: placing queue before time would
leave a gap for lane-only/champion+lane pages. Queue omissions or filtering
another queue can therefore examine more entries than the final total.
Patch, rank, timeEnd and relationship conditions may require residual document
checks or a different existing index. There is no 61/61 guarantee for every
patch/time/rank combination or a replacement for exact counting work.

## Apply and compare

The script is [`scripts/match-count-indexes-v4.mongosh.js`](scripts/match-count-indexes-v4.mongosh.js).
Loading it only defines functions. Select the intended database explicitly;
use the deployed `MongoDB` constants rather than inferring environment from
its name. The supplied live benchmark used `beebot_test`.

```javascript
load("docs/mongo/scripts/match-count-indexes-v4.mongosh.js");
createMatchCountIndexesV4();
```

Creation is sequential and idempotent for matching names/key patterns. The
script only adds the two indexes and never hides, replaces or drops another
index. There are **no indexes to delete in this rollout**. No live index
creation was executed as part of the code change.

Run these separately after creation completes. Each benchmark prints one
complete EJSON report, with explicit async raw commands for Compass shell:

```javascript
benchmarkMatchCountsV4();
```

The default checks all eight champion/lane/queue subsets against the same
player/region, comparing planner, existing player index and the applicable
new index. Each count has three client timings and a separate explain with
index bounds, keys, documents and server time. A supplied `patch` repeats the
eight subsets with that patch. It reports total consistency and actual index
sizes. `sameTotal: false` needs investigation, including concurrent ingestion.
The script is read-only apart from the explicit creation function.

For the observed `lane=BOT&season=previous&queue=TEAM_BUILDER_RANKED_SOLO`
request, the API resolved these bounds and returned total 47 on 2026-10-19:

```javascript
benchmarkMatchCountsV4({
  lane: "BOT",
  timeStart: 1736424000000,
  timeEnd: 1767830400000
});
```

Recheck `metadata.filter` when the configured seasons change. This diagnostic
benchmarks counts only; also measure the actual endpoint with `sort=timeStart:asc`
and deeper offsets. It does not measure page transfer, mapping, profile/build
generation, HTTP connection setup or compression. If Compass abbreviates a
report, it can be printed again without repeating the benchmark:

```javascript
print(EJSON.stringify(matchCountBenchmarkReportV4, null, 2));
```

Both additions repeat the PUUID multikey access, so less width does not
guarantee less total storage. Keep an addition when needed for the warm HTTP
target below one second, or when it demonstrates a repeatable improvement of
at least 20% and 50 ms. Compare total space and ingestion overhead before
deciding whether to replace the wide v3 player index with a narrower index;
none is removed speculatively here.

## Compatibility and validation

ADR-0009 ownership is unchanged: operators manage indexes; `MongoDB` owns
predicates. The matches controller and canonical models were reviewed; public
parameters, response fields, filter metadata, exact total, pagination and
presentation are unchanged. There are no new caches, queues, collections or
counter writes. No invalidation or shard fan-out is required.

This update uses static review and diff checks only; no automated tests or
live Mongo benchmarks/index writes were run for it. The earlier 19-test result
predates this change and does not validate it. Live explain and latency after
index creation and deployment remain required to measure the improvement.

Sources: [compound index prefixes](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-compound/),
[multikey bounds](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-multikey/multikey-index-bounds/),
[covered multikey queries](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-multikey/),
[explain and plan cache](https://www.mongodb.com/docs/manual/reference/method/cursor.explain/).
