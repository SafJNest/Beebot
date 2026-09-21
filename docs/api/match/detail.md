# Scope: match — Detail

## Endpoint

`GET /api/lol/{shard}/match/{gameId}`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/match/EUW1_6789012345'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Shard used for the lookup. |
| `gameId` | path | string | yes | — | Full Riot ID, for example `EUW1_6789012345`. |

The prefix before `_` is stripped for the SQL lookup; the path shard
remains the reference region. A miss follows `Redis → DB → Tracker → Riot`
and does not perform a synchronous Riot fetch in the HTTP request.

## `200` response

Complete `Match`. The example contains two representative participants; the
actual response contains all available participants for the match.

```json
{
  "id": 6789012345,
  "gameId": "EUW1_6789012345",
  "leagueShard": "EUW1",
  "queue": "TEAM_BUILDER_RANKED_SOLO",
  "rank": "DIAMOND",
  "lastUpdate": 1714521600000,
  "bans": {
    "BLUE": [157, 238, 64, 555, 121],
    "RED": [122, 84, 67, 39, 157]
  },
  "events": {
    "kills": [
      {
        "timestamp": 184000,
        "killerPuuid": "Qx7m2vW8-example-puuid",
        "victimPuuid": "opponent-puuid"
      }
    ],
    "objectives": []
  },
  "timeStart": 1714514400000,
  "timeEnd": 1714521600000,
  "patch": "14.10",
  "participants": [
    {
      "id": 1,
      "summonerId": 12345678,
      "matchId": 6789012345,
      "win": true,
      "kda": "8/3/11",
      "champion": 157,
      "lane": "MID",
      "team": "BLUE",
      "roleQuestId": 0,
      "rankProgress": {
        "rank": "DIAMOND_II",
        "lp": 74,
        "gain": 21,
        "previousRank": "DIAMOND_II",
        "previousLp": 53
      },
      "damage": 18432,
      "damageTaken": 14987,
      "damageBuilding": 4210,
      "healing": 912,
      "cs": 241,
      "goldEarned": 15321,
      "ward": 7,
      "wardKilled": 3,
      "visionScore": 27,
      "pings": {"danger": 2, "onMyWay": 1},
      "subTeam": 0,
      "subTeamPlacement": 0,
      "puuid": "Qx7m2vW8-example-puuid",
      "riotId": "Player",
      "riotTag": "EUW",
      "level": 18,
      "doubles": 1,
      "triples": 0,
      "quadruples": 0,
      "pentas": 0,
      "item0": 3031,
      "item1": 6673,
      "item2": 3006,
      "item3": 3036,
      "item4": 0,
      "item5": 0,
      "item6": 3363,
      "turretKills": 4,
      "q": 5,
      "w": 5,
      "e": 5,
      "r": 3,
      "d": 2,
      "f": 2,
      "summonerSpell1": 4,
      "summonerSpell2": 14,
      "primaryRunes": [8010, 9111, 9104, 8014],
      "secondaryRunes": [8347, 8304],
      "statsRunes": [5005, 5008, 5001],
      "skillOrder": [1, 3, 2, 1, 1, 4],
      "augments": [],
      "starterItems": [1055],
      "buildPath": [3031, 6673],
      "boots": 3006,
      "supportItem": 0
    },
    {
      "id": 2,
      "summonerId": 87654321,
      "matchId": 6789012345,
      "win": false,
      "kda": "3/8/4",
      "champion": 412,
      "lane": "UTILITY",
      "team": "RED",
      "roleQuestId": 2011,
      "rankProgress": {
        "rank": "DIAMOND_III",
        "lp": 52,
        "gain": -18,
        "previousRank": "DIAMOND_III",
        "previousLp": 70
      },
      "damage": 8432,
      "damageTaken": 22001,
      "damageBuilding": 1021,
      "healing": 120,
      "cs": 34,
      "goldEarned": 9234,
      "ward": 31,
      "wardKilled": 8,
      "visionScore": 68,
      "pings": {"danger": 5},
      "subTeam": 0,
      "subTeamPlacement": 0,
      "puuid": "opponent-puuid",
      "riotId": "Opponent",
      "riotTag": "EUW",
      "level": 17,
      "doubles": 0,
      "triples": 0,
      "quadruples": 0,
      "pentas": 0,
      "item0": 3870,
      "item1": 3190,
      "item2": 3107,
      "item3": 3110,
      "item4": 0,
      "item5": 0,
      "item6": 3364,
      "turretKills": 0,
      "q": 5,
      "w": 5,
      "e": 5,
      "r": 3,
      "d": 2,
      "f": 2,
      "summonerSpell1": 4,
      "summonerSpell2": 3,
      "primaryRunes": [8360, 8304, 8347, 8345],
      "secondaryRunes": [8210, 8237],
      "statsRunes": [5008, 5001, 5001],
      "skillOrder": [3, 2, 1, 3, 3, 4],
      "augments": [],
      "starterItems": [3850],
      "buildPath": [3107],
      "boots": 3110,
      "supportItem": 3870
    }
  ]
}
```

`participant.rankProgress` is the only rank contract for the participant. It contains
the current snapshot (`rank`, `lp`), the optional gain and the optional previous snapshot.
The former top-level `rank`, `lp` and `gain` participant fields
have been intentionally removed from the public JSON.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `202` | `match_pending` | Match not yet persisted or analyzed; work has been queued. |
| `400` | `invalid_request` | Missing/invalid `shard` or `gameId`. |
| `404` | `not_found` | Match searched and marked as not found. |

```json
{
  "status": 202,
  "code": "match_pending",
  "message": "Match analysis is pending"
}
```

The `200` response adds root `metadata` with the match `lastUpdate` and
`refresh=false`; pagination and filter are `null`.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`MatchService`](../../../src/main/java/com/safjnest/lol/service/MatchService.java) — `getDetail`
- Success model: [`Match`](../../../src/main/java/com/safjnest/lol/model/match/Match.java)
