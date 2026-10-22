# ADR-0008: Component caches and asynchronous match lookups

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14
- Amended: 2026-09-23

## Context

The LoL endpoints reuse the same profile and ranked data across requests. Search performed one rank lookup per result, profile assembled the complete view on every cache miss, champion build lookup loaded all stored builds, and match detail fetched Riot synchronously when the match was not persisted.

## Decision

- Redis keys do not contain cache schema version tokens; test data is reset manually with `FLUSHALL`.
- `RedisKey` is the executable source of truth for every LoL Redis pattern and TTL. `Duration.ZERO` means persistent storage; positive durations are applied literally by `RedisClient`. `set`, `setCached`, `claim`, list/set writes, sorted-set publication and TTL refreshes all derive expiration from the owning `RedisKey`.
- Every raw R4J cache key starts with `beebot:lol:r4j:`; League OS/application
  keys start with `beebot:lol:ls:`. PUUID-scoped summoner keys place the actual
  values first, for example `beebot:lol:ls:EUROPE:EUW1:<puuid>:summoner` or
  `beebot:lol:ls:EUROPE:EUW1:<puuid>:summoner:statistics:<filterKey>`.
- R4J's own response cache is configured with `EmptyCacheProvider`; Redis is
  the only cache for R4J payloads. Static Data Dragon items, champions,
  summoner spells, runes and version metadata are cached in Redis for one day.
  Other Redis cache writes use the TTL declared by their respective `RedisKey`.
- Every Redis entry uses the exact TTL declared by its `RedisKey`: for example, one hour for matchlist responses, sixty seconds for spectator state, and one day for Data Dragon static data. `Duration.ZERO` remains persistent. There is no negative match cache.
- Summoner search caches use the normalized query and an explicit scope in Redis: shard searches retain their shard-specific keys, while global search uses `GLOBAL:GLOBAL`. The existing one-hour `SUMMONER_SEARCH` TTL applies to both scopes.
- Tracker timeline analysis uses `R4J_TIMELINE` in Redis so the insert and rank-tracking passes reuse one Riot timeline response. The tracker explicitly deletes this entry in `finally`; its two-minute key TTL is only a fallback if the tracker exits outside its normal cleanup path.
- The R4J `MATCH` Redis payload expires after the one-hour TTL declared by `R4J_MATCH`; Mongo remains the durable match source. A background match job performs one `fetch -> insert` attempt: a null R4J result marks the match missing and ends the job; there is no retry map or automatic retry. A later request may queue a new attempt. The subsequent forced rank refresh is low-priority R4J work and does not block match insertion; the tracker does not refresh masteries.
- Search loads ranks in one Redis batch and one bounded SQL `IN` query for misses.
- Profile reads use saved getters for rank and mastery: Redis first, then Mongo. When either component is absent, `GET` queues its initial Riot fetch through the existing R4J scheduling path and returns the available profile as `PARTIAL` with `metadata.refresh=true`. Existing components are not refreshed by `GET`; `POST /profile/{puuid}/refresh` remains the explicit forced refresh.
- `ProfileService` starts a summoner Future only when the profile base is absent. When Riot ID resolution is pending, its completion also starts base-profile loading so a second request is not required to advance the flow. The Spring `GET` does not wait for rank, mastery or statistics work: once the summoner base is available, it returns the available view while missing statistics are queued through the existing compute scheduler and missing rank/mastery through their existing services. `202` is reserved for Riot ID resolution or base-profile loading still in progress.
- `SUMMONER_OVERVIEW` caches the summoner components and aggregate without the volatile `recentMatches` list. The HTTP profile request loads up to five lightweight `MatchResult` rows separately, without events, and composes them into the response.
- The synchronous bot wrappers wait for the same Futures. A successful component Future persists the canonical value in Redis and Mongo once; a Riot error is retried later and is never persisted as an empty list.
- Champion build reads the single persisted aggregate before allowing command/refresh computation.
- Match detail follows `Redis -> Mongo -> MatchService background fetch -> insert`.
- The profile component Futures and MatchService jobs are the asynchronous work retained by the LoL flow.
- Profile match pages dispatch the page lookup and cached/disjunctive facet lookup concurrently on virtual threads, then combine both canonical results into one asynchronous HTTP response. The facet aggregation supplies both the facets and page total, so `/matches` does not issue a separate exact-count query.

## Invariants

- Canonical models remain the only success payloads.
- Missing profile-base data starts immediately through the `SummonerService` Future. Missing rank and mastery components are queued by profile `GET`; forced refresh of existing components remains exclusive to the explicit profile refresh.
- Missing profile statistics start through `ComputeScheduler` after the profile base is ready.
- Profile component lists are cached only after a confirmed Riot result; database or Riot failures do not cache empty lists.
- No Riot request is made by the match HTTP endpoint when the detail is missing.
- Redis stores fetched R4J payloads and Data Dragon static data; it does not
  store a match queue, retry set or negative lookup value. `EmptyCacheProvider`
  prevents R4J from reading or writing its filesystem cache.
- Tracker timeline cache entries are match-scoped, shared by the two analysis passes, and explicitly invalidated when tracking completes.
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

The profile-statistics ownership and identity summary is maintained in [`profile-statistics-source-of-truth.md`](../profile-statistics-source-of-truth.md).
