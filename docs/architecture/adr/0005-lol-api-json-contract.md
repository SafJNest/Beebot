# ADR-0005: LoL API JSON contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The current API serializes similar data through profile DTOs, leaderboard DTOs and mapper-specific nested records. The project accepts a public JSON cleanup and will update consumers.

## Decision

Success payloads use canonical models from `lol.model`. Spring retains only HTTP error models.

Profile exposes the complete `SummonerView` shape. Leaderboard exposes page metadata and rows of `SummonerLeaderboard`, each with the same nested `summoner` view.

The leaderboard contract remains:

```text
page
pageSize
total
pages
summoners[]
  position
  summoner
```

`region` remains optional with internal default `GLOBAL`. `queue` remains optional with the existing solo ranked default. Leaderboard pages remain fixed at 50 elements.

Rank distribution and top-regions remain non-paginated and continue using their persistent aggregate/cache flow.

HTTP controllers unwrap the domain-level `ApiResult<T>` through one shared
`LolApiResponses` mapper. `READY` and `PARTIAL` are successful JSON payloads;
`PENDING` is returned as the standard `LolApiError` envelope with HTTP 202.

`ChampionStatistics.filter` remains part of the canonical object used by Redis
and Kryo, but the Spring mapper ignores it through a Jackson mixin because it
is an internal storage key and not part of the HTTP contract.

## Compatibility

This is an intentional public JSON change. No compatibility aliases for old DTO class names are introduced. Consumers must migrate to the canonical field structure.

## Acceptance criteria

- Profile, search, leaderboard and match success responses do not require Spring DTOs.
- Profile and leaderboard share the same serialized summoner shape.
- Pagination, default region, queue defaults and aggregate endpoints remain explicit and tested.
- HTTP status mapping is centralized and storage-only fields are not exposed accidentally.
