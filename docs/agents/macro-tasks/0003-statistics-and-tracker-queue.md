# Macro-task 0003: statistics and Tracker asynchronous generation

## Objective

Separate statistics persistence from request handling and prevent request-time aggregate construction.

## Dependencies

- Macro-task 0000 approved;
- ADR-0004 accepted.

## Scope

- reduce `ProfileStatisticsService` to read and refresh methods;
- move immediate API-triggered generation and in-flight deduplication into `Tracker`;
- keep the existing match queues separate and unchanged;
- remove profile-statistics queue processing from `TrackerScheduler`.

## Out of scope

- generic Tracker refactor;
- Redis match queue behavior;
- leaderboard response redesign outside the missing-statistics status change.

## Invariants

- API-triggered generation starts immediately on a virtual thread;
- no Profile Statistics application queue or retry queue exists;
- repeated requests for the same PUUID and complete summoner filter are deduplicated while running;
- one failed generation does not stop another generation;
- failed in-flight markers are removed so a later request can retry;
- match lookup and match analysis queues remain process-owned and unchanged.

## Acceptance criteria

- profile-statistics queue fields and processor methods are removed;
- request paths only read ready aggregates and start missing work asynchronously;
- scheduler no longer processes Profile Statistics;
- match queue processing remains available.

## Handoff

Report executor ownership, deduplication keys, failure cleanup, scheduler changes and verification results.

## Implemented flow reference

The implemented source-of-truth contract is [`profile-statistics-source-of-truth.md`](../../architecture/profile-statistics-source-of-truth.md). The important recovery invariant is:

```text
ProfileStatistics identity = puuid + Filter.toSummonerKey()
Mongo unique index        = { puuid: 1, filterKey: 1 }
Mongo _id                 = random ObjectId, $setOnInsert only
recentMatches             = separate MatchResult query with the same Filter
```

`ProfileStatisticsService` owns read, calculation and persistence. `Tracker` owns async dispatch and in-flight deduplication using `puuid + ":" + filterKey`. Overview, profile and `!summoner` read the same aggregate, while each existing presentation remains unchanged unless a style refactor is explicitly requested. `lastUpdate` is written after the calculation completes.
