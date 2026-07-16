# ADR-0006: Champion API contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

Champion statistics and builds are already persisted by the existing champion refresh services, but the API must return one complete aggregate that lets the UI choose which options to display. The existing read methods can compute missing data synchronously, which is not suitable for an API request.

## Decision

Expose `GET /api/lol/champion/{champion}` through `ChampionController` and `ChampionPageService`.

The request accepts optional `rank`, `region`, `queue` and `role` parameters. Missing rank and region mean that those dimensions are not filtered. Missing queue selects `TEAM_BUILDER_RANKED_SOLO`. A supplied rank keeps the existing minimum-tier behavior of `Filter`.

The success model is `ChampionView`, containing champion identity, `ChampionStatistics` and one `Build` aggregate. `Build` contains independent, bounded option lists for core builds/items, starters, boots, support items, item slots, complete rune configurations, summoner spell configurations, skill orders, prismatics and augment slots. `ChampionStatistics` contains the overview, advanced metrics, all valid matchups and all valid lane synergies. No list position means highest win rate or most used.

The HTTP request is orchestrated by one `ChampionPageService.get` flow. It first reads the complete page from Redis. On a miss, its internal `compute` reads the persisted stats and build components from Redis/DB without calculating match data. If either component is missing, the filter is deduplicated in `Tracker`, a refresh starts immediately on a virtual thread and the request returns HTTP 202. When both components are available, the service builds `ChampionView` and caches the complete page.

The scheduler is started explicitly and idempotently by the application. It does not register processors for API-triggered profile or champion work; the scheduled full champion refresh remains independent of the request path.

Champion statistics, builds and profile statistics use one Kryo configuration for the current model classes. The decoder keeps read-only fallbacks for the stable and legacy registrations already used by persisted data. If a persisted payload cannot be decoded or validated, the data is treated as missing so the existing refresh flow can recreate it; the database blob is preserved.

## Ownership

- `ChampionController` owns HTTP parsing and status mapping.
- `ChampionPageService` owns filter construction, page cache and response assembly.
- `ChampionUtils` owns champion name and image resolution.
- `ChampionStatsService` owns champion statistics persistence, advanced aggregation, matchup and lane-synergy rebuild operations; its internal API read is storage-only.
- `BuildService` owns the single build aggregate persistence and rebuild operation; its internal API read is storage-only.
- `Tracker` owns asynchronous champion data requests and in-flight deduplication.
- Match queue ownership remains in the existing match tracker flow.

## Invariants

- No match aggregation or Riot fetch runs during the HTTP request.
- No second public DTO is created for build or champion statistics.
- Role is rejected when the selected queue does not support lanes.
- The page cache is written only when both aggregates are ready.
- Missing data is deduplicated by the complete `Filter.toKey()` value and starts immediately in the background.
- API page reads do not call the synchronous command fallback methods.
- Automatic `mostCommonBuild`, `highestWinrate`, `getMostUsed` and `getHighWinrate` selections are not part of the service contract.
- Every bounded build category contains at most three options; empty source data remains an empty list.
- Augments are persisted and exposed by slot, preserving augment order.
- Champion stats cache and persistence keys include lane when a lane is selected; no-lane keys remain global.
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
