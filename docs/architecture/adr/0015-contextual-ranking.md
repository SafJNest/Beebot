# ADR-0015: Contextual ranking projections

- Status: Accepted
- Owner: Main agent
- Date: 2026-09-07

## Context

Profiles and leaderboard rows need absolute global and regional positions without making Mongo a per-row rank query or persisting a second public projection. `competitive` is the rebuildable Mongo access path for ranking; records use direct ordered Mongo reads. Players at the same MMR need a deterministic order that rewards a larger positive ranked win/loss balance while keeping page and absolute positions consistent.

## Decision

`RankService` owns the canonical rank-write cascade: it persists `summoner.ranks`, then delegates the derived Mongo projection to `CompetitiveService`. `saveEntry` updates one canonical queue and preserves all other queues; `saveEntries` replaces the complete rank map and must receive a full Riot response. No normal runtime path updates `competitive` from profile statistics or any other source. `CompetitiveService` is the sole writer of `competitive`; only after its Mongo write is acknowledged does it delegate leaderboard-index updates to `LeaderboardService`. `LeaderboardService` is the sole writer of leaderboard Redis indexes. Records have no Redis ranking indexes. Mongo remains the source of truth. Redis sorted sets are disposable derived indexes; they store the canonical numeric MMR score and a binary member made from sortable signed win/loss score followed by the 128-bit SHA-256-derived competitive identity. They never store a `SummonerView`, Riot ID or icon.

Leaderboard segments are keyed by canonical queue, `GLOBAL` or `LeagueShard`, and the exact division requested by the canonical `Rank`. Mongo selects a segment exclusively through that division's fixed MMR interval; `competitive` persists no tier field. `competitive.score` is `wins - losses` from the canonical ranked entry. A larger positive balance therefore grows with the number of games, while a small sample contributes less. Leaderboard pages sort by `mmr DESC, score DESC`; no additional criterion is applied when both values are equal. Redis ZSET score remains `competitive.mmr`. Its binary member prefixes the signed score in big-endian sortable form and appends the existing 128-bit `competitive._id` hash so identities remain unique; Redis may order exact MMR-and-score ties by this member representation. Score and MMR are not packed into one Redis double. Exact-rank counts continue to use MMR only.

`MASTER_I`, `GRANDMASTER_I` and `CHALLENGER_I` segments are permanent. Startup preserves resident Redis keys and queues only missing segments and count hashes. Lower leaderboard divisions remain lazy. Builds use projected Mongo cursors of 5,000 rows, without sorting, and Redis writes of 1,000 members before atomic publication. Empty results occupy the same key as an empty string; rank reads treat them as empty and incremental writes convert them to ZSETs. The existing job registry deduplicates active builds; no service-local build maps are needed.

Exact-rank counts use one Mongo aggregation per canonical queue, grouping by region and MMR. Java assigns grouped MMR values to canonical divisions and sums global counts, then writes all scope hashes. Existing hashes are reused and updated incrementally. Absolute leaderboard rank is the sum of higher exact-rank counts plus the one-based ZSET ordinal within the exact division.

Records are returned directly from Mongo sorted by their actual `value`, descending for highest metrics and ascending for earliest-time metrics, with `occurredAt ASC, puuid ASC` ties. `ProfileRecord` has no normalized score or positional fields. Record Redis ZSETs, build jobs and ranking calculations are removed. The only record Redis cache stores paginated totals per filter/metric/region/champion for 60 seconds, invalidated when row counts change. `HIGHEST_MASTERY` remains a rebuildable projection of canonical `summoner.masteries`.

`Rank.globalRanking` and `Rank.regionRanking` are nullable derived response fields, not persisted in `summoner.ranks`. `SummonerLeaderboard.position` continues to mean the position in the requested filtered/page leaderboard.

`GET /api/lol/leaderboard?summoner=<PUUID|Riot ID#tag>` is a page locator over the existing leaderboard, not a new ranking source. It derives the tier from the summoner's rank for the selected queue and keeps the optional region. Exact stored Riot ID lookup does not use autocomplete or Riot API search. A resident Redis exact-division index supplies the target ordinal and page members; a cold index falls back to an indexed count-before query and bounded Mongo seek around the target `competitive` row, without deep `skip`. The summoner path bypasses the cached page because that cache may be stale. The response remains `LeaderboardPage`; rank, role, OTP and incoming page are ignored, while queue, region and limit remain effective. Equal MMR-and-score Mongo rows retain no third ordering criterion and can shift between pages.

## Operations

After changing the competitive projection, including adding `score`, run `!test regenerate competitive` before serving contextual rankings. Run `!test regenerate aggregates` separately for leaderboard aggregates. A member-encoding change requires explicit removal of affected Redis indexes; ordinary startup never clears them. A resident key skips both job submission and its Mongo scan. Redis loss rebuilds missing indexes on startup or demand.

`LeaderboardService.indexStatus()` provides resident segment counts, per-key cardinality, `MEMORY USAGE`, TTL and in-progress build state. It contains no player-level logging and does not clean up Redis as a side effect.

## Consequences

- The existing rank-distribution API remains tier-grouped and unchanged.
- Leaderboard API payloads and presentation are unchanged. Record API rows intentionally remove `score`, `globalRanking` and `regionRanking`; their filters and pagination remain unchanged.
- Competitive filters, exact-division counts and segment rebuilds use only MMR intervals; `tier` remains only in canonical Riot ranks and derived response labels.
- Equal-MMR leaderboard rows use the ranked `wins - losses` score consistently in Mongo page order and Redis absolute positions. Cached leaderboard pages keep their existing TTL and may remain stale until expiration.
- Contextual positions are refreshed even on page-cache hits, so lazy divisions such as Iron can build missing indexes and expose positions once ready without rebuilding the page payload.
- The contextual index supports a later `rank + range` API without changing its key shape.
