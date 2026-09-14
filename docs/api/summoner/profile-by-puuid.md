# Scope: summoner — Profile by PUUID

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}`

`shard` is a `LeagueShard` and `puuid` is the canonical Riot PUUID of the summoner.

## Parameters

| Name | Position | Type | Required | Description |
|---|---|---|---:|---|
| `shard` | path | `LeagueShard` | yes | Shard of the summoner. |
| `puuid` | path | string | yes | Canonical Riot PUUID. |
| `queue` | query | `GameQueueType` or `all` | no | Limits statistics to one valid Riot queue. Omitted or `all` includes every queue. |
| `patch` | query | `major.minor` | no | Limits statistics to the exact patch. Its major identifies the season (`15.x` is 2025; `16.x` is 2026). |
| `season` | query | configured year or `all` | no | Limits statistics to that whole season. `all` removes the time bound and returns every persisted game matching any selected queue or patch. |

With both `patch` and a numeric `season`, the patch major must equal the
season number: `season=2025&patch=15.1` is valid, while
`season=2026&patch=15.1` returns `400`. With only `patch`, its season is
derived automatically. With no filters, the profile retains the canonical
current-season dataset shared with the leaderboard.

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid' \
  --data-urlencode 'queue=RANKED_SOLO_5X5' \
  --data-urlencode 'season=2026'
```

## `200` response

Returns `SummonerView`. `overview.statistics` is an aggregatable leaf dataset, not an already precomputed page: the consumer builds total,
queue, position and averages from the received data. `overview.masteries`,
and `overview.champions` remain part of the response. Recent matches are served
only by the dedicated profile-matches endpoint.

```json
{
  "overview": {
    "statistics": {
      "timeStart": 1711929600000,
      "timeEnd": 1714521600000,
      "lastUpdate": 1714521600000,
      "champions": {
        "157": {
          "RANKED_SOLO": {
            "TOP": {
              "games": 42,
              "wins": 24,
              "kills": 286,
              "deaths": 198,
              "assists": 512,
              "damage": 684321,
              "damageTaken": 501223,
              "championLevelTotal": 756,
              "lpGain": 286,
              "playtime": 110880000
            }
          },
          "ARENA": {
            "UNKNOWN": {
              "games": 2,
              "arenaPlacementSum": 5
            }
          }
        }
      },
      "pings": {},
      "spellOne": {},
      "spellTwo": {}
    }
  }
}
```

Neither `total`, `queueStats`, `laneStats`,
`championStats`, `reference`, `context`, `winrate`, `kda` nor `avg*` fields are
returned or stored.
A leaf may include `isOtp: true` only for the single OTP champion of the
same queue; the non-OTP case is omitted.

Dataset queues are `CanonicalQueue`, not Riot enums: for example
`RANKED_SOLO`, `RANKED_FLEX`, `NORMAL_DRAFT`, `ARAM`, `ARENA` and `SWIFTPLAY`.
A missing or non-applicable position is always `UNKNOWN`.

An omitted metric field means the data was not available in the historical raw
data; a present `0` is a collected value that is genuinely zero. Level
is exclusively `championLevelTotal`, i.e. the sum of final champion levels.

Averages are derived by the consumer: `avgKills = kills / games`,
`avgChampionLevel = championLevelTotal / games`, and Arena placement uses
`arenaPlacementSum / games` of the `ARENA → UNKNOWN` leaf only. Arena
fields are not present in leaves of other queues.

If rank/mastery or statistics are not ready, the response keeps the `PARTIAL`/`202` states
documented by the `ApiResult` contract; no GET performs
a synchronous Riot call.

Each classified entry in `ranks` may include nullable `globalRanking` and
`regionRanking`. These are one-based contextual positions for the queue across
all players and within the summoner's `region`; unranked or unavailable entries
omit both fields.
