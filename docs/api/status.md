# Scope: status — Bot metrics

`GET /api/status` returns cached counters plus live snapshots. It performs no
Riot request and no persistent match-queue read.

`league` contains only persistent counters. All live backlog is under
`dispatchers`; the obsolete `tracker`, `workers`, `riot`, `gameQueue` and
`profileQueue` fields were removed.

Each dispatcher reports its routes, the single worker for each route and its
current task plus up to twenty queued tasks. `runs` is populated only by live
Sync batches and vanishes after the final child finishes.

```json
{
  "status": "online",
  "league": {
    "gamesAnalyzed": 18439201,
    "totalSummoners": 523891,
    "totalMasteries": 4123901,
    "ranksByQueue": { "RANKED_SOLO_5X5": 401203 }
  },
  "dispatchers": [
    {
      "id": "sync",
      "queues": [{
        "route": "EUW1",
        "worker": {
          "id": 1,
          "state": "RUNNING",
          "currentTask": {
            "key": "match:EUW1:EUW1_6789012345",
            "name": "match analysis id=EUW1_6789012345",
            "route": "EUW1",
            "priority": "BACKGROUND",
            "state": "RUNNING",
            "runId": "uuid",
          "queuedAt": 1755680400000,
            "startedAt": 1755680400123,
            "phase": "PERSISTING",
            "progress": { "current": 100, "total": 1000 },
            "items": { "EUW1_6789012345": "DONE", "EUW1_6789012346": "PENDING" }
          },
          "queuedCount": 1,
          "inFlight": 2,
          "queuedTasks": []
        }
      }],
      "runs": [{
        "id": "uuid",
        "type": "TRACKING",
        "state": "RUNNING",
        "queuedAt": 1755680400000,
        "startedAt": 1755680400123,
        "progress": { "current": 1, "total": 7 },
        "tasks": []
      }]
    },
    { "id": "riot", "queues": [], "runs": [] },
    { "id": "compute", "queues": [], "runs": [] }
  }
}
```

The owner command `tracker` renders this same dispatcher snapshot. Sync tasks
are memory-only and do not reappear after a restart.
