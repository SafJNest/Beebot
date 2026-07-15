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
- repeated requests for the same summoner and season are deduplicated while running;
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
