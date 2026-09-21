# Scope: summoner — Matchups

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/matchups`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/matchups' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'patch=14.10' \
  --data-urlencode 'role=BOT' \
  --data-urlencode 'minGames=5'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Shard of the profile. |
| `puuid` | path | string | yes | — | Canonical Riot PUUID of the summoner. |
| `start` | query | epoch millis | no | `0` | Period start; if `end` is missing, the end of the current day is used (`23:59:59.999`). When present, it takes precedence over `patch`. |
| `end` | query | epoch millis | no | `0` | Period end; can be used alone and must be greater than or equal to `start` when `start` is present. |
| `queue` | query | enum `GameQueueType` or `ALL` | no | `ALL` | Queue to filter; omitting it and `ALL` aggregate all queues. |
| `patch` | query | `major.minor` | no | no filter | Fallback when both `start` and `end` are absent; if the period is present it is ignored. |
| `role` | query | enum `LaneType` | no | all roles | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`. Not valid with lane-less queues. |
| `minGames` | query | integer `>= 1` | no | `5` | Threshold applied only to matchup maps in individual leaves. |

## `200` response

The source of truth is a `champion × CanonicalQueue × position` leaf.
Each leaf keeps its base accumulators and, under `matchups`, only the
opponents encountered in the same position. No aggregates for
champion, queue or position are stored, nor `reference`, `winrate`, `kda` or `avg*` fields.

```json
{
  "filter": {
    "timeStart": 1711929600000,
    "timeEnd": 1714521600000,
    "champion": 0,
    "lane": "BOT",
    "queue": "TEAM_BUILDER_RANKED_SOLO",
    "rank": null,
    "rankBehavior": "GREATER_OR_EQUAL",
    "patch": "14.10",
    "region": null,
    "opponent": 0,
    "duo": 0
  },
  "timeStart": 1711929600000,
  "timeEnd": 1714521600000,
  "lastUpdate": 1714521600000,
  "champions": {
    "157": {
      "RANKED_SOLO": {
        "TOP": {
          "games": 42,
          "wins": 24,
          "kills": 183,
          "deaths": 86,
          "assists": 211,
          "damage": 684321,
          "gold": 441320,
          "championLevelTotal": 756,
          "playtime": 110880000,
          "lastPlayedAt": 1714518000000,
          "matchups": {
            "412": {
              "games": 6,
              "wins": 3,
              "kills": 22,
              "deaths": 18,
              "assists": 31,
              "damage": 91240,
              "gold": 61780,
              "championLevelTotal": 108,
              "playtime": 15840000,
              "lastPlayedAt": 1714518000000
            }
          }
        }
      }
    }
  }
}
```

Champion and opponent are numeric object keys. The consumer resolves
name and image from static data and computes required totals/averages by summing the
leaves. A missing or non-applicable position is `UNKNOWN`; Riot queues are
normalized to `CanonicalQueue` during ingestion.

If `start` is passed without `end`, the period end is the end of the
current day (`23:59:59.999`, server timezone), so the `filterKey`
remains stable throughout the day.
If only `end` is passed, no lower bound is applied. When
at least one of `start` and `end` is present, the period takes precedence and `patch` is not
applied; if both are missing, `patch` filters by patch while keeping the
canonical season period.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Aggregate ready. |
| `202` | `profile_matchups_pending` | Aggregate missing; on-demand refresh has been started in the background. |
| `400` | `invalid_request` | Invalid start/end period, queue, patch, role or `minGames`. |
| `404` | — | Profile not found. |

`metadata` is root in both the `200` and the `202` error: it includes the requested aggregation
filter, `lastUpdate` and `refresh`. A stale entry remains `200` with the
persisted payload and `refresh=true`, then queues only the low-priority matchup job.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Parameters: [`LolApiParameters`](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Model: [`ProfileMatchups`](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileMatchups.java)
- Redis: `SUMMONER_MATCHUPS(puuid, filterKey)`, TTL 6 hours
- Mongo: collection `profile_matchups`, identity `{ puuid, filterKey }`
