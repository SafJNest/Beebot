# Macro-task 0003: statistics and Tracker queue

## Objective

Separate statistics persistence from asynchronous refresh and prevent request-time aggregate construction.

## Dependencies

- Macro-task 0000 approved;
- ADR-0004 accepted.

## Scope

- reduce `ProfileStatisticsService` to read and refresh methods;
- remove `ProfileStatisticsQueue`;
- move queue, deduplication, bounded batch processing and retry into `Tracker`;
- update `TrackerScheduler`.

## Out of scope

- generic Tracker refactor;
- Redis queueing;
- leaderboard response redesign.

## Invariants

- queue is process-local and not Redis-backed;
- missing aggregates return without synchronous rebuild;
- one failed item does not stop a batch;
- repeated enqueue requests are deduplicated;
- scheduler remains the periodic orchestrator.

## Acceptance criteria

- `ProfileStatisticsQueue` is deleted;
- request paths only read ready data and enqueue missing work;
- processing uses a bounded `for` loop and per-item failure handling;
- scheduler still processes the queue periodically.

## Handoff

Report queue ownership, retry behavior, batch limits, scheduler changes and verification results.
