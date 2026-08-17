# Scope: summoner — Activity

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/activity`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/activity' \
  --data-urlencode 'start=1715731200000' \
  --data-urlencode 'end=1718323200000' \
  --data-urlencode 'queue=ALL' \
  --data-urlencode 'champion=0'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard del profilo. |
| `puuid` | path | string | sì | — | PUUID Riot canonico del summoner. |
| `start` | query | epoch millis | no | `0` | Inizio del periodo; `0` significa nessun limite inferiore. |
| `end` | query | epoch millis | no | `0` | Fine del periodo; `0` significa nessun limite superiore. |
| `queue` | query | enum `GameQueueType` oppure `ALL` | no | `ALL` | Queue da filtrare; `ALL` viene normalizzato a nessun filtro queue. |
| `champion` | query | integer | no | `0` | Champion del summoner; `0` significa tutti. |

`start` e `end` devono essere positivi o `0`; quando sono entrambi presenti,
`end` non può precedere `start`. Il filtro interno usa `Filter.summoner(start,
end)` e aggiunge soltanto queue e champion.

La response contiene tutte le sessioni del periodo in un unico payload. Non
usa cursor, limit, offset o timezone nel contratto HTTP.

## Risposta `200`

`ProfileActivity`. I timestamp sono epoch Unix in millisecondi. `heatmap.cells`
contiene sempre 168 celle in ordine `day * 24 + hour`: `day=0` è Monday,
`day=6` è Sunday, mentre `hour` va da `0` a `23`. Le celle senza partite hanno
`games=0` e `winrate=null`.

```json
{
  "filter": {
    "timeStart": 1715731200000,
    "timeEnd": 1718323200000,
    "champion": 0,
    "lane": null,
    "queue": null,
    "rank": null,
    "rankBehavior": "GREATER_OR_EQUAL",
    "patch": null,
    "region": null,
    "opponent": 0,
    "duo": 0
  },
  "coverage": {
    "games": 142,
    "oldestMatchAt": 1715731200000,
    "newestMatchAt": 1718323200000,
    "calculatedAt": 1718323200000
  },
  "summary": {
    "games": 142,
    "wins": 73,
    "losses": 69,
    "winrate": 51.4,
    "gamesPerDay": 3.6,
    "mostActiveDay": {
      "day": 3,
      "games": 28,
      "wins": 15,
      "losses": 13,
      "share": 19.72,
      "winrate": 53.57
    },
    "bestWinrateSlot": {
      "rank": 1,
      "day": 2,
      "startHour": 19,
      "endHour": 20,
      "games": 7,
      "wins": 5,
      "losses": 2,
      "winrate": 71.43
    },
    "favoriteQueue": {
      "queue": "TEAM_BUILDER_RANKED_SOLO",
      "games": 102,
      "wins": 52,
      "losses": 50,
      "share": 71.83,
      "winrate": 50.98
    },
    "sessionCount": 48,
    "averageSessionDurationMs": 5760000,
    "sessionDurationStdDevMs": 2520000
  },
  "heatmap": {
    "days": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
    "hours": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23],
    "cells": [
      {"day": 0, "hour": 0, "games": 0, "wins": 0, "losses": 0, "winrate": null},
      {"day": 0, "hour": 1, "games": 1, "wins": 0, "losses": 1, "winrate": 0.0}
    ]
  },
  "bestTimeWindows": [
    {
      "rank": 1,
      "day": 2,
      "startHour": 19,
      "endHour": 20,
      "games": 7,
      "wins": 5,
      "losses": 2,
      "winrate": 71.43
    }
  ],
  "dailyActivity": [
    {"day": 0, "games": 16, "wins": 8, "losses": 8, "share": 11.27, "winrate": 50.0}
  ],
  "hourlyTrend": [
    {"hour": 19, "games": 15, "wins": 9, "losses": 6, "share": 10.56, "winrate": 60.0}
  ],
  "queueActivity": [
    {
      "queue": "TEAM_BUILDER_RANKED_SOLO",
      "games": 102,
      "wins": 52,
      "losses": 50,
      "share": 71.83,
      "winrate": 50.98
    }
  ],
  "recentSessions": [
    {
      "start": 1718324100000,
      "end": 1718330100000,
      "durationMs": 6000000,
      "games": 3,
      "wins": 2,
      "losses": 1,
      "winrate": 66.67,
      "queues": ["TEAM_BUILDER_RANKED_SOLO"],
      "championIds": [157, 238]
    }
  ],
  "insights": [
    {
      "code": "MOST_ACTIVE_DAY",
      "values": {"day": "THURSDAY", "games": 28, "share": 19.72}
    },
    {
      "code": "BEST_TIME_SLOT",
      "values": {"day": "WEDNESDAY", "hour": 19, "games": 7, "winrate": 71.43}
    }
  ]
}
```

Le sessioni sono raggruppate in un'unica scansione ordinata dei match: una
nuova sessione inizia quando il distacco tra la fine del match precedente e
l'inizio del successivo supera 90 minuti. `recentSessions` è ordinato dalla
sessione più recente alla più vecchia.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Response activity calcolata; può contenere liste vuote se non esistono match. |
| `202` | `profile_activity_pending` | Activity assente; il job on-demand è accodato. |
| `400` | `invalid_request` | Parametro temporale, queue o champion non valido. |

Ogni `200` include `metadata` root con il filtro richiesto e `lastUpdate` preso
da `coverage.calculatedAt`. Se il valore è stale, la risposta resta `200` con
`refresh=true` e viene accodato solo il refresh activity in bassa priorità. Il
`202` usa lo stesso oggetto nel `LolApiError`, con `refresh=true`.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Success model: [`ProfileActivity`](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileActivity.java)
- Match query: [`MongoDB.findProfileStatisticsMatches`](../../../src/main/java/com/safjnest/nosql/MongoDB.java)
- Persistence: Redis `SUMMONER_ACTIVITY(puuid, filterKey)` e collection Mongo `profile_activity` con la stessa identità `{ puuid, filterKey }`.
