# ADR-0008: Component caches and asynchronous match lookups

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The LoL endpoints reuse the same profile and ranked data across requests. Search performed one rank lookup per result, profile assembled the complete view on every cache miss, champion build lookup loaded all stored builds, and match detail fetched Riot synchronously when the match was not persisted.

## Decision

- Redis keys do not contain cache schema version tokens; test data is reset manually with `FLUSHALL`.
- `RedisKey` is the executable source of truth for every LoL Redis pattern and TTL. `Duration.ZERO` means persistent storage; positive durations are applied by `RedisClient` with `SETEX`.
- The balanced cache policy is: persistent R4J identity and pending-match payloads; six hours for Mongo-backed profile components and R4J rank/mastery payloads; one hour for projections, searches and pages; twelve hours for rebuildable champion/leaderboard aggregates; a temporary sixty seconds for spectator state (restore five minutes later); and five minutes for negative match lookups.
- The R4J `MATCH` payload remains persistent until the match is persisted or intentionally discarded by the tracker. Queue members are removed only after a terminal successful/intentional outcome; transient Mongo or analysis errors leave the queue member and payload available for retry.
- Search loads ranks in one Redis batch and one bounded SQL `IN` query for misses.
- Profile misses use the `LeagueService` component flows. Every saved getter reads Redis, then Mongo, and returns `null` when the component was never loaded; every async getter starts or reuses a deduplicated Riot Future on a miss.
- `ProfilePageService` starts the summoner, ranks and masteries Futures together. While one is incomplete the endpoint returns `202 profile_pending`; once the three components are ready, missing statistics still return the available profile as `PARTIAL` while `DatabaseTracker` queues the refresh.
- `PROFILE_PAGE` caches the profile components and aggregate without the volatile `recentMatches` list. The HTTP profile request loads up to five lightweight `MatchResult` rows separately, without events, and composes them into the response.
- The synchronous bot wrappers wait for the same Futures. A successful component Future persists the canonical value in Redis and Mongo once; a Riot error is retried later and is never persisted as an empty list.
- Champion build reads the single persisted aggregate before allowing command/refresh computation.
- Match detail follows `Redis -> DB -> Tracker lookup queue -> Riot -> existing match analysis queue`.
- The profile component Futures, match lookup queue and match analysis queue are the asynchronous work retained by the LoL flow.

## Invariants

- Canonical models remain the only success payloads.
- Missing summoner, rank and mastery data start immediately through `LeagueService` virtual-thread Futures.
- Missing profile statistics start through the general `DatabaseTracker` queue after the profile components are ready.
- Profile component lists are cached only after a confirmed Riot result; database or Riot failures do not cache empty lists.
- No Riot request is made by the match HTTP endpoint when the detail is missing.
- The existing Redis queue stores only matches already fetched from Riot.
- Redis expiration is an optimization and never the correctness mechanism: explicit invalidation and successful Mongo writes remain authoritative.
- Profile statistics are keyed by the complete `Filter.toSummonerKey()` together with the PUUID; recent matches are loaded separately from the same filter.
- Mongo persists profile statistics flat. The application uses `{ puuid, filterKey }` as the logical identity; `_id` is a random ObjectId created only on insert and is not used for lookup.

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

The complete profile-statistics flow and the index rationale are maintained in [`profile-statistics-source-of-truth.md`](../profile-statistics-source-of-truth.md).
