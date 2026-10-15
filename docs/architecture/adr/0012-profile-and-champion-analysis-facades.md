# ADR-0012: Profile and champion analysis facades

- Status: Accepted
- Owner: Main agent
- Date: 2026-08-01
- Amended: 2026-10-14

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
indexables, cache invalidation and refresh entry points. MongoDB's
`forEachChampionRawMatchEventBatch` is the sole bounded match/event join for
all champion queues and consumers. It returns raw projected matches, including
matches without event documents; it applies no statistics or build eligibility.
The union build projection includes participant IDs, boots, placement, subTeam
and skillOrder.
`ChampionAnalyzer` feeds statistics and requested build accumulators from the
same source scan. Statistics and standard builds require usable timelines;
CHERRY dispatches to `ArenaChampionAnalyzer`, which retains outcomes and global
Prismatics and up to six original augment positions without a core.
Full builds group attributed item purchases within each core in timeline order,
once per item ID. `builds[].items` excludes boots, Prismatics, Juices and the
Prismatic Anvil `220007`; when no attributable item timeline exists, recognized
final slots provide the fallback with the same exclusions.
Its only core is boots plus first Prismatic; it has no starter category.
A build-only miss reuses the same raw reader without rebuilding statistics.

Arena public requests use the existing major.minor patch parameter and support
rank/region through the canonical source filter; role is rejected. Internal full
patch scopes remain exact. Catalogs are loaded for the requested patch, never
substituted with the current patch. Arena stores schema approvato under
`build.arena` with `arenaLastUpdate`. CHERRY reads use Arena presence and freshness,
ignore old standard-only aggregates and expose the Arena subtree through the
canonical Build; HTTP CHERRY projects Arena directly as build without skill
orders or other standard legacy fields. Stats retain raw scope ownership and exact
subteam synergies, team sizes, `subteamsById` counts for Arena IDs 1–8, and overview placement counts plus average placement; the
added counters do not change synergy aggregation. Internal Build compatibility projections remain. Standard
readers and disjoint standard writers remain unchanged. Older Arena payloads
with the legacy global or per-build `consumables` fields are still returned;
their presence does not trigger regeneration. Old `skillOrders` data is ignored
and omitted from CHERRY responses without triggering a refresh. Present stale data is
returned while its background refresh is queued.

`lol.queue.scheduler.ComputeScheduler` always enqueues champion matrices, builds and the
scheduled champion refresh on the `CHAMPION` channel and inherits the common
task lifecycle from `AbstractQueueScheduler`. Profile-logical work may land on
either channel at insert (ADR-0010). While a matrix is queued, build
filters for the same `patch + queue` are merged into the matrix request. After
the matrix starts, a new build request is independently deduplicated by its full
`Filter.toKey()` and is processed as build-only work. CHERRY API and explicit Arena jobs share
`champion-arena:<Filter.toKey()>` for deduplication.

## Invariants

- Non-CHERRY payloads remain unchanged; the approved CHERRY contract is
  documented in docs/arena-build/champion-schema.json;
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
