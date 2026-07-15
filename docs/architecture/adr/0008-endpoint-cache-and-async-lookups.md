# ADR-0008: Component caches and asynchronous match lookups

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The LoL endpoints reuse the same profile and ranked data across requests. Search performed one rank lookup per result, profile assembled the complete view on every cache miss, champion build lookup loaded all stored builds, and match detail fetched Riot synchronously when the match was not persisted.

## Decision

- Redis keys do not contain cache schema version tokens; test data is reset manually with `FLUSHALL`.
- Search loads ranks in one Redis batch and one bounded SQL `IN` query for misses.
- Profile misses read the summoner base from the database. A database profile with valid statistics and at least one rank produces and caches the complete `SummonerView`; otherwise the service resolves Redis components before database components and returns the available base as `PARTIAL`.
- If the summoner is absent from the database, the profile endpoint returns `202 profile_pending` and `ProfileBootstrapService` deduplicates the Riot-to-database summoner/rank bootstrap by `shard:puuid`.
- Champion most-used build reads the single persisted winner before allowing command/refresh computation.
- Match detail follows `Redis -> DB -> Tracker lookup queue -> Riot -> existing match analysis queue`.
- The match lookup queue is separate from `pushqueue`; `pushqueue` continues to drain only Profile Statistics and Champion Data.

## Invariants

- Canonical models remain the only success payloads.
- Missing profile statistics are still enqueued through `Tracker`.
- A missing summoner is bootstrapped asynchronously without using the `TrackerScheduler`.
- Profile component lists are cached only after a successful database query; database failures do not cache empty lists.
- No Riot request is made by the match HTTP endpoint when the detail is missing.
- The existing Redis queue stores only matches already fetched from Riot.
- The `profile_statistics` database key format remains unchanged.

## Rejected alternatives

- A rank JOIN for search was rejected because the search result is already limited and a bounded batch query keeps the large rank table out of the main search plan.
- Caching incomplete profile pages was rejected because it would hide refreshed statistics until expiry.
- Reusing the existing match analysis set for unresolved IDs was rejected because it stores fetched `LOLMatch` objects, not lookup requests.

## Acceptance criteria

- Search has no per-result rank query.
- Profile cache hits avoid component assembly.
- Champion API does not compute builds.
- Match misses return `202` and are resolved by the scheduler.
- No Redis key pattern contains `v1`, `v2` or `v3`.
