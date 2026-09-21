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
