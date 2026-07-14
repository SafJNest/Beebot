# ADR-0004: Profile statistics refresh queue

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

Building profile statistics during an HTTP request makes leaderboard pages slow. The current queue is a separate service class, while the existing `Tracker` already owns scheduled ingestion work.

## Decision

`ProfileStatisticsService` is synchronous persistence/read logic only:

- `get(...)` reads Redis and DB and returns the existing aggregate or `null`;
- `refresh(...)` builds or updates and persists the aggregate.

`Tracker` temporarily owns the process-local statistics queue, deduplication set, bounded batch processing and retry behavior. The queue is not stored in Redis. `TrackerScheduler` invokes the processing method periodically.

The queue is best-effort across process restarts: a missing aggregate is enqueued again by the next request or scheduled flow.

## Processing rules

- Use a bounded `for` loop for each batch.
- Catch failures per item so one profile does not stop the batch.
- Remove deduplication state after success or final retry handling.
- Never build a missing aggregate synchronously from a leaderboard request.

## Rejected alternatives

- Redis queueing is unnecessary for the current process-local workflow.
- A `while` loop with broad try/catch obscures batch bounds and failure ownership.
- Keeping queue ownership in `ProfileStatisticsService` couples persistence and scheduling.

## Acceptance criteria

- `ProfileStatisticsQueue` is removed.
- Requests only read ready aggregates and enqueue missing work.
- Scheduler processing is bounded, deduplicated and resilient to individual failures.
