# Macro-task 0004: profile and search API

## Objective

Move profile and search endpoints to the canonical summoner models and remove Spring success DTO duplication.

## Dependencies

- Macro-task 0001 approved;
- Macro-task 0003 approved;
- ADR-0001, ADR-0002 and ADR-0005 accepted.

## Scope

- update `ProfilePageService` to build `SummonerView`;
- update search to use `Summoner` and `Rank`;
- load search ranks with one Redis batch and one bounded DB query;
- cache the complete profile view only after statistics are ready;
- move overview and recent results to canonical models;
- remove profile/search mapping duplication;
- keep Spring errors and controller concerns only.

## Out of scope

- leaderboard-specific cache behavior;
- asynchronous match lookup migration.

## Invariants

- profile and leaderboard share the same nested summoner representation;
- missing statistics are submitted immediately through `DatabaseTracker`;
- no Riot request is introduced for view serialization;
- existing profile data remains available through canonical fields.

## Acceptance criteria

- profile endpoint returns the canonical view;
- search returns canonical summoner/rank data;
- duplicate profile DTOs and mapper paths are removed or have a documented remaining HTTP-only use;
- JSON changes are recorded against ADR-0005.

## Handoff

Report endpoint changes, removed DTOs, consumer impact, serialization checks and unresolved API differences.
