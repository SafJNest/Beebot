# ADR-0015: Contextual ranking projections

- Status: Accepted
- Owner: Main agent
- Date: 2026-09-07

## Context

Profiles, leaderboard rows and records need absolute global and regional positions without making Mongo a per-row rank query or persisting a second public projection. `competitive` and `profile_records` are already the rebuildable Mongo access paths for those domains.

## Decision

`RankService` owns the canonical rank-write cascade: it persists `summoner.ranks`, then delegates the derived Mongo projection to `CompetitiveService`. `CompetitiveService` is the sole writer of `competitive`; only after its Mongo write is acknowledged does it delegate leaderboard-index updates to `LeaderboardService`. `LeaderboardService` is the sole writer of leaderboard Redis indexes, while `ProfileRecordService` owns the equivalent record indexes. Mongo remains the source of truth. Redis sorted sets are disposable derived indexes; they store only a 128-bit SHA-256-derived binary member and the canonical numeric score. They never store a `SummonerView`, Riot ID, icon or record payload.

Leaderboard segments are keyed by canonical queue, `GLOBAL` or `LeagueShard`, and the exact division requested by the canonical `Rank`. Mongo selects a segment exclusively through that division's fixed MMR interval; `competitive` persists no tier field. Their score is `competitive.mmr`; the member is the existing 128-bit `competitive._id` hash. Score and member are deliberately not packed into one Redis double, and Mongo applies no `_id` tie-break sort.

`MASTER_I`, `GRANDMASTER_I` and `CHALLENGER_I` segments are permanent. `LeaderboardService.warmupIndexAsync()` builds them, plus all exact-rank count hashes, at application startup. Lower exact-rank segments are built on demand by an explicitly asynchronous build path and return nullable rankings until ready. A build streams Mongo in batches into a temporary ZSET and atomically publishes it only when complete. In-process build deduplication makes concurrent requests share one build.

Exact-rank counts are small Redis hashes derived by assigning `competitive.mmr` to its fixed division interval for the same queue and scope. They are rebuilt from Mongo after Redis loss and are updated only when already resident. Absolute leaderboard rank is the sum of higher exact-rank counts plus the one-based ZSET ordinal within the exact division.

Record segments are lazy and keyed by the real record context only: `filterKey + metric + GLOBAL|region`. Their score is the persisted `ProfileRecord.score`; ties use competition ranking (`count(score greater) + 1`). No champion, queue, lane or kill-type dimension is invented because it is not present in the current `profile_records` identity or endpoints.

`Rank.globalRanking` and `Rank.regionRanking` are nullable derived response fields. `ProfileRecord` carries the same nullable fields. Neither is persisted in `summoner.ranks` or `profile_records`. `SummonerLeaderboard.position` continues to mean the position in the requested filtered/page leaderboard.

## Operations

After removing the retired `competitive.tier` field, run `!test regenerate competitive` before serving contextual rankings. The operation replaces the projection rows, then rebuilds leaderboard aggregates and warms the permanent leaderboard index. Redis may be flushed at any time: permanent leaderboard segments are rebuilt by startup warmup or the rebuild operation, while lazy leaderboard and record segments rebuild only on request.

`LeaderboardService.indexStatus()` and `ProfileRecordService.indexStatus()` provide resident segment counts, per-key cardinality, `MEMORY USAGE`, TTL and in-progress build state. They contain no player-level logging and do not clean up Redis as a side effect.

## Consequences

- The existing rank-distribution API remains tier-grouped and unchanged.
- Competitive filters, exact-division counts and segment rebuilds use only MMR intervals; `tier` remains only in canonical Riot ranks and derived response labels.
- The contextual index supports a later `rank + range` API without changing its key shape.
