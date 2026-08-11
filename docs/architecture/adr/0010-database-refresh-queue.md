# ADR-0010: Database refresh queue

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-26
- Supersedes: the no-application-queue portion of ADR-0004

## Context

Profile statistics and champion statistics/builds are expensive Mongo calculations. A request that returns `202` must not create an unbounded number of concurrently running calculations, even when every request has a different logical key.

## Decision

`DatabaseTracker` owns asynchronous database calculation dispatch. It contains:

- one process-local FIFO `LinkedBlockingQueue` for champion tasks (builds and statistics) and one for profile tasks;
- one in-flight map keyed by the logical resource key and holding the task `CompletableFuture`;
- exactly two virtual-thread workers: worker 1 consumes profile work, while worker 2 serializes champion work and helps profiles only while its champion queue is empty;
- task removal from the in-flight map only after success or failure.

The queues store task suppliers and their completion futures. Suppliers are not started by the request thread. Duplicate submissions return the existing future and do not add another queue entry. Champion builds, champion stats and scheduled champion refreshes execute only on worker 2 in one FIFO sequence. Profile statistics and profile matchups enter the profile queue: worker 1 consumes it first and worker 2 may consume it when no champion task is waiting. Failed tasks complete exceptionally and become retryable after their key is removed.

Normal task lifecycle events are not written to the application log because high-volume queue/profile logging slows the workers. The owner-only `tracker` command reads the two worker snapshots on demand and reports readable job names, current work, queue positions, cumulative progress and scheduler state in three separate embeds. Task failures remain logged.

The queue owns profile-statistics refreshes, champion stats/build refreshes and the scheduled champion-data refresh. `TrackerScheduler` owns the calendar trigger and submits the scheduled job. The match lookup and match analysis queues remain owned by `Tracker` and are unchanged.

`ProfileStatisticsService`, `ChampionStatsService`, `BuildService` and `ChampionDataRefreshService` remain synchronous calculation/persistence owners. HTTP requests continue to read ready data, enqueue missing work and preserve the existing `202`, `PARTIAL` and response payload contracts.

## Processing rules

- profile key: `profile-statistics:<puuid>:<Filter.toSummonerKey()>`;
- champion stats matrix key: `champion-stats-matrix:<patch>:<queue>`;
- recent champion stats key: `champion-stats-recent:<queue>:<oldestPatch>,<middlePatch>,<newestPatch>`;
- individual champion stats persistence key: `Filter.genericKey()`;
- champion build key: `champion-build:<Filter.toKey()>`;
- scheduled champion refresh key: `champion-data-refresh:<patch>`;
- the latest three patch matrices for one queue are chained from oldest to newest, so each newer aggregate can read its previous-patch trend from storage;
- the complete filter is snapshotted before it is queued;
- worker failures are isolated to their task;
- `DatabaseTracker.shutdown()` stops workers before Mongo closes.

## Rejected alternatives

- A virtual-thread-per-refresh executor allows too many database scans to run concurrently.
- Removing deduplication at dequeue time allows a duplicate request to overlap the existing calculation.
- Moving match/R4J work into this queue would merge two different rate-limit and persistence flows.

## Acceptance criteria

- no more than two database calculations execute at once;
- champion calculations never execute concurrently with another champion calculation;
- profile calculations may run on either worker, but never exceed two database calculations overall;
- duplicate queued or running keys share one future and one calculation;
- a failed key can be submitted again;
- scheduled champion refreshes cannot overlap for the same patch;
- HTTP status and payload contracts remain unchanged.
