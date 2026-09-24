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
| `minGames` | query | integer `>= 1` | no | `5` | Champion buckets below the threshold are combined into `others` separately for `matchups` and `synergies`; no games are dropped. |

## `200` response

The source of truth is a `champion × CanonicalQueue × position` leaf.
Each leaf keeps its base accumulators and the relation maps that apply to that
queue and position. `matchups` contains opponents encountered in the same
position. `synergies` contains the complementary BOT/UTILITY ally, or the
same-subteam ally in Arena. Summoner-spell cast counts are grouped by Riot spell
ID in the leaf's `D` and `F` maps. No aggregates for champion, queue or
position are stored, nor `reference`, `winrate`, `kda` or `avg*` fields.

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
        "BOT": {
          "games": 13,
          "wins": 7,
          "kills": 43,
          "deaths": 30,
          "assists": 66,
          "damage": 182560,
          "gold": 126000,
          "championLevelTotal": 234,
          "d": 145,
          "f": 96,
          "playtime": 34320000,
          "lastPlayedAt": 1714518000000,
          "D": {
            "4": 125,
            "6": 20
          },
          "F": {
            "4": 14,
            "14": 82
          },
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
              "d": 65,
              "f": 46,
              "D": {
                "4": 55,
                "6": 10
              },
              "F": {
                "4": 6,
                "14": 40
              },
              "playtime": 15840000,
              "lastPlayedAt": 1714518000000
            },
            "others": {
              "games": 7,
              "wins": 4,
              "kills": 21,
              "deaths": 12,
              "assists": 35,
              "damage": 91320,
              "gold": 64220,
              "championLevelTotal": 126,
              "d": 80,
              "f": 50,
              "D": {
                "4": 70,
                "6": 10
              },
              "F": {
                "4": 8,
                "14": 42
              },
              "playtime": 18480000
            }
          },
          "synergies": {
            "222": {
              "games": 10,
              "wins": 5,
              "kills": 32,
              "deaths": 22,
              "assists": 50,
              "damage": 146350,
              "gold": 100260,
              "championLevelTotal": 180,
              "d": 115,
              "f": 80,
              "D": {
                "4": 100,
                "6": 15
              },
              "F": {
                "4": 10,
                "14": 70
              },
              "playtime": 26400000
            },
            "others": {
              "games": 3,
              "wins": 2,
              "kills": 11,
              "deaths": 7,
              "assists": 16,
              "damage": 36210,
              "gold": 25740,
              "championLevelTotal": 54,
              "d": 30,
              "f": 16,
              "D": {
                "4": 25,
                "6": 5
              },
              "F": {
                "4": 4,
                "14": 12
              },
              "playtime": 7920000
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
leaves. Champion-ID keys are decimal strings; `"others"` combines all champions
with fewer than `minGames` games for that leaf and relation. With the default
`minGames=5`, it contains the 1–4 game buckets. Summing named champion buckets
and `others` reproduces the complete relation totals; `D` and `F` spell-cast
counts sum to the leaf's existing `d` and `f` cast counts.

For lane-based queues, every game contributes to the opposing champion in the
same lane. BOT and UTILITY leaves also include the complementary teammate in
`synergies`. If the relation participant is unavailable, champion ID `0`
preserves the game's contribution. Arena has no lane matchup: each Arena game
contributes its teammate from the same `subTeam` to `synergies`. A missing or
non-applicable position is `UNKNOWN`; Riot queues are normalized to
`CanonicalQueue` during ingestion.

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
- Redis: `SUMMONER_MATCHUPS(puuid, filterKey)`, TTL 12 hours
- Mongo: collection `profile_matchups`, identity `{ puuid, filterKey }`
