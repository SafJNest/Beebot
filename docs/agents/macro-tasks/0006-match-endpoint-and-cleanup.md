# Macro-task 0006: match endpoint and cleanup

## Objective

Finish the public match migration and remove obsolete DTO and mapper structures.

## Dependencies

- Macro-task 0002 approved;
- Macro-task 0004 approved;
- Macro-task 0005 approved;
- ADR-0001, ADR-0003 and ADR-0005 accepted.

## Scope

- update LoL controllers and match endpoint responses;
- return canonical `Match` or `MatchResult` according to endpoint size;
- remove `LolApiMapper` after its last consumer migrates;
- remove obsolete Spring success DTOs and unused imports;
- update architecture documentation with any accepted migration decisions.

## Out of scope

- new match ingestion features;
- changes to Riot API behavior;
- unrelated Spring error handling.

## Invariants

- Spring contains no operational LoL success DTO;
- full match and lightweight result remain distinct;
- tracker-only Riot records are not exposed;
- API JSON follows ADR-0005.

## Acceptance criteria

- match endpoints return canonical models;
- `LolApiMapper` has no remaining consumers and is removed;
- old DTOs, imports and duplicate recent-match structures are removed;
- no undocumented API compatibility behavior remains.

## Handoff

Report final endpoint mapping, removed files, serialization checks, cleanup search results and remaining risks.
