# ADR-0006: Champion API contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14
- Amended: 2026-08-20
- Service ownership superseded by ADR-0012; public HTTP contract remains in force

## Context

Champion statistics and builds are already persisted by the existing champion refresh services, but the API must return one complete aggregate that lets the UI choose which options to display. The existing read methods can compute missing data synchronously, which is not suitable for an API request.

## Decision

Expose `GET /api/lol/champion/{champion}` through `ChampionController` and `ChampionService`.

The request accepts optional `rank`, `region`, `queue` and `role` parameters. Missing rank and region mean that those dimensions are not filtered. Missing queue selects `TEAM_BUILDER_RANKED_SOLO`. A supplied rank keeps the existing minimum-tier behavior of `Filter`.

The success model is `ChampionView`, containing champion identity, `ChampionStatistics` and one `Build` aggregate. `Build` contains independent, bounded option lists for core builds/items, starters, boots, support items, item slots, complete rune configurations, summoner spell configurations, skill orders, prismatics and augment slots. `ChampionStatistics` contains the overview, advanced metrics, all valid matchups and all valid lane synergies. No list position means highest win rate or most used.

The HTTP request is orchestrated by one `ChampionService.get` flow. It first reads the complete page from Redis. On a miss, it reads the persisted statistics and build components without calculating match data on the request thread. If either component is missing, the filter is deduplicated in `ComputeScheduler`, a matrix refresh rooted at the requested `patch + queue` is submitted on the `CHAMPION` channel, a missing build is submitted on the same channel, and the request returns HTTP 202. Statistics persistence is one ready `ChampionStatsDocument` per `queue + rankBehavior + rank + patch + region` scope. Build persistence is one aggregate per `champion + lane + queue + rank + rankBehavior + patch + region` filter, plus opponent/duo when requested. Its `champions.<championId>.lanes.<lane>` values are raw `ChampionLeafStats`; the no-lane API view merges those leaves at runtime. Mongo never stores a `ChampionStatistics`, `overview`, `filter`, `laneStats`, `statistics.<championId>`, or a lane-specific document. A ready scope without the requested champion returns an empty 200 result; when both components are available, the service builds `ChampionView` and caches the complete page. Statistics and build each expose their own update timestamp for stale detection.

The scheduler is started explicitly and idempotently by the application. It owns the calendar trigger and submits the scheduled full champion refresh to `DatabaseTracker`; API-triggered work remains request-driven and deduplicated by the same database queue.

Champion statistics, builds and profile statistics use the shared Jackson JSON codec. MariaDB stores UTF-8 JSON text and Mongo stores structured BSON. Champion stats have one raw aggregate document per scope; legacy champion-stat readers, markers and compatibility fallbacks are not supported. Invalid payloads are treated as missing so the refresh flow can recreate them.

## Ownership

- `ChampionController` owns HTTP parsing and status mapping.
- `ChampionService` owns filter construction, page cache, response assembly, persisted statistics/build reads and refresh entry points.
- `ChampionUtils` owns champion name and image resolution.
- `ChampionAnalyzer` owns composed champion statistics and build computation.
- `DatabaseTracker` owns asynchronous champion data requests and in-flight deduplication.
- Match queue ownership remains in the existing match tracker flow.

## Invariants

- No match aggregation or Riot fetch runs during the HTTP request.
- No second public DTO is created for build or champion statistics.
- Role is rejected when the selected queue does not support lanes.
- The page cache is written only when both aggregates are ready.
- Missing global statistics are deduplicated by `champion-stats-matrix:<patch>:<queue>`, while missing builds are deduplicated by `champion-build:<Filter.toKey()>`; both jobs are submitted immediately to the database queue.
- A matrix starts only from the requested patch and queue, enumerates global and active-region filters plus cumulative rank thresholds, and persists one `ready=true` raw scope document even when `champions={}`.
- API page reads do not call the synchronous command fallback methods.
- Automatic `mostCommonBuild`, `highestWinrate`, `getMostUsed` and `getHighWinrate` selections are not part of the service contract.
- Every bounded build category contains at most three options; empty source data remains an empty list.
- Augments are persisted and exposed by slot, preserving augment order.
- Champion stats and page cache keys include rank behavior, period and requested lane; persistence is scope-only and stores lane leaves beneath the champion. Recomputing one scope invalidates statistics and page caches for every champion and valid lane in that scope.
- Missing advanced metrics are represented as `null`, never as fabricated zeroes.

## HTTP behavior

- `200`: complete `ChampionView`.
- `202`: `LolApiError` with code `champion_data_pending`.
- `400`: invalid enum or incompatible role.
- `404`: unknown champion.

## Rejected alternatives

- Computing raw match aggregates in the request would reintroduce unpredictable latency.
- Creating `ChampionStatsView` or `BuildView` would duplicate existing canonical models.
- Returning a successful page with only one aggregate would make the response contract ambiguous.

## Acceptance criteria

- The endpoint returns stats and one complete build aggregate in one response.
- The response exposes all valid matchup and lane-synergy rows needed by the UI.
- Missing data starts immediately in the background and is never computed synchronously by the controller.
- A ready response is cached for five minutes.
- Rank, region, queue and role are represented by the existing typed domain values.
