# Macro-task 0008: profile and global records

## Objective

Expose personal and global League records from canonical matches and timeline
events without changing profile statistics, rank ownership or presentation of
existing endpoints.

## Dependencies

- ADR-0003, ADR-0005 and ADR-0009 accepted;
- canonical `Filter.toSummonerKey()` profile identity available;
- match events persisted separately from match documents.

## Scope

- canonical record models, metric ownership and value ordering;
- `profile_records` rebuildable Mongo projection;
- bounded profile match/event calculation on the existing PROFILE compute worker;
- profile and global Records API contracts;
- operator Mongo indexes and administrative rebuild.

## Invariants

- identity is `{ puuid, filterKey, metric }`, with ObjectId as storage-only `_id`;
- `Rank` remains Riot-owned; record rank/LP/MMR is a historical participant snapshot;
- positions are never stored;
- individual records omit `gameShared`; TEAM and MATCH records set it to true;
- timeline absence omits only timeline metrics, never invents zero;
- global query is an indexed `profile_records` read, not an aggregation over profile statistics.

## Initial metrics

`KILLS`, `DEATHS`, `ASSISTS`, `FIRST_BLOOD_TIME`, `PENTAKILLS`, `CS`,
`DAMAGE_DEALT`, `DAMAGE_TAKEN`, `BARON_KILLS`, `ELDER_KILLS` are participant
metrics. `FIRST_DRAKE_TIME`, `FIRST_BARON_TIME`, `FIRST_ELDER_TIME`,
`BARONS_TAKEN`, `ELDERS_TAKEN` are TEAM metrics. `LONGEST_GAME` is a MATCH
metric. Highest is best except the `FIRST_*_TIME` metrics, where the lower
timeline timestamp wins.

## Acceptance criteria

- metrics are covered by focused analyzer tests;
- match event reads are batched and no N+1 event lookup exists;
- API uses canonical models and standard 202 handling;
- indexes, Mongo collection and API contract are documented.
