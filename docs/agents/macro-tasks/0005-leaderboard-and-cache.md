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
- query the derived `competitive` index by queue/region/MMR/primary role/OTP champion, then load the page PUUIDs from `summoner` with one `$in`;
- persist rank-distribution, top-region and leaderboard-count aggregate snapshots in Mongo, keyed by filter;
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
- distributions are grouped from `competitive` and returned through `LeaderboardDistribution`;
- aggregate snapshots and the `competitive` projection are derived and rebuildable; `summoner.ranks` remains the rank source of truth;
- MMR, primary role and optional OTP champion ID live only in `competitive`; persisted ranks keep Riot rank, LP, wins and losses;
- page and total are independent: the page is a bounded `find()`, while the total resolves Redis, then `leaderboard_aggregates`, then `countDocuments()`;
- the page cache includes a global version and an incomplete page is never cached.

## Acceptance criteria

- `LeaderboardService` has one `LeaderboardPage` construction flow;
- no intermediate leaderboard row model, row projection or page collection remains in the runtime flow;
- no sequential Riot fetch occurs for each row;
- the page query has no `$facet`, `$unwind` or `$count` and can stop after its requested limit;
- a Redis-miss count is restored from `leaderboard_aggregates` before `countDocuments()`; the latter refreshes both aggregate and Redis entries;
- distribution and top-region snapshots are rebuilt every 12 hours; new filter scopes remain lazy;
- overview cache hits are batch-loaded through Profile Statistics;
- page and aggregate caches are invalidated through the version key;
- pagination works for 0, 1, 50 and 51 results;
- rank distribution and top-regions remain non-paginated.

## Handoff

Report query count behavior, cache keys, page edge cases, removed helpers and verification output.

## Test command

`%test leaderboard-aggregates` removes and regenerates every known leaderboard
aggregate scope: rank distribution and all-ranks count for Solo/Flex globally
and for every active shard, plus top-regions for every tier. It increments the
leaderboard Redis version once after the regeneration.
