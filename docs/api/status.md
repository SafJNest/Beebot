# Scope: status — Bot metrics

## Endpoint

`GET /api/status`

Endpoint operativo del processo BeeBot. Non è shard-scoped e non appartiene
a `/api/lol/{shard}`.

## Fetch

```bash
curl 'http://localhost:8080/api/status'
```

## Parametri

Nessuno.

## Risposta `200`

`BotStatus`. La request aggrega solo snapshot e contatori già in memoria:
nessuna chiamata Riot, nessun `countDocuments()` Mongo, nessuna attesa per
campionare CPU o network.

CPU totale/per-core e i byte/s di rete arrivano da `SystemMetricsSampler`
(tick da 1 secondo). Le metriche League sono snapshot in memoria aggiornati
dal sampler: se la chiave Redis ha TTL valido viene letta da lì, altrimenti
viene eseguita la query sorgente (Redis `SCARD`, `estimatedDocumentCount` o
aggregazione Mongo) e il valore viene riscritto in cache. `profileQueue` resta
live da `DatabaseTracker.profileQueueSize()`.

`tracker` espone lo stato del `TrackerScheduler` (tracking LP, high elo,
game analysis, sample games e pending games) con progresso in memoria aggiornato
dai job stessi. `totalSummoners` nel tracking è il conteggio Mongo
`summoner.tracking=true`, cache Redis ogni ~10 minuti. Durante il job LP
(`tracking.state=running`), `tracking.summoners` elenca tutti i tracked con
`done=true|false` nell'ordine in cui verranno analizzati (stesso ordine del
loop Mongo → Riot). Fuori dal job resta
`null`. Durante il job high elo (`highElo.state=running`), `highElo.steps`
elenca ogni combinazione tier/shard/queue con `done`; `tier`/`shard`/`queue`
in cima indicano lo step corrente. Fuori dal job restano `null`. `sampleGames.nextRunAt`
resta `null` finché il job non è schedulato (oggi manuale via owner `test pushsamplegame`).

`workers` espone lo snapshot canonico live dei due worker `DatabaseTracker`
(`profile` e `champion`): stato, job corrente, progresso `current/total`,
conteggio in-flight e fino a 20 nomi in coda. `queuedCount` resta il conteggio
completo.

`riot` espone lo stesso snapshot canonico di `R4JQueue`: un worker per shard
Riot, con job corrente, progresso, conteggio in-flight e fino a 20 nomi in
coda. `queuedCount` resta il conteggio completo e `totalInFlight` somma le
richieste Riot non completate su tutti gli shard. I worker compaiono solo dopo
la prima richiesta su quello shard; finché uno shard non è stato usato non
compare in `queues[]`.

Il comando owner `tracker` legge lo stesso `BotStatus`: non ricostruisce né
interroga separatamente lo stato di scheduler o worker.

Memoria, disco e network sono in byte. La CPU è una percentuale `0`–`100`.
`uptime` è in millisecondi. Sezioni assenti restano `null` invece di far
fallire l'endpoint. Non c'è `metadata`.

