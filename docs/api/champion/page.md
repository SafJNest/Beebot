# Scope: champion — Page

## Endpoint

`GET /api/lol/champion/{champion}`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/champion/Thresh' \
  --data-urlencode 'patch=14.10' \
  --data-urlencode 'rank=EMERALD' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'role=UTILITY'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `champion` | path | string | yes | — | Champion name, case-insensitive; search uses static champion normalization. |
| `patch` | query | string `major.minor` | no | current patch | Dataset patch. The value must keep both components, e.g. `14.10`. |
| `rank` | query | enum `TierType` | no | no filter | Minimum tier of the dataset; `EMERALD` includes Emerald and higher tiers according to the filter. |
| `region` | query | enum `LeagueShard` | no | internal `GLOBAL` aggregate | Shard to aggregate. Do not send `GLOBAL` or `UNKNOWN`. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Dataset queue. |
| `role` | query | enum `LaneType` | no | no filter | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`; requires a queue with lanes. |

## `200` response

`ChampionView`. The internal `filter` field is not part of the HTTP JSON.

If the refresh has completed but the filter contains no valid games/builds, the
same `200` response contains a zeroed overview and empty lists. The frontend must
render the no-data state; no indefinite `202` is kept.

```json
{
  "champion": {
    "id": 412,
    "name": "Thresh",
    "image": "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/Thresh.png"
  },
  "stats": {
    "overview": {
      "games": 12540,
      "picks": 12540,
      "bans": 1830,
      "wins": 6646,
      "winrate": 0.53,
      "pickrate": 0.084,
      "banrate": 0.012,
      "kda": 2.91,
      "csPerMinute": 1.21,
      "goldPerMinute": 312.4,
      "damageProfile": {
        "physical": 0.41,
        "magic": 0.52,
        "trueDamage": 0.07
      }
    },
    "laneStats": [
      {
        "lane": "UTILITY",
        "games": 12540,
        "winrate": 0.53
      }
    ],
    "matchups": {
      "MatchupKey[champion=157, lane=UTILITY]": {
        "champion": 157,
        "lane": "UTILITY",
        "matches": 312,
        "wins": 145,
        "winrate": 0.465,
        "deltaWinrate": -0.065,
        "goldDiffAt15": -84,
        "csDiffAt15": -1.4,
        "soloKillRate": 0.031,
        "killParticipation": 0.58,
        "opponentBanRate": 0.021,
        "metricGames": 286
      }
    },
    "laneSynergies": [
      {
        "allyChampion": 157,
        "allyLane": "MID",
        "matches": 488,
        "wins": 278,
        "winrate": 0.57,
        "pickrate": 0.039
      }
    ],
    "powerCurve": [
      {
        "durationBucket": "0-20",
        "games": 2140,
        "wins": 1113,
        "winrate": 0.52
      },
      {
        "durationBucket": "20-30",
        "games": 6940,
        "wins": 3750,
        "winrate": 0.54
      }
    ],
    "trend": {
      "previousPatch": "14.9",
      "games": 11880,
      "winrate": 0.525,
      "deltaWinrate": 0.005
    }
  },
  "build": {
    "games": 12540,
    "wins": 6646,
    "winrate": 0.53,
    "coreBuilds": [
      {
        "id": "3865-3100-3157",
        "items": [3865, 3100, 3157],
        "matches": 1820,
        "wins": 1010,
        "winrate": 0.555,
        "pickrate": 0.145
      }
    ],
    "coreItems": [
      {
        "id": "3100",
        "matches": 7420,
        "wins": 4010,
        "winrate": 0.54,
        "pickrate": 0.592,
        "spell1": 31,
        "spell2": 0
      }
    ],
    "starters": [
      {
        "id": "3865-2",
        "matches": 10400,
        "wins": 5560,
        "winrate": 0.535,
        "pickrate": 0.829,
        "spell1": 38,
        "spell2": 65
      }
    ],
    "boots": [
      {
        "id": "3117",
        "matches": 4320,
        "wins": 2390,
        "winrate": 0.553,
        "pickrate": 0.345,
        "spell1": 31,
        "spell2": 17
      }
    ],
    "supportItems": [
      {
        "id": "3871",
        "matches": 3860,
        "wins": 2150,
        "winrate": 0.557,
        "pickrate": 0.308,
        "spell1": 38,
        "spell2": 71
      }
    ],
    "slots": [
      [
        {
          "id": "3100",
          "matches": 7420,
          "wins": 4010,
          "winrate": 0.54,
          "pickrate": 0.592,
          "spell1": 31,
          "spell2": 0
        }
      ],
      [
        {
          "id": "3157",
          "matches": 5160,
          "wins": 2830,
          "winrate": 0.548,
          "pickrate": 0.411,
          "spell1": 31,
          "spell2": 57
        }
      ]
    ],
    "runes": [
      {
        "id": "rune-config-example",
        "configuration": {
          "primaryTree": 8400,
          "keystone": 8439,
          "primaryRunes": [8437, 8463, 8242],
          "secondaryTree": 8300,
          "secondaryRunes": [8347, 8304],
          "statShards": [5008, 5001, 5011]
        },
        "matches": 3180,
        "wins": 1740,
        "winrate": 0.547,
        "pickrate": 0.254
      }
    ],
    "summonerSpells": [
      {
        "id": "4-14",
        "matches": 8210,
        "wins": 4430,
        "winrate": 0.539,
        "pickrate": 0.655,
        "spell1": 4,
        "spell2": 14
      }
    ],
    "skillOrders": [
      {
        "id": "3-2-1-3-3-4",
        "order": [3, 2, 1, 3, 3, 4],
        "matches": 2210,
        "wins": 1220,
        "winrate": 0.552,
        "pickrate": 0.176
      }
    ],
    "prismatics": [],
    "augments": [
      [],
      [],
      [],
      []
    ]
  }
}
```

Build lists are independent categories and each category contains at most
three options. `coreBuilds`, `coreItems` and `slots` include only valid depth-3 items
present in the final inventory; intermediate pieces are
excluded.

Options in `skillOrders` are sequences at the maximum observed level: 18
when available, otherwise the maximum available length. `matches` and
`wins` include every game whose observed order is a prefix of the
sequence, so games that ended before that level also contribute
to the compatible combination.
starter, boots, support items, consumables, trinket, prismatics and augment
keep existing categories and exclusions. `matchups` is a map with
keys serialized as
`MatchupKey[champion=championId, lane=ROLE]`; unavailable metrics are
`null`. The frontend converts this map into an array for the presentation layer.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `202` | `champion_data_pending` | Statistics or build have not yet been generated; refresh is queued in the background. A completed refresh with no data produces `200` with empty aggregates. |
| `400` | `invalid_request` | Invalid rank, region, queue or role, or role incompatible with queue. |
| `404` | `not_found` | Unknown champion. |

The requested variant is stale when the top-level Mongo timestamp for statistics
or build is missing/more than a week old. In that case it keeps `202
champion_data_pending` and queues only the stale component. `metadata` exposes the
full requested filter, `refresh` and the oldest of the build
and statistics timestamps; all global variants remain available.

```json
{
  "status": 202,
  "code": "champion_data_pending",
  "message": "Champion data is being prepared"
}
```

## Owner

- Controller: [`ChampionController`](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java)
- Service: [`ChampionService`](../../../src/main/java/com/safjnest/lol/service/ChampionService.java)
- Success model: [`ChampionView`](../../../src/main/java/com/safjnest/lol/model/ChampionView.java)
