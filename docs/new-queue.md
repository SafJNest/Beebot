# Hive job runtime

`com.hive` owns the JDK-only mechanism. The application facade
`lol.queue.QueueHandler` explicitly composes one runtime from App before opening
API/tracker admission. Registry and Router are per-instance; the monolith
shares one registry across Riot, compute and Sync. A scheduler owns its own
physical queues and workers. Sync uses direct execution without a physical queue.
[ADR-0017](architecture/adr/0017-hive-job-runtime.md) defines these boundaries.

```text
QueueHandler -> Router -> Registry -> AbstractScheduler<R>
                                      JobQueue -> JobWorker -> Job
```

`HiveConfig` supplies identity, clock, snapshot limits, shutdown timeout and
observation. Scheduler hooks supply route validation, labels, placement and
execution mode. Public submission relates the scheduler route type and route,
and uses a result type token to reject incompatible active-key reuse.

`Job` is the submission data (`PID`, `PPID`, scheduler class, key, name,
logical route, priority, work and progress). `Registry` owns lifecycle,
descendants, futures and follower deduplication; the physical scheduler owns
queue placement and workers.
`QueueHandler.immediate`, `.normal` and `.background` submit it. Priorities are
`IMMEDIATE`, `NORMAL` and `BACKGROUND`; a running job body is never interrupted.

| Dispatcher | Routes | Owner |
| --- | --- | --- |
| `RiotScheduler` | one `LeagueShard` worker | outbound Riot API |
| `ComputeScheduler` | `PROFILE`, `PROFILE_2`, `CHAMPION`, `MONGO` | expensive Mongo compute |
| `SyncScheduler` | starts every submitted job immediately on its own virtual thread | tracking, rank, match, sample and participant refresh workflows |

Riot and Compute keep their route-local priority queues. Sync has no waiting
queue or per-shard worker limit: each submitted job starts immediately and may
run alongside other Sync jobs. Sync parents and their descendants remain
tracked by the Registry until the full job tree completes. Riot API requests
created by those jobs still pass through RiotScheduler's per-shard priority
queues and rate protection.

`runs` is only a derived compatibility view for live Sync roots (`TRACKING`,
`RANK_ENTRIES`, `MATCH_ANALYSIS`, `SAMPLE_GAMES`). It
is derived from submitted child jobs and disappears when its root completes.
It is not persisted. A task can
report `phase`, `progress` and a compact `itemId -> PENDING|DONE|MISSING|FAILED`
map plus optional `itemLabels` (for example `puuid -> riotId`) while it runs;
the run exposes those child job snapshots without a parallel Tracker telemetry
store. A job body can call `QueueHandler.retain(job)` and later
`QueueHandler.resume(job, callback)` so the worker is released while the logical
parent waits for the callback and its descendants.

Explicit item progress wins over direct-child progress; child counters are the
fallback. Priority inheritance selects the more urgent parent/request priority.
Compute profile placement selects the least loaded of its four workers at
insertion. While champion matrix/build/refresh work reserves CHAMPION, profile
work chooses only PROFILE or PROFILE_2. Heavy work and all filter/key/merge
interpretation remain in the concrete `lol.scheduler` adapter. Riot lives in
`r4j.scheduler`, with existing SDK protection and external-cache ownership.

Future cancellation does not automatically interrupt a job body. Explicit job
cancellation and runtime shutdown close logical lifecycle separately from
executor interruption. Shutdown closes admission and owned executors; bodies
ignoring interruption cannot promise immediate physical termination.

## Match flow

```text
OP.GG missing match -> Sync/IMMEDIATE/shard -> Riot -> cache + Mongo + participant seed
API missing match   -> Sync/BACKGROUND/shard -> Riot -> cache + Mongo + participant seed
tracker/sample/import -> Sync/BACKGROUND/shard -> Mongo persistence
```

Redis is cache only: it is not a queue, backlog or retry store. There is no
match poller, no pending set, and no restart recovery for Sync tasks.

Match ingestion has two levels. Raw ingestion persists the canonical match,
events and participant identity seed with the internal Mongo marker
`tracked=false`; it does not refresh participant ranks. Tracked enrichment
invalidates Redis and R4J rank caches and obtains every current
participant rank directly from Riot before updating `summoner.ranks{}`, Redis
and the match. Mongo participant history and the embedded Mongo rank remain
baseline-only inputs for LP gain; they never supply the participant's current
rank. The complete match is then persisted with `tracked=true`. The marker is
storage-only and does not alter the HTTP `Match` contract or Discord
presentation.

`%test fix-tracked` is a Mongo-only recovery operation for summoners with
`tracking=true`: it rebuilds their stored Solo/Duo RankProgress history and
marks every repaired ranked match `tracked=true`, so the scheduled tracker does
not enqueue that recovered history again.

The scheduled tracked-summoner flow waits for its latest ranked match to finish
enrichment; its second Riot match is raw-persisted only as a history reference.
The LP predecessor is selected from Mongo by PUUID, shard, Solo/Duo queue and
the latest earlier `timeStart`, always excluding the current full match ID.
OP.GG remains responsive: displayed cards are raw-persisted immediately, then a
best-effort current `{ rank, lp }` snapshot is written for each participant.
Missing snapshots start the same deduplicated background rank refresh used by
the tracker. OP.GG never writes gain or predecessor data; only `tracked=true`
commits the authoritative history.

After the final `tracked=true` persistence, the scheduled tracked-summoner flow
emits one `LPTracker` success log for that summoner. Raw ingestion and
already-tracked matches do not emit this log.

The periodic tracker starts its next run ten minutes after the current root and
all its descendants finish. Other periodic jobs keep their own schedules.

Async domain continuations use the runtime-owned continuation executor, with
retained/restored job context. New background work enters the submission facade.
Orchestrators that wait for compute children run on Sync, avoiding a join onto
the same occupied serial compute worker.

Rank entries are scheduled as real leaf tasks before execution: one task for
each `shard + tier + queue`. The derived `runs[].tasks` can therefore be grouped by
task `route` to render the seven shard groups and their pending/running/done
rank jobs without parsing task names.
