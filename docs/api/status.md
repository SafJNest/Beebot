# Scope: status — Bot metrics

`GET /api/status` returns cached counters plus live snapshots. It performs no
Riot request and no persistent match-queue read.

`league` contains only persistent counters. `jobs` is the authoritative live
job tree; `dispatchers` is the physical queue/worker projection. The obsolete
`tracker`, `workers`, `riot`, `gameQueue` and `profileQueue` fields were removed.

Each scheduler reports its routes, the single worker for each route and its
current job plus up to twenty queued jobs. Job snapshots include `pid` and
`ppid`. `runs` is a compatibility projection derived from active registry roots
for tracking, sample games and rank entries; it is not lifecycle state.

Each `jobs` item has `pid`, `ppid`, scheduler `type`, logical `route`, priority
and lifecycle state. Physical placement belongs to `dispatchers.queues[].worker`.
`children` contains only PIDs of currently active children;
resolve their details against the same `jobs` array. A duplicate active job
has `followingPid` and no physical queue entry. The registry and status output
contain only active jobs: a job disappears as soon as its body and all its
children have terminated.

The top-level `jobs` projection includes every job in the first three levels from
a root, then at most 100 fourth-level jobs ordered by priority and enqueue time.
It exposes phase and aggregate `progress`, but never per-item `items` or `itemLabels`.
For example, `rank-entries` exposes its regional/tier job and `500/1000`
progress without serializing the thousand summoner PUUIDs.

A leaf job reports its own item progress. A parent reports terminal direct
children over direct children created, including children that already left the
registry. A root that starts two children reports `0/2`; if both children each
start two further jobs, the root remains `0/2` while each child reports `0/2`.

A nested job cannot exceed its parent priority: `IMMEDIATE` requested from a
`BACKGROUND` job remains `BACKGROUND`; `IMMEDIATE` requested from `NORMAL`
remains `NORMAL`.

`mongo` is sampled every second in background by `SystemMetricsSampler`. A
`GET /api/status` read returns the latest in-memory snapshot; it does not
recompute metrics on demand.

## Mongo metrics

### Operations (`mongo.operations`)

Server-side ops/sec aligned with Atlas categories, from client driver counters
(excluding internal commands such as `serverStatus`). If client counters are
unavailable on the first sample, the sampler falls back to `serverStatus.opcounters`
deltas.

| Field | Meaning |
|---|---|
| `intervalSeconds` | Sample interval (1) |
| `current` | Latest ops/sec bucket |
| `series` | Rolling history, up to 300 points (5 minutes) |

Each rate object contains `insert`, `query`, `update`, `delete`, `command`,
`getmore` and `total` (sum).

### Performance (`mongo.performance`)

Client-side metrics from the Mongo `CommandListener`: latency, hottest
collections and slowest operations.

| Field | Window | Meaning |
|---|---|---|
| `hottestNow` | last completed 1s bucket | collection with most ops in that second; `null` when idle |
| `hottestRecent` | last 10s | collection with highest average ops/sec; decays after spikes |
| `recentWindowSeconds` | constant 10 | |
| `slowWindowSeconds` | constant 300 | |
| `avgMsByCommand` | fino a 5 min di attività | average round-trip ms per wire command; **svuotato dopo 10s idle** |
| `collections` | fino a 5 min di attività | per-collection count, avgMs, maxMs; **svuotato dopo 10s idle** |
| `slowest` | **ultimi 10s** | top 10 per durata; ogni riga include `query` (comando Mongo JSON) |

`hottestNow` captures instant spikes (e.g. champion analysis hammering `match`).
`hottestRecent` stays elevated for up to 10 seconds after the spike, then
returns to `null`. After **10 seconds**, `slowest` entries drop out automatically because each row
carries its own `at` timestamp. `collections` and `avgMsByCommand` use the
5-minute sliding window instead.

`operations.current` drops to **0** at idle because rates use client counters
that exclude the periodic `serverStatus` poll. `operations.series` keeps up to
5 minutes of samples, including recent zero buckets.

### Server snapshot (`mongo.connections`, `mongo.memory`)

From `serverStatus` when available:

- `connections`: current open connections to MongoDB
- `memory.residentMb`, `memory.virtualMb`: mongod process memory

