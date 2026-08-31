# Scope: summoner — Profile by PUUID

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}`

`shard` is a `LeagueShard` and `puuid` is the canonical Riot PUUID of the summoner.

## `200` response

Returns `SummonerView`. `overview.statistics` is an aggregatable leaf dataset, not an already precomputed page: the consumer builds total,
queue, position and averages from the received data. `overview.masteries`,
`overview.champions` and `overview.recentMatches` remain part of the response.

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
