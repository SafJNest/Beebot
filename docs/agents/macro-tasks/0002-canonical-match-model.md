# Macro-task 0002: canonical match model

## Objective

Consolidate complete matches and lightweight match results into one consistent model hierarchy.

## Dependencies

- Macro-task 0001 approved;
- ADR-0001 and ADR-0003 accepted.

## Scope

- place `Match` and `Participant` under `lol.model.match`;
- convert `ProfileMatch` to `MatchResult`;
- replace profile and DTO-specific recent-match records;
- migrate Kryo and JSON serialization consumers;
- keep Riot tracker records internal.

## Out of scope

- leaderboard pagination and cache;
- statistics queue scheduling;
- unrelated match ingestion changes.

## Invariants

- one complete `Match` model exists;
- one complete global `Participant` model exists;
- `MatchResult` is the only lightweight list projection;
- full match endpoints return `Match`;
- list and overview endpoints return `MatchResult`.

## Acceptance criteria

- no `ProfileMatch`, `RecentMatch` or duplicate participant success DTO remains;
- all serialization changes are explicit;
- profile recent matches use `MatchResult`.

## Handoff

Report canonical fields, removed records, serialization migration and any compatibility risk.
