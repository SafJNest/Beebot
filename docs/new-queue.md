# Request dispatcher

`lol.queue` owns one shared request infrastructure and three separate dispatcher
owners. A dispatcher never shares workers, routes, limits, in-flight tasks or
deduplication with another dispatcher.

```text
AbstractRequestDispatcher<R>
  RequestQueue<R> -> RequestWorker<R> -> RequestTask<R, T>
```

`Request` is the immutable submission (`key`, `name`, route, priority and
supplier). `RequestTask` is its deduplicated in-flight instance. Priorities are
`IMMEDIATE`, `NORMAL` and `BACKGROUND`; a running task is never interrupted.

| Dispatcher | Routes | Owner |
| --- | --- | --- |
| `RiotRequestDispatcher` | one `LeagueShard` worker | outbound Riot API |
| `ComputeRequestDispatcher` | `PROFILE`, `CHAMPION` | expensive Mongo compute |
| `SyncRequestDispatcher` | one `LeagueShard` worker | tracking, rank, match, sample and participant refresh workflows |

Every route has its own three-lane physical queue. An immediate EUW task can
overtake background EUW work, but cannot reorder or block NA work.

`RequestRun` is only live Sync batch state (`TRACKING`,
`RANK_ENTRIES`, `MATCH_ANALYSIS`, `SAMPLE_GAMES`). It
references its submitted child tasks, reuses an active logical run, and
disappears when its final child completes. It is not persisted. A task can
report `phase`, `progress` and a compact `itemId -> PENDING|DONE|MISSING|FAILED`
map plus optional `itemLabels` (for example `puuid -> riotId`) while it runs;
the run exposes those task snapshots without a parallel
Tracker telemetry store.

## Match flow

```text
OP.GG missing match -> Sync/IMMEDIATE/shard -> Riot -> cache + Mongo + participant seed
API missing match   -> Sync/BACKGROUND/shard -> Riot -> cache + Mongo + participant seed
tracker/sample/import -> Sync/BACKGROUND/shard -> Mongo persistence
```

Redis is cache only: it is not a queue, backlog or retry store. There is no
match poller, no pending set, and no restart recovery for Sync tasks.

`thenApplyAsync` and `thenComposeAsync` in a domain service remain continuations
of an already-owned request; new background work must enter one of the three
dispatchers.

Rank entries are scheduled as real leaf tasks before execution: one task for
each `shard + tier + queue`. `RequestRun.tasks` can therefore be grouped by
task `route` to render the seven shard groups and their pending/running/done
rank jobs without parsing task names.