```json
{
  "status": "online",
  "league": {
    "gameQueue": 4,
    "profileQueue": 12,
    "gamesAnalyzed": 18439201,
    "totalSummoners": 523891,
    "totalMasteries": 4123901,
    "ranksByQueue": {
      "RANKED_SOLO_5X5": 401203,
      "RANKED_FLEX_SR": 122688
    }
  },
  "tracker": {
    "scheduler": "running",
    "tracking": {
      "state": "running",
      "nextRunAt": 1755680400000,
      "totalSummoners": 142,
      "progress": { "current": 37, "total": 142 },
      "summoners": [
        { "puuid": "abc-123", "riotId": "Alpha#EUW", "shard": "EUW1", "done": true },
        { "puuid": "def-456", "riotId": "Beta#EUW", "shard": "EUW1", "done": false }
      ]
    },
    "highElo": {
      "state": "running",
      "nextRunAt": 1755684000000,
      "tier": "MASTER_I",
      "shard": "EUW1",
      "queue": "RANKED_SOLO_5X5",
      "progress": { "current": 12, "total": 84 },
      "steps": [
        { "tier": "MASTER_I", "shard": "EUW1", "queue": "RANKED_SOLO_5X5", "done": true },
        { "tier": "MASTER_I", "shard": "EUW1", "queue": "RANKED_FLEX_SR", "done": false }
      ]
    },
    "gameAnalysis": {
      "state": "idle",
      "nextRunAt": 1755648000000,
      "progress": null
    },
    "sampleGames": {
      "state": "running",
      "nextRunAt": null,
      "queue": "TEAM_BUILDER_RANKED_SOLO",
      "regions": [
        { "shard": "EUW1", "state": "analyzing", "total": 50, "analyzed": 5, "remaining": 45 }
      ]
    },
    "games": {
      "pendingGames": 0,
      "matchLookups": 0,
      "nextMatchLookupAt": 1755680420000
    }
  },
  "workers": {
    "workers": [
      {
        "id": 1,
        "type": "profile",
        "state": "idle",
        "currentJob": null,
        "currentStartedAt": null,
        "progress": null,
        "queuedCount": 3,
        "inFlight": 3,
        "queuedJobs": []
      }
    ]
  },
  "riot": {
    "totalInFlight": 12,
    "queues": [
      {
        "shard": "EUW1",
        "state": "running",
        "currentJob": "EUW1:match:abc-123",
        "currentStartedAt": 1755680382000,
        "progress": { "current": 4, "total": 12 },
        "queuedCount": 11,
        "inFlight": 12,
        "queuedJobs": [
          "EUW1:summoner:def-456",
          "EUW1:match:ghi-789"
        ]
      }
    ]
  },
  "process": {
    "cpu": 13.7,
    "memory": {
      "used": 1248231424,
      "committed": 2147483648,
      "max": 4294967296
    },
    "threads": 87,
    "peakThreads": 120,
    "uptime": 8329401
  },
  "system": {
    "cpu": {
      "usage": 32.4,
      "cores": 6,
      "perCore": [22.3, 17.5, 45.1, 38.7, 31.2, 39.8]
    },
    "memory": {
      "used": 10485760000,
      "available": 6291456000,
      "total": 16777216000
    },
    "disk": {
      "used": 53687091200,
      "available": 32212254720,
      "total": 85899345920
    },
    "network": {
      "receivedBytesPerSecond": 1823912,
      "sentBytesPerSecond": 728391
    }
  },
  "redis": {
    "keys": 163842,
    "memoryUsed": 428392448
  }
}
```

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Snapshot disponibile. Redis o una metrica di sistema possono essere `null`. |
| `404` | `not_found` | Endpoint non trovato. |
| `405` | `method_not_allowed` | Metodo diverso da `GET`. |
| `500` | `internal_error` | Errore inatteso del dispatcher. |

## Owner

- Controller: [`StatusController`](../../src/main/java/com/safjnest/spring/controller/StatusController.java)
- Service: [`StatusService`](../../src/main/java/com/safjnest/status/StatusService.java)
- Sampler: [`SystemMetricsSampler`](../../src/main/java/com/safjnest/status/SystemMetricsSampler.java)
- Tracker snapshot: [`TrackerMetricsStore`](../../src/main/java/com/safjnest/status/TrackerMetricsStore.java) sopra lo stato tracker-owned [`TrackerJobProgress`](../../src/main/java/com/safjnest/lol/tracker/TrackerJobProgress.java)
- Queue snapshot: [`QueueWorkerStatus`](../../src/main/java/com/safjnest/lol/model/status/QueueWorkerStatus.java)
- Success model: [`BotStatus`](../../src/main/java/com/safjnest/lol/model/status/BotStatus.java)
