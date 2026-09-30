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

The source of truth is a `champion × CanonicalQueue × position` leaf. Each leaf
keeps its base accumulators and the relation maps that apply to that queue and
position. For lane queues, timeline differences in the leaf are aggregated
against the same-lane opponent across games where each timeline value is
available; each `matchups` entry exposes the same metrics scoped to one
opponent. `synergies` contains the complementary BOT/UTILITY ally, or the
same-subteam ally in Arena, with timeline differences scoped to the ally pair.
Summoner-spell cast counts are grouped by Riot spell ID in the leaf's `D` and
`F` maps. There are no additional roll-up documents above these leaves, nor
`reference`, `winrate`, `kda` or `avg*` fields in raw storage.

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
              "lastPlayedAt": 1714518000000,
              "kda": 2.4,
              "goldPerMinute": 441.2,
              "killParticipation": 63.1,
              "deathShare": 17.2,
              "goldDiffAt15": 125,
              "goldDiffAt15Sum": 750,
              "goldDiffAt15Games": 6,
              "csDiffAt15": 4.3,
              "csDiffAt15Sum": 25.8,
              "csDiffAt15Games": 6,
              "xpDiffAt15": 1320,
              "xpDiffAt15Sum": 7920,
              "xpDiffAt15Games": 6,
              "killDiffAt15": 1.0,
              "killDiffAt15Sum": 6,
              "killDiffAt15Games": 6,
              "levelDiffAt15": 0.4,
              "levelDiffAt15Sum": 2.4,
              "levelDiffAt15Games": 6,
              "turretPlateDiffAt15": 0.6,
              "plateDiffAt15Sum": 3.6,
              "plateDiffAt15Games": 6,
              "adjustedWinrate": 0.514,
              "weightedDelta": -0.024,
              "matchupScore": -0.82,
              "tier": "C",
              "reliable": true
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
              "playtime": 26400000,
              "kda": 3.2,
              "goldPerMinute": 455.6,
              "killParticipation": 66.4,
              "deathShare": 14.8,
              "goldDiffAt15": 380,
              "goldDiffAt15Sum": 3800,
              "goldDiffAt15Games": 10,
              "csDiffAt15": 9.2,
              "csDiffAt15Sum": 92,
              "csDiffAt15Games": 10,
              "xpDiffAt15": 2140,
              "xpDiffAt15Sum": 21400,
              "xpDiffAt15Games": 10,
              "killDiffAt15": 2.0,
              "killDiffAt15Sum": 20,
              "killDiffAt15Games": 10,
              "levelDiffAt15": 0.8,
              "levelDiffAt15Sum": 8,
              "levelDiffAt15Games": 10,
              "turretPlateDiffAt15": 1.0,
              "plateDiffAt15Sum": 10,
              "plateDiffAt15Games": 10,
              "adjustedWinrate": 0.561,
              "weightedDelta": 0.023,
              "matchupScore": 0.67,
              "tier": "A",
              "reliable": true
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

The HTTP projection ranks `matchups` and `synergies` separately inside each
champion × canonicalQueue × position leaf, after the existing `minGames`
projection has grouped small buckets into `others`. Relation leaves expose raw
timeline sums/counts in the response as well as in Mongo and Redis. Their
averages, adjusted win rate and tier are response-only. A missing timeline
metric has a zero sum and zero sample count.

For both relation maps, prior strength is the median game count of named
champions. Win rate is shrunk toward the parent leaf win rate. The score gives
50% to overall performance (adjusted WR 55%, KDA 20%, gold per minute 10%, kill
participation 10%, inverse death share 5%) and 50% to lane dominance (gold
difference at 15 25%, CS difference 20%, XP difference 20%, kill difference
25%, turret-plate difference 10%). Synergies compare the focal player and ally
against the opposing lane pair. Snapshot metrics use the available frame nearest
minute 15 without interpolation; kill and plate events are counted through
minute 15. Each metric is standardized inside its relation map and non-win-rate
values are shrunk toward the local relation mean before standardization. Level
difference is exposed for context but is not scored.
Pick rate and ban rate are not inputs.

Ranked named entries expose `adjustedWinrate`, `weightedDelta`,
`matchupScore`, `tier` and `reliable`. Reliability means games are at
least the median sample and does not remove entries. Named entries are ordered
by score descending, then adjusted win rate, games and champion ID. Champion
ID `"0"` and `"others"` are not scored, have no tier and are kept at the
end. Tier thresholds are `S+ >= 2`, `S >= 1`, `A >= 0.25`,
`B >= -0.25`, `C >= -1`, otherwise `D`.

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
