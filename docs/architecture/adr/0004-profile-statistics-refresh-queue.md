# ADR-0004: Profile statistics asynchronous generation

- Status: Superseded by ADR-0010
- Owner: Main agent
- Date: 2026-07-14

## Context

Building profile statistics during an HTTP request makes profile and leaderboard pages slow. API-triggered refreshes must start immediately without waiting for a process-local application queue.

## Decision

`ProfileStatisticsService` is synchronous persistence/read logic only:

- `get(...)` reads Redis and DB and returns the existing aggregate or `null`;
- `refresh(...)` builds or updates and persists the aggregate.

`DatabaseTracker` owns the two-worker executor, FIFO queue and in-flight deduplication for API-triggered statistics generation. A missing aggregate is submitted immediately by the request path and is processed by the database queue. The match lookup and match analysis queues remain separate.

The aggregate identity is always `puuid + Filter.toSummonerKey()`. The complete filter, including queue, lane, champion, opponent, duo, rank behavior, patch, region and period, is passed unchanged from the request to Mongo match filtering and persistence. The detailed data contract is documented in [`profile-statistics-source-of-truth.md`](../profile-statistics-source-of-truth.md).

## Processing rules

- Remove in-flight deduplication state after success or failure.
- Allow a later request to retry a failed generation.
- Never build a missing aggregate synchronously from an HTTP request.
- Store the generated aggregate flat in `profile_statistics` under the unique `{ puuid, filterKey }` identity.

## Historical alternatives

- Redis queueing was unnecessary for the original request-triggered workflow.
- Keeping asynchronous execution in `ProfileStatisticsService` still couples persistence and scheduling and remains rejected.

## Historical acceptance criteria

- Profile-statistics queue state and processor methods were removed by the original migration.
- The current dispatch and concurrency rules are defined by ADR-0010.
