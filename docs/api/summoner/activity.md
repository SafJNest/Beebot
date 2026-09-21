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

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Shard of the profile. |
| `puuid` | path | string | yes | — | Canonical Riot PUUID of the summoner. |
| `start` | query | epoch millis | no | `0` | Explicit period start; if both `start` and `end` are `0`, the canonical season is used. |
| `end` | query | epoch millis | no | `0` | Explicit period end; if present alone it leaves the lower bound open. |
| `queue` | query | enum `GameQueueType` or `ALL` | no | `ALL` | Queue to filter; `ALL` is normalized to no queue filter. |
| `champion` | query | integer | no | `0` | Summoner champion; `0` means all. |

`start` and `end` must be positive or `0`; when both are present,
`end` must not be before `start`. Without time bounds the internal filter uses
`Filter.canonical()`; with at least one bound it uses `Filter.summoner(start, end)` and
adds only queue and champion.

The response contains all sessions for the period in a single payload. It does not
use cursor, limit, offset or timezone in the HTTP contract.

## `200` response

`ProfileActivity`. Timestamps are Unix epoch in milliseconds. `heatmap.cells`
always contains 168 cells in `day * 24 + hour` order: `day=0` is Monday,
`day=6` is Sunday, while `hour` ranges from `0` to `23`. Cells without matches have
`games=0` and `winrate=null`.

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

Sessions are grouped in a single ordered scan of matches: a
new session starts when the gap between the end of the previous match and
the start of the next exceeds 90 minutes. `recentSessions` is ordered from the
most recent session to the oldest.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Computed activity response; may contain empty lists if no matches exist. |
| `202` | `profile_activity_pending` | Activity missing; on-demand job has been queued. |
| `400` | `invalid_request` | Invalid time, queue or champion parameter. |

Every `200` includes root `metadata` with the requested filter and `lastUpdate` taken
from `coverage.calculatedAt`. If the value is stale, the response remains `200` with
`refresh=true` and only the low-priority activity refresh is queued. The
`202` uses the same object in the `LolApiError`, with `refresh=true`.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Success model: [`ProfileActivity`](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileActivity.java)
- Match query: [`MongoDB.findProfileStatisticsMatches`](../../../src/main/java/com/safjnest/nosql/MongoDB.java)
- Persistence: Redis `SUMMONER_ACTIVITY(puuid, filterKey)` and Mongo collection `profile_activity` with the same `{ puuid, filterKey }` identity.
