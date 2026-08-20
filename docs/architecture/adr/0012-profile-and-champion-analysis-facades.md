# ADR-0012: Profile and champion analysis facades

- Status: Accepted
- Owner: Main agent
- Date: 2026-08-01
- Amended: 2026-08-20

## Context

Profile and champion reads were split across page, aggregate, activity,
matchup, indexable, build and refresh services. Champion cold misses also
required two independent base Mongo scans for statistics and build data.

## Decision

`ProfileService` owns profile page composition, profile statistics, activity,
matchups, indexables, Redis/Mongo read-through and asynchronous `202`
coordination. `ProfileAnalyzer` is pure: it receives already-filtered matches
and produces or updates `ProfileStatistics`, `ProfileActivity` and
`ProfileMatchups`; it has no cache, Mongo or tracker dependency.

`ChampionService` owns the champion page, persisted statistics/build reads,
indexables, cache invalidation and refresh entry points. `ChampionAnalyzer`
owns the composed computation. For missing statistics it streams a single
`patch + queue` base cursor with the union stats/build projection. The matrix
accumulators receive every compatible match and requested build accumulators
receive only compatible participants from that same document. The bounded
`match_events` pass is performed only for statistics. A build-only miss keeps
the narrow build projection and never starts the events pass.

`lol.queue.DatabaseTracker` always enqueues champion matrices, builds and the
scheduled champion refresh on the `CHAMPION` channel and inherits the common
task lifecycle from `AbstractQueueScheduler`. Profile-logical work may land on
either channel at insert (ADR-0010). While a matrix is queued, build
filters for the same `patch + queue` are merged into the matrix request. After
the matrix starts, a new build request is independently deduplicated by its full
`Filter.toKey()` and is processed as build-only work.

## Invariants

- I payload esistenti restano invariati salvo l'aggiunta del `metadata` root;
  `200`/`202`/`206`/`404` mantengono i contratti indicati dagli endpoint.
- Mongo remains the persistence owner and Redis remains the cache owner.
- Ready-empty statistics and builds are persisted so a completed empty result
  is not enqueued indefinitely.
- A champion page cache entry is invalidated once after its persisted component
  result is saved.
- No analyzer fetches Riot data or runs on an HTTP request thread.

## Superseded ownership

This ADR supersedes the service ownership and independent champion build/stats
scheduling clauses of ADR-0006, and the `ProfilePageService` ownership clause
of ADR-0011. ADR-0006's public response contract and ADR-0011's R4J queue
rules remain in force.
