# Macro-task 0005: leaderboard and cache

## Objective

Simplify leaderboard assembly and reuse the complete canonical summoner view without sequential Riot fetches or persisted leaderboard row projections.

## Dependencies

- Macro-task 0001 approved;
- Macro-task 0003 approved;
- Macro-task 0004 approved;
- ADR-0002, ADR-0004 and ADR-0005 accepted.

## Scope

- rewrite `LeaderboardService` around filtering, pagination, cache and distribution access;
- use one page construction path;
- wrap one canonical `SummonerView` in each `SummonerLeaderboard`;
- remove `toSummoner`, `overview`, `mostPlayed`, `championName`, `ratio`, `rounded`, local Riot ID parsing and `distributionVersion`;
- preserve fixed page size, defaults and distribution endpoints;
- query `summoner.ranks[]` directly with one filtered `$facet` for total and page;
- persist only rank-distribution and top-region aggregate snapshots in Mongo, keyed by filter;
- cache complete pages and aggregate responses with versioned deterministic keys;
- reuse the existing per-summoner Profile Statistics cache for overview data;
- rebuild materialized leaderboard aggregates every 12 hours and then invalidate the global leaderboard cache version.

## Out of scope

- unrelated Tracker ingestion refactor;
- new leaderboard UI behavior;
- changes to the public `LeaderboardPage` JSON contract.

## Invariants

- omitted region means internal `GLOBAL`;
- queue default remains the existing solo ranked queue;
- page size is 50;
- page response contains `page`, `pageSize`, `total`, `pages` and rows;
- missing profile statistics start immediately in the background and are never rebuilt synchronously;
- distributions are grouped from `summoner.ranks[]` and returned through `LeaderboardDistribution`;
- aggregate snapshots are derived and rebuildable; `summoner.ranks[]` remains the only rank source of truth;
- `mmr` remains embedded in each persisted rank and is used only for ordering;
- the page cache includes a global version and an incomplete page is never cached.

## Acceptance criteria

- `LeaderboardService` has one `LeaderboardPage` construction flow;
- no intermediate leaderboard row model, row projection or page collection remains in the runtime flow;
- no sequential Riot fetch occurs for each row;
- one Mongo aggregation returns total and the filtered summoner page;
- distribution and top-region snapshots are rebuilt every 12 hours; new filter scopes remain lazy;
- overview cache hits are batch-loaded through Profile Statistics;
- page and aggregate caches are invalidated through the version key;
- pagination works for 0, 1, 50 and 51 results;
- rank distribution and top-regions remain non-paginated.

## Handoff

Report query count behavior, cache keys, page edge cases, removed helpers and verification output.