Requires a Mongo user with permission to run `serverStatus` on `admin` (e.g.
Atlas `clusterMonitor`). When denied, these fields stay `null` while
`mongo.performance` remains populated.

## Example

```json
{
  "status": "online",
  "league": {
    "gamesAnalyzed": 18439201,
    "totalSummoners": 523891,
    "totalMasteries": 4123901,
    "ranksByQueue": { "RANKED_SOLO_5X5": 401203 }
  },
  "jobs": [{
    "pid": 101,
    "ppid": 0,
    "type": "SyncScheduler",
    "key": "tracking",
    "name": "tracking summoners",
    "route": null,
    "priority": "IMMEDIATE",
    "state": "WAITING_CHILDREN",
    "followingPid": null,
    "queuedAt": 1755680400000,
    "startedAt": 1755680400001,
    "completedAt": null,
    "phase": "TRACKING",
    "progress": null,
    "items": {},
    "itemLabels": {},
    "children": [102]
  }],
  "dispatchers": [
    {
      "id": "sync",
      "queues": [{
        "route": "EUW1",
        "worker": {
          "id": 1,
          "state": "RUNNING",
          "currentJob": {
            "pid": 102,
            "ppid": 101,
            "type": "SyncScheduler",
            "key": "match:EUW1:EUW1_6789012345",
            "name": "match analysis id=EUW1_6789012345",
            "route": "EUW1",
            "priority": "BACKGROUND",
            "state": "RUNNING",
            "followingPid": null,
            "queuedAt": 1755680400000,
            "startedAt": 1755680400123,
            "completedAt": null,
            "phase": "PERSISTING",
            "progress": { "current": 100, "total": 1000 },
            "items": {},
            "itemLabels": {},
            "children": []
          },
          "queuedCount": 1,
          "inFlight": 2,
          "queuedJobs": []
        }
      }],
      "runs": [{
        "id": "101",
        "type": "TRACKING",
        "state": "RUNNING",
        "queuedAt": 1755680400000,
        "startedAt": 1755680400123,
        "progress": { "current": 1, "total": 7 },
        "jobs": []
      }]
    },
    { "id": "riot", "queues": [], "runs": [] },
    { "id": "compute", "queues": [], "runs": [] }
  ],
  "process": { "cpu": 12.5, "memory": { "used": 1, "committed": 2, "max": 3 }, "threads": 42, "peakThreads": 48, "uptime": 3600000 },
  "system": { "cpu": { "usage": 25.0, "cores": 8, "perCore": [10.0, 20.0] }, "memory": { "used": 1, "available": 2, "total": 3 }, "disk": { "used": 1, "available": 2, "total": 3 }, "network": { "receivedBytesPerSecond": 1000, "sentBytesPerSecond": 500 } },
  "redis": { "keys": 100, "memoryUsed": 2048 },
  "mongo": {
    "operations": {
      "intervalSeconds": 1,
      "current": { "insert": 0.0, "query": 12.4, "update": 1.1, "delete": 0.0, "command": 0.3, "getmore": 0.8, "total": 14.6 },
      "series": [{ "at": 1755680400000, "rates": { "insert": 0.0, "query": 12.4, "update": 1.1, "delete": 0.0, "command": 0.3, "getmore": 0.8, "total": 14.6 } }]
    },
    "performance": {
      "hottestNow": { "name": "match", "opsPerSecond": 82.0, "ops": 82 },
      "hottestRecent": { "name": "match", "opsPerSecond": 41.5, "ops": 415 },
      "recentWindowSeconds": 10,
      "slowWindowSeconds": 300,
      "avgMsByCommand": { "find": 12.3, "aggregate": 89.0 },
      "collections": [{ "name": "match", "count": 120, "avgMs": 45.0, "maxMs": 890 }],
      "slowest": [{
        "command": "aggregate",
        "collection": "match",
        "durationMs": 890,
        "at": 1755680400123,
        "query": {
          "aggregate": "beebot.match",
          "pipeline": [{ "$match": { "region": "EUW1" } }, { "$limit": 1000 }]
        }
      }]
    },
    "connections": 5,
    "memory": { "residentMb": 259, "virtualMb": 3710 }
  }
}
```

The owner command `tracker` renders the dispatcher snapshot only. Sync tasks
are memory-only and do not reappear after a restart.
