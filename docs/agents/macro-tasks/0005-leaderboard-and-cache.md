# Macro-task 0005: leaderboard and cache

## Objective

Simplify leaderboard assembly and reuse the complete canonical summoner view without sequential Riot fetches.

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
- use deterministic cache keys and TTL without wildcard deletion.

## Out of scope

- unrelated Tracker ingestion refactor;
- new leaderboard UI behavior;
- new lightweight leaderboard summoner projection.

## Invariants

- omitted region means internal `GLOBAL`;
- queue default remains the existing solo ranked queue;
- page size is 50;
- page response contains `page`, `pageSize`, `total`, `pages` and rows;
- missing profile statistics are queued and never rebuilt synchronously;
- distribution rows remain keyed by queue, rank and region.

## Acceptance criteria

- `LeaderboardService` has one `LeaderboardPage` construction flow;
- leaderboard rows use `SummonerLeaderboard` and `SummonerView`;
- no sequential Riot fetch occurs for each row;
- cache is refreshed through known deterministic keys and TTL;
- pagination works for 0, 1, 50 and 51 results;
- rank distribution and top-regions remain non-paginated.

## Handoff

Report query count behavior, cache keys, page edge cases, removed helpers and verification output.
