# ADR-0015: Contextual ranking projections

- Status: Accepted
- Owner: Main agent
- Date: 2026-09-07

## Context

Profiles, leaderboard rows and records need absolute global and regional positions without making Mongo a per-row rank query or persisting a second public projection. `competitive` and `profile_records` are already the rebuildable Mongo access paths for those domains. Players at the same MMR need a deterministic order that rewards a larger positive ranked win/loss balance while keeping page and absolute positions consistent.

## Decision

`RankService` owns the canonical rank-write cascade: it persists `summoner.ranks`, then delegates the derived Mongo projection to `CompetitiveService`. `saveEntry` updates one canonical queue and preserves all other queues; `saveEntries` replaces the complete rank map and must receive a full Riot response. No normal runtime path updates `competitive` from profile statistics or any other source. `CompetitiveService` is the sole writer of `competitive`; only after its Mongo write is acknowledged does it delegate leaderboard-index updates to `LeaderboardService`. `LeaderboardService` is the sole writer of leaderboard Redis indexes, while `ProfileRecordService` owns the equivalent record indexes. Mongo remains the source of truth. Redis sorted sets are disposable derived indexes; they store the canonical numeric MMR score and a binary member made from sortable signed win/loss score followed by the 128-bit SHA-256-derived competitive identity. They never store a `SummonerView`, Riot ID, icon or record payload.

Leaderboard segments are keyed by canonical queue, `GLOBAL` or `LeagueShard`, and the exact division requested by the canonical `Rank`. Mongo selects a segment exclusively through that division's fixed MMR interval; `competitive` persists no tier field. `competitive.score` is `wins - losses` from the canonical ranked entry. A larger positive balance therefore grows with the number of games, while a small sample contributes less. Leaderboard pages sort by `mmr DESC, score DESC`; no additional criterion is applied when both values are equal. Redis ZSET score remains `competitive.mmr`. Its binary member prefixes the signed score in big-endian sortable form and appends the existing 128-bit `competitive._id` hash so identities remain unique; Redis may order exact MMR-and-score ties by this member representation. Score and MMR are not packed into one Redis double. Exact-rank counts continue to use MMR only.

`MASTER_I`, `GRANDMASTER_I` and `CHALLENGER_I` segments are permanent. `LeaderboardService.warmupIndexAsync()` builds them, plus all exact-rank count hashes, at application startup. Lower exact-rank segments are built on demand by an explicitly asynchronous build path and return nullable rankings until ready. A build streams Mongo in batches into a temporary ZSET and atomically publishes it only when complete. In-process build deduplication makes concurrent requests share one build.

Exact-rank counts are small Redis hashes derived by assigning `competitive.mmr` to its fixed division interval for the same queue and scope. They are rebuilt from Mongo after Redis loss and are updated only when already resident. Absolute leaderboard rank is the sum of higher exact-rank counts plus the one-based ZSET ordinal within the exact division.

Record segments are lazy and keyed by the real record context: `filterKey + metric + GLOBAL|region`. `HIGHEST_MASTERY` adds an optional exact champion ID because its persisted identity is one row per canonical mastery entry. Its rows are a rebuildable index over `summoner.masteries`, never a second mastery source. Other record metrics add no champion, queue, lane or kill-type dimension. Their score is the persisted `ProfileRecord.score`; ties use competition ranking (`count(score greater) + 1`).

`Rank.globalRanking` and `Rank.regionRanking` are nullable derived response fields. `ProfileRecord` carries the same nullable fields. Neither is persisted in `summoner.ranks` or `profile_records`. `SummonerLeaderboard.position` continues to mean the position in the requested filtered/page leaderboard.

## Operations

After changing the competitive projection, including adding `score`, run `!test regenerate competitive` before serving contextual rankings. It replaces only the projection rows. Run `!test regenerate aggregates` separately to rebuild leaderboard aggregates. Startup warmup clears registered leaderboard ZSETs so members using an older encoding cannot remain, rebuilds permanent Master+ segments and all count hashes, and lets lazy lower-rank segments rebuild on request. Redis may be flushed at any time; record segments rebuild on request.

`LeaderboardService.indexStatus()` and `ProfileRecordService.indexStatus()` provide resident segment counts, per-key cardinality, `MEMORY USAGE`, TTL and in-progress build state. They contain no player-level logging and do not clean up Redis as a side effect.

## Consequences

- The existing rank-distribution API remains tier-grouped and unchanged.
- Competitive filters, exact-division counts and segment rebuilds use only MMR intervals; `tier` remains only in canonical Riot ranks and derived response labels.
- Equal-MMR leaderboard rows use the ranked `wins - losses` score consistently in Mongo page order and Redis absolute positions. Cached leaderboard pages keep their existing TTL and may remain stale until expiration.
- The contextual index supports a later `rank + range` API without changing its key shape.
