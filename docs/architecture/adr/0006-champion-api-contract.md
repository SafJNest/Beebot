# ADR-0006: Champion API contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

Champion statistics and builds are already persisted by the existing champion refresh services, but there is no HTTP endpoint that returns both aggregates together. The existing read methods can compute missing data synchronously, which is not suitable for an API request.

## Decision

Expose `GET /api/lol/champion/{champion}` through `ChampionController` and `ChampionPageService`.

The request accepts optional `rank`, `region`, `queue` and `role` parameters. Missing rank and region mean that those dimensions are not filtered. Missing queue selects `TEAM_BUILDER_RANKED_SOLO`. A supplied rank keeps the existing minimum-tier behavior of `Filter`.

The success model is `ChampionView`, containing champion identity, the existing `ChampionStatistics` aggregate and the existing `Build` aggregate selected by games.

The HTTP request is orchestrated by one `ChampionPageService.get` flow. It first reads the complete page from Redis. On a miss, its internal `compute` reads the persisted stats and build components from Redis/DB without calculating match data. If either component is missing, the filter is deduplicated in `Tracker` and the request returns HTTP 202 until the scheduled refresh or the owner `pushqueue` drain produces both components. When both components are available, the service builds `ChampionView` and caches the complete page.

The scheduler is started explicitly and idempotently by the application. In testing mode it does not register periodic jobs; `pushqueue` is the manual processor for the Profile Statistics and Champion Data queues.

Champion statistics, builds and profile statistics use one Kryo configuration for the current model classes. There is no legacy decoder or compatibility fallback. If a persisted payload cannot be decoded or validated, its row is deleted and the data is treated as missing so the existing refresh flow can recreate it.

## Ownership

- `ChampionController` owns HTTP parsing and status mapping.
- `ChampionPageService` owns filter construction, page cache and response assembly.
- `ChampionUtils` owns champion name and image resolution.
- `ChampionStatsService` owns champion statistics persistence, command fallback and rebuild operations; its internal API read is storage-only.
- `BuildService` owns build persistence, command fallback and rebuild operations; its internal API read is storage-only.
- `Tracker` owns asynchronous champion data requests.
- `PushQueue` owns only the owner-command trigger for draining the application queues; it does not process the Redis match queue.

## Invariants

- No match aggregation or Riot fetch runs during the HTTP request.
- No second public DTO is created for build or champion statistics.
- Role is rejected when the selected queue does not support lanes.
- The page cache is written only when both aggregates are ready.
- Missing data is deduplicated by the complete `Filter.toKey()` value.
- API page reads do not call the synchronous command fallback methods.
- `getStored`, `getLazy` and `getMostUsedLazy` are not part of the service contract.

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

- The endpoint returns stats and the most used build in one response.
- Missing data is queued and never computed synchronously by the controller.
- A ready response is cached for five minutes.
- Rank, region, queue and role are represented by the existing typed domain values.
