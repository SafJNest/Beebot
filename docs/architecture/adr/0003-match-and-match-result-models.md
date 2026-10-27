# ADR-0003: Match and match result models

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The repository contains a complete `Match`, a complete `Participant`, `ProfileMatch`, profile participant records and DTO-specific recent-match structures.

## Decision

Keep one complete match model and one lightweight result projection:

- `Match`: complete persisted match, including events, bans and full participants;
- `Participant`: complete global participant model;
- `MatchResult`: lightweight result used by profile lists, overviews and recent-match payloads.

Riot-specific tracker records remain internal to the service/tracker boundary. They are converted to the canonical models before being returned by an API controller.

## Projection rules

- `MatchResult` is created by a factory from the available match data.
- A result may contain only the participant fields required by the lightweight contract.
- `RecentMatch`, `ProfileMatch` and DTO-specific participant classes are removed after all consumers migrate.
- A complete match endpoint returns `Match`; list and overview endpoints return `MatchResult`.

## Rejected alternatives

- Keeping a different recent-match DTO per endpoint multiplies serialization rules.
- Returning full `Match` objects for every profile row increases payload size and couples list views to event data.
- Exposing Riot tracker records leaks ingestion-specific structures into the public API.

## Acceptance criteria

- There is one complete match owner and one lightweight result owner.
- Profile and match list responses no longer define their own recent-match records.
- JSON serialization is migrated explicitly where field names or packages change; Kryo is not part of the runtime.

## Amendment 2026-10-20: profile match facets

The canonical `MatchPage` keeps its existing page fields and includes the
`MatchFacets.Facets` projection for the selected match filters. It reuses
`FacetsService` so the dedicated `/facets` endpoint and `/matches` share the
same disjunctive facet calculation and Redis cache. The page's root `total`
continues to describe the exact selected match filter.

## Amendment 2026-10-27: one public match structure

At the owner's explicit request, this supersedes the lightweight list contract
above. Both `GET /profile/{puuid}/matches` items and `GET /match/{gameId}` return
canonical `Match`: general match fields, bans and complete `Participant` values.
The PUUID scopes selection; no requested-player fields are copied to the match
root. Consumers find their participant by PUUID and derive team totals as needed.

`MatchPage.items` is `List<Match>`. The page reads complete Mongo `match`
documents without a field projection and never joins `match_events`. List
items omit `events`. The single-match read uses the same mapper and adds the
entire stored timeline under the existing `events` property; missing timeline
data is an empty object, not invented events. Page metadata belongs to the page;
detail response metadata remains at the single-match root.

No new DTO, collection, data backfill or index is required. `MatchResult` remains
only for existing internal statistics compatibility and serialization. Its
requested-player mapper and Mongo list projection are removed. This is a
breaking public list contract: fields such as `championId`, `gold`, `vision`,
`items` and `teamKills` no longer appear at the match root. Participant field
names remain canonical (`champion`, `goldEarned`, `visionScore`, `item0`–`item6`).

Timeline acquisition, persistence, filters, facets, page limits, cache ownership,
statistics generation and Discord presentation remain unchanged. Larger page
payloads contain full participants but no timeline; live query-plan and payload
measurements are separate from offline contract checks.
