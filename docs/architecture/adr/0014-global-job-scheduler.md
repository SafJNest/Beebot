# ADR-0014: Global job scheduler

Status: accepted

The LoL runtime exposes one submission API: `QueueHandler.immediate`,
`QueueHandler.normal` and `QueueHandler.background`. A `Job` is registered before it is routed, receives a
monotonic process-local PID and its PPID from the current job context. The
registry is authoritative for lifecycle, descendants and active deduplication
by `(type, logical route, key)`; it is not a shared physical queue.

`Router.register(AbstractScheduler)` maps each scheduler class to exactly one
scheduler. Duplicate types and unknown types fail explicitly; routing has no
central type switch. `AbstractScheduler<R>` remains the shared physical queue
and worker implementation for `RiotScheduler`, `ComputeScheduler` and
`SyncScheduler`.

Each job body receives its own `Job`, including progress. The registry, not the
job, owns the lifecycle entry, child count, future, result and follower link. A body
can finish and release its physical worker while its job is `WAITING_CHILDREN`.
Execution failures before the body starts also fail the job body, with future
completion deferred until its registered children terminate. The job future
completes only after all descendants are terminal. Explicit async callbacks
retain and restore their parent with `QueueHandler.retain(job)` and
`QueueHandler.resume(job, callback)`. Child
failures do not cancel siblings; they yield `COMPLETED_WITH_ERRORS` on an
otherwise successful parent. A reused active job creates a follower PID with
`followingPid`, rather than another physical task.

Compute has physical `PROFILE` and `CHAMPION` workers. Profile work is placed
on the less-loaded worker only while champion has no queued or running
statistics matrix, build, or full champion-data refresh. Those heavy champion
tasks reserve the `CHAMPION` worker: later profile work stays on `PROFILE`,
regardless of its current load. Already placed profile tasks are never moved;
once the heavy champion work completes, normal insert-time balancing resumes.

Sync is volatile by design. It owns no Redis backlog, pending set or retry map.
Each submitted Sync job starts immediately on its own virtual thread, without a
per-shard Sync queue or worker limit. `SYNC + null` remains the logical
global/root route; its public route remains `null`. Parent/child completion is
owned by Registry. Riot API calls created by Sync jobs still use RiotScheduler's
per-shard priority queues. Missing API matches are background Sync work; OP.GG
match loading is immediate Sync work.

`/api/status.jobs` is the registry view. Dispatcher snapshots expose Riot and
Compute queues/workers; Sync lifecycle appears in `jobs` without a physical
queue/worker projection. Task snapshots include PID/PPID. `runs` remains only a
derived compatibility projection for tracking, sample-game and rank-entry
roots. A job remains in memory while its body, an explicit async callback or at
least one child is active. It is removed as soon as its full subtree is terminal;
no Redis, MongoDB or pub/sub persistence is involved.

The HTTP `jobs` projection includes three complete levels and at most 100 jobs
from the fourth level, ordered by priority and enqueue time. It omits per-item
progress maps. This keeps tracker and all-rank-entry status payloads bounded
while the registry retains the full active tree and item state.

Progress is registry-owned: leaf jobs report their own items; every parent
reports terminal direct children over direct children created, including
completed children that have already been removed from the registry.

Priority is registry-owned and deduplicated work is promotable. A later request
with a more urgent priority promotes the queued source job in its existing
scheduler route and moves it to the front of that lane; the new caller remains
a follower of the same future. Parent/child links describe status ownership,
not a priority ceiling: an `IMMEDIATE` child may exceed a background parent.
Running work is never preempted or duplicated.
