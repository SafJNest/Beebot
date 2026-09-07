# ADR-0015: Contextual ranking projections

- Status: Accepted
- Owner: Main agent
- Date: 2026-09-07

## Context

Profiles, leaderboard rows and records need absolute global and regional positions without making Mongo a per-row rank query or persisting a second public projection. `competitive` and `profile_records` are already the rebuildable Mongo access paths for those domains.

## Decision

`RankingService` is the single owner of contextual ranking. Mongo remains the source of truth. Redis sorted sets are disposable derived indexes; they store only a 128-bit SHA-256-derived binary member and the canonical numeric score. They never store a `SummonerView`, Riot ID, icon or record payload.

Leaderboard segments are keyed by canonical queue, `GLOBAL` or `LeagueShard`, and exact `TierDivisionType`. Their score is `competitive.mmr`; the member is the existing 128-bit `competitive._id` hash. Mongo and Redis order ties by the same binary member in descending order. Score and member are deliberately not packed into one Redis double.

`MASTER_I`, `GRANDMASTER_I` and `CHALLENGER_I` segments are permanent. Lower exact-rank segments are built on demand, refreshed to a six-hour idle TTL on access, and are never created by a rank write alone. A build streams Mongo in batches into a temporary ZSET and atomically publishes it only when complete. In-process build deduplication makes concurrent requests share one build.

Exact-rank counts are small Redis hashes derived from `competitive.tier` for the same queue and scope. They are rebuilt from Mongo after Redis loss and are updated only when already resident. Absolute leaderboard rank is the sum of higher exact-rank counts plus the one-based ZSET ordinal within the exact division.

Record segments are lazy and keyed by the real record context only: `filterKey + metric + GLOBAL|region`. Their score is the persisted `ProfileRecord.score`; ties use competition ranking (`count(score greater) + 1`). No champion, queue, lane or kill-type dimension is invented because it is not present in the current `profile_records` identity or endpoints.

`Rank.globalRanking` and `Rank.regionRanking` are nullable derived response fields. `ProfileRecord` carries the same nullable fields. Neither is persisted in `summoner.ranks` or `profile_records`. `SummonerLeaderboard.position` continues to mean the position in the requested filtered/page leaderboard.

## Operations

After deploying the new `competitive.tier` field, run the existing competitive rebuild (`!test stats otp` or the corresponding owner operation) before serving contextual rankings. This refreshes the projection, exact counts and permanent segments. Redis may be flushed at any time: permanent leaderboard segments are rebuilt by the rebuild operation or their next read, while lazy leaderboard and record segments rebuild only on request.

`RankingService.snapshot()` provides resident leaderboard/record segment counts, per-key cardinality, `MEMORY USAGE`, permanent/lazy state, TTL and in-progress build state. It contains no player-level logging.

## Consequences

- The existing rank-distribution API remains tier-grouped and unchanged.
- The existing `competitive` page sort adds `_id DESC` as the explicit deterministic tie-break required to match Redis binary-member ordering.
- The contextual index supports a later `rank + range` API without changing its key shape.
