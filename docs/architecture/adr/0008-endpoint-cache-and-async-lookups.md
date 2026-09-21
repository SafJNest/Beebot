# ADR-0008: Component caches and asynchronous match lookups

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14
- Amended: 2026-08-15

## Context

The LoL endpoints reuse the same profile and ranked data across requests. Search performed one rank lookup per result, profile assembled the complete view on every cache miss, champion build lookup loaded all stored builds, and match detail fetched Riot synchronously when the match was not persisted.

## Decision

- Redis keys do not contain cache schema version tokens; test data is reset manually with `FLUSHALL`.
- `RedisKey` is the executable source of truth for every LoL Redis pattern and TTL. `Duration.ZERO` means persistent storage; positive durations are applied by `RedisClient` with `SETEX`.
- Every raw R4J cache key starts with `beebot:lol:r4j:`; League OS/application
  keys start with `beebot:lol:ls:`. PUUID-scoped summoner keys place the actual
  values first, for example `beebot:lol:ls:EUROPE:EUW1:<puuid>:summoner` or
  `beebot:lol:ls:EUROPE:EUW1:<puuid>:summoner:statistics:<filterKey>`.
- During the current local-debug window every `RedisKey` uses a 60-second TTL;
  the production TTL to restore is recorded beside each enum entry.
- The balanced cache policy is: persistent R4J identity and fetched-match payloads; six hours for Mongo-backed profile components and R4J rank/mastery payloads; one hour for projections, searches and pages; twelve hours for rebuildable champion/leaderboard aggregates; and a temporary sixty seconds for spectator state (restore five minutes later). There is no negative match cache.
- The R4J `MATCH` payload remains persistent until the match and its participant summoner seeds are inserted or intentionally discarded. A background match job performs one `fetch -> insert` attempt: a null R4J result marks the match missing and ends the job; there is no retry map or automatic retry. A later request may queue a new attempt. The subsequent forced rank refresh is low-priority R4J work and does not block match insertion; the tracker does not refresh masteries.
- Search loads ranks in one Redis batch and one bounded SQL `IN` query for misses.
- Profile reads use saved getters for rank and mastery: Redis first, then Mongo, with an absent component represented as an empty view value. `POST /profile/{puuid}/refresh` is the only profile flow that starts their Riot refreshes; it uses the same R4J scheduling path.
- `ProfileService` starts a summoner Future only when the profile base is absent. It never starts rank or mastery Riot work during `GET`; once the base is ready, missing statistics still return the available profile as `PARTIAL` while `DatabaseTracker` queues the aggregate refresh.
- `SUMMONER_OVERVIEW` caches the summoner components and aggregate without the volatile `recentMatches` list. The HTTP profile request loads up to five lightweight `MatchResult` rows separately, without events, and composes them into the response.
- The synchronous bot wrappers wait for the same Futures. A successful component Future persists the canonical value in Redis and Mongo once; a Riot error is retried later and is never persisted as an empty list.
- Champion build reads the single persisted aggregate before allowing command/refresh computation.
- Match detail follows `Redis -> Mongo -> MatchService background fetch -> insert`.
- The profile component Futures and MatchService jobs are the asynchronous work retained by the LoL flow.

## Invariants

- Canonical models remain the only success payloads.
- Missing profile-base data starts immediately through the `SummonerService` Future. Missing rank and mastery data are fetched only by explicit profile refresh.
- Missing profile statistics start through `DatabaseTracker` after the profile components are ready.
- Profile component lists are cached only after a confirmed Riot result; database or Riot failures do not cache empty lists.
- No Riot request is made by the match HTTP endpoint when the detail is missing.
- Redis stores fetched R4J payloads only; it does not store a match queue, retry set or negative lookup value.
- Redis expiration is an optimization and never the correctness mechanism: explicit invalidation and successful Mongo writes remain authoritative.
- Profile statistics are keyed by the complete `Filter.toSummonerKey()` together with the PUUID; recent matches are loaded separately from the same filter.
- Mongo persists profile statistics flat. The application uses `{ puuid, filterKey }` as the logical identity; `_id` is a random ObjectId created only on insert and is not used for lookup.

## Rejected alternatives

- A rank JOIN for search was rejected because the search result is already limited and a bounded batch query keeps the large rank table out of the main search plan.
- Caching incomplete profile pages was rejected because it would hide refreshed statistics until expiry.
- A negative match cache was rejected because a null R4J response is already a terminal result for its single queued attempt.

## Acceptance criteria

- Search has no per-result rank query.
- Profile cache hits avoid component assembly.
- Champion API does not compute builds.
- Match misses return `202` and are resolved by the scheduler.
- No Redis key pattern contains `v1`, `v2` or `v3`.

The complete profile-statistics flow and the index rationale are maintained in [`profile-statistics-source-of-truth.md`](../profile-statistics-source-of-truth.md).
