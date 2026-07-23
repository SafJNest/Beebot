# ADR-0004: Profile statistics asynchronous generation

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

Building profile statistics during an HTTP request makes profile and leaderboard pages slow. API-triggered refreshes must start immediately without waiting for a process-local application queue.

## Decision

`ProfileStatisticsService` is synchronous persistence/read logic only:

- `get(...)` reads Redis and DB and returns the existing aggregate or `null`;
- `refresh(...)` builds or updates and persists the aggregate.

`Tracker` owns the virtual-thread executor and the in-flight deduplication set for API-triggered statistics generation. A missing aggregate is submitted immediately by the request path. There is no profile-statistics application queue, retry queue or periodic processor.

The match lookup and match analysis queues remain separate and unchanged.

The aggregate identity is always `puuid + Filter.toSummonerKey()`. The complete filter, including queue, lane, champion, opponent, duo, rank behavior, patch, region and period, is passed unchanged from the request to Mongo match filtering and persistence. The detailed data contract is documented in [`profile-statistics-source-of-truth.md`](../profile-statistics-source-of-truth.md).

## Processing rules

- Remove in-flight deduplication state after success or failure.
- Allow a later request to retry a failed generation.
- Never build a missing aggregate synchronously from an HTTP request.
- Store the generated aggregate flat in `profile_statistics` under the unique `{ puuid, filterKey }` identity.

## Rejected alternatives

- Redis queueing is unnecessary for the request-triggered workflow.
- Keeping asynchronous execution in `ProfileStatisticsService` couples persistence and scheduling.

## Acceptance criteria

- Profile-statistics queue state and processor methods are removed.
- Requests only read ready aggregates and start missing work immediately.
- Background execution is deduplicated and resilient to individual failures.
