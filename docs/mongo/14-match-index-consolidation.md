# Match indexes and filtered counts

The `match` collection has **seven secondary indexes plus `_id_`**. Priorities
are speed, storage, then index count. This is not a claim that every filter
combination has an optimal access path.

## Query rules

- PUUID and known region remain present in player scopes. Unknown-region
  discovery, global champion/patch reads and ID-only access retain their scope.
- A single participant predicate uses a dotted path. Multiple conditions on
  the requested participant use one `$elemMatch`, with PUUID, champion, lane in
  that order. Independent opponent/duo existence predicates keep the existing
  Java team/position checks.
- `scopedMatchFilter` and `buildMatchFilter` share `addMatchTimeFilters`. An
  effective end bound limits `timeStart <= end` as well as `timeEnd <= end`;
  start bounds remain inclusive. This preserves valid matches under the
  invariant `timeStart <= timeEnd` and bounds chronological scans at both ends.
- Count and page use the same effective filter. Profile statistics, records
  and rank-history reads using these builders receive the same time bounds.
- Chronological sorts use only `timeStart`; reverse scans support ascending
  reads. No index contains `_id` or `timeEnd`. `_id_` remains the identity
  index, including ID batches and migration cursors.
- Application queries have no hardcoded hints or runtime index management.

## Index catalog

| Name | Ordered keys | Access |
|---|---|---|
| `match_player_search_v3` | `{participants.puuid:1, region:1, timeStart:-1, participants.champion:1, participants.lane:1, queue:1, patchMajor:1, rank:1, timeEnd:1}` | Unfiltered player pages, player/queue counts and fallback for optional filters |
| `match_player_champion_lane_time_v4` | `{participants.puuid:1, region:1, participants.champion:1, participants.lane:1, timeStart:-1, queue:1}` | Player champion and champion + lane filters |
| `match_player_lane_time_v4` | `{participants.puuid:1, region:1, participants.lane:1, timeStart:-1, queue:1}` | Player lane-only filters |
| `match_region_time_v3` | `{region:1, timeStart:-1}` | Regional history and season subject scans |
| `match_patch_region_time_v3` | `{patchMajor:1, region:1, timeStart:-1}` | Patch-wide reads, regional patch counts and latest match |
| `match_queue_patch_scope_v3` | `{queue:1, patchMajor:1, region:1, rank:1, patch:1}` | Global matrix scopes, queue repairs and training sources |
| `match_champion_scope_v3` | `{participants.champion:1, participants.lane:1, patchMajor:1, queue:1, region:1, rank:1, patch:1}` | Global champion sources without a PUUID |

Queue follows time in the v4 indexes because it is optional: placing it before
time would leave a gap for lane-only and champion + lane pages. Patch, rank,
`timeEnd` and relationship conditions may require residual document checks.
`$elemMatch` always requires document verification.

## Apply and benchmark

The script is [`scripts/match-count-indexes-v4.mongosh.js`](scripts/match-count-indexes-v4.mongosh.js).
Loading it only defines functions. Select the intended database explicitly.

```javascript
load("docs/mongo/scripts/match-count-indexes-v4.mongosh.js");
createMatchCountIndexesV4();
benchmarkMatchCountsV4();
```

Creation is sequential and idempotent; the script never hides, replaces or
drops another index. The benchmark is read-only and compares planner, existing
player index and the applicable v4 index for every champion/lane/queue subset;
pass `{lane, timeStart, timeEnd, patch}` to narrow it. Reprint the last report
with `print(EJSON.stringify(matchCountBenchmarkReportV4, null, 2))`.

Compare total space and ingestion overhead before replacing the wide v3 player
index with a narrower one.
